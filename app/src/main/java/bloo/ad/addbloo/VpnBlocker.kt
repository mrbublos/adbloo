package bloo.ad.addbloo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import bloo.ad.addbloo.db.Blocked
import bloo.ad.addbloo.db.Db
import bloo.ad.addbloo.db.UrlDao
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference


class VpnBlocker : VpnService(), Handler.Callback, LifecycleOwner {

    companion object {
        const val KEEP_ALIVE_INT = 15
        const val SLEEP_INT = 100L
        const val RECEIVE_TIMEOUT = 120_000

        const val ACTION_DISCONNECT = "adBloo.disconnect"
        const val ACTION_CONNECT = "adBloo.connect"
    }

    private val mDispatcher = ServiceLifecycleDispatcher(this)

    var job: Job? = null
    var builder: Builder = Builder()
    var mInterface: ParcelFileDescriptor? = null


    private val blockedNames = AtomicReference<List<String>>(mutableListOf())
    private var connectivityManager: ConnectivityManager? = null

    private var mHandler: Handler? = null
    private var mConfigureIntent: PendingIntent? = null

    // TODO write cleaner for requests without response
    private val pendingRequests = ConcurrentHashMap<Int, DnsPacket>()

    private lateinit var dao: UrlDao

    override fun onCreate() {
        if (mHandler == null) {
            mHandler = Handler(this)
        }

        mConfigureIntent = PendingIntent.getActivity(this, 0, Intent(this, VpnBlocker::class.java),
                                                     PendingIntent.FLAG_UPDATE_CURRENT)

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        dao = Db.instance(applicationContext).blockedUrlsDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent != null && ACTION_DISCONNECT == intent.action) {
            stop()
            START_NOT_STICKY
        } else {
            start()
            START_STICKY
        }
    }

    private fun start() {
        updateForegroundNotification(R.string.connecting)
        mHandler?.sendEmptyMessage(R.string.connecting)

        job = GlobalScope.launch(Dispatchers.IO) {
            val address = getLocalIpAddress() ?: throw IllegalStateException("Unable to determine local address")
            startBlackListSynchronize()

            // TODO make UI selector for a DNS server
            val dnsAddresses = getDnsServers()
            val dnsAddress = dnsAddresses.find { it is Inet4Address } ?: throw IllegalStateException("Unable to determine dns address")
            //            dnsAddress = "8.8.8.8" // for emulator

            "Using address $address".log()
            "Using DNS server $dnsAddress".log()
            try {
                val builder = builder.setSession("AddBloo")
                        .addDnsServer(dnsAddress)
                        .addRoute(dnsAddress.hostAddress, 32)
                        .addAddress(address, if (address.length > 15) 128 else 32)

                mInterface = builder.establish()

                DatagramChannel.open().use { tunnel ->
                    tunnel.connect(InetSocketAddress(dnsAddress, 53))
                    tunnel.configureBlocking(false)
                    protect(tunnel.socket())

                    try {
                        filterPackets(tunnel)
                    } catch (e: CancellationException) {}
                }
            } catch (e: Exception) {
                "Some Error ".logE(e)
            } finally {
                stop()
            }
        }
    }

    private suspend fun startBlackListSynchronize() = withContext(Dispatchers.Main) {
        dao.getAll().observe(this@VpnBlocker,  Observer { data ->
            val newBlockedUrls = data.filter { it.blocked }.map(Blocked::host)
            blockedNames.set(newBlockedUrls)
        })
    }

    private suspend fun filterPackets(tunnel: DatagramChannel) {
        val input = FileInputStream(mInterface!!.fileDescriptor)
        val out = FileOutputStream(mInterface!!.fileDescriptor)
        var lastSendTime = System.currentTimeMillis()
        var lastReceiveTime = System.currentTimeMillis()

        val packet = ByteBuffer.allocate(100_000)

        // TODO if it will be slow consider splitting into several threads
        while (job?.isCancelled == false) {
            var idle = true

            var length = input.read(packet.array())
            if (length > 0) {
                DnsPacket.fromArray(packet.array().sliceArray(0 until length))?.let { dns ->
                    // TODO should we pass through non TCP/UDP packets?
                    if (allowedPacket(dns.queries)) {
                        pendingRequests[dns.id] = dns
                        tunnel.write(ByteBuffer.wrap(dns.datagram))
                    } else {
                        // forging DNS response
                        dns.makeLoopbackResponse()
                        dns.setHeaders(dns)
//                        "Sending forged response ${dns.raw.toWireShark()}".log()
                        out.write(dns.raw, 0, dns.getLength())
                    }
                }

                packet.clear()
                idle = false
                lastReceiveTime = System.currentTimeMillis()
            }

            length = tunnel.read(packet)
            if (length > 0) {
                val data = packet.array().sliceArray(0 until length)
                if (packet.get(0) != 0.toByte()) {
                    DnsPacket.fromDatagram(data).let { dnsResponse ->
                        if (pendingRequests.containsKey(dnsResponse.id)) {
                            val request = pendingRequests[dnsResponse.id]!!
                            "Received response for ${request.queries}".log()
                            dnsResponse.setHeaders(request)
//                            "Response ${dnsResponse.raw.toWireShark()}".log()
                            out.write(dnsResponse.raw, 0, length)
                        }
                    }
                }
                packet.clear()

                idle = false
                lastReceiveTime = System.currentTimeMillis()
            }

            if (idle) {
                try {
                    delay(SLEEP_INT)
                } catch (e: Exception) {
                    break
                }

                val now = System.currentTimeMillis()
                if (lastSendTime + KEEP_ALIVE_INT < now) {
                    packet.put(0.toByte()).limit(1)
                    for (i in 0 until 3) {
                        packet.position(0)
                        tunnel.write(packet)
                    }
                    packet.clear()
                    lastSendTime = now
                } else if (lastReceiveTime + RECEIVE_TIMEOUT < now) {
                    "Timed out".log()
                    break
                }
            }
        }
    }

    private fun stop() {
        try {
            job?.cancel()
            mInterface?.close()
            mInterface = null
            stopSelf()
        } catch (e: Exception) {
            "Stopping error".logE(e)
        }
    }

    private fun allowedPacket(domains: List<String>): Boolean {
        var allowed = true
        GlobalScope.launch(Dispatchers.IO) {
            domains.filter {
                !blockedNames.get().contains(it)
            }.forEach {
                dao.insert(Blocked(it, false))
            }
        }

        domains.forEach dn@{ domain ->
            blockedNames.get().forEach { blocked ->
                if (domain.contains(blocked)) {
                    allowed = false
                    return@dn
                }
            }
        }
        "DNS request to $domains, allowed: $allowed".log()
        return allowed
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
        "AddBloo vpn service destroyed".log()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val networkInterface = en.nextElement()
                val enumIpAddr = networkInterface.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    "\n****** INET ADDRESS ******\naddress: ${inetAddress.hostAddress}\nhostname: ${inetAddress.hostName}\n".log()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        "IS NOT LOOPBACK ADDRESS: ${inetAddress.hostAddress}".log()
                        return inetAddress.hostAddress.toString()
                    }
                }
            }
        } catch (ex: SocketException) {
            "Error getting IP address ".logE(ex)
        }

        return null
    }

    private fun updateForegroundNotification(message: Int) {
        startForeground(1, NotificationCompat.Builder(this, createNotificationChannel("adBloo", "adBloo"))
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .setOngoing(true)
                .build())
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun handleMessage(message: Message?): Boolean {
        message?.let {
            Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show()
            if (message.what != R.string.disconnected) {
                updateForegroundNotification(message.what)
            }
        }
        return true
    }

    private fun getDnsServers(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        val cm = connectivityManager
        cm?.allNetworks?.forEach { network ->
            val networkInfo = cm.getNetworkInfo(network)
            if (networkInfo.isConnected) {
                val linkProperties = cm.getLinkProperties(network)
                result.addAll(linkProperties.dnsServers)
            }
        }
        "Dns servers: $result".log()
        return result
    }

    override fun getLifecycle(): Lifecycle {
        return mDispatcher.lifecycle
    }
}