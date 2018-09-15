package bloo.ad.addbloo

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.widget.Toast
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel


class VpnBlocker : VpnService(), Handler.Callback {


    companion object {
        const val KEEP_ALIVE_INT = 15
        const val SLEEP_INT = 100L
        const val RECEIVE_TIMEOUT = 120_000

        const val ACTION_DISCONNECT = "adBloo.disconnect"
        const val ACTION_CONNECT = "adBloo.connect"
    }

    var job: Job? = null
    var builder: Builder = Builder()
    var mInterface: ParcelFileDescriptor? = null
    private val blockedNames = mutableListOf<String>()
    private var connectivityManager: ConnectivityManager? = null

    private var mHandler: Handler? = null
    private var mConfigureIntent: PendingIntent? = null

    override fun onCreate() {
        if (mHandler == null) {
            mHandler = Handler(this)
        }
        // Create the intent to "configure" the connection (just start ToyVpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, Intent(this, VpnBlocker::class.java),
                                                     PendingIntent.FLAG_UPDATE_CURRENT)

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
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

    fun start() {
        updateForegroundNotification(R.string.connecting)
        mHandler?.sendEmptyMessage(R.string.connecting)

        job = launch(CommonPool) {
            val address = getLocalIpAddress() ?: throw IllegalStateException("Unable to determine local address")

            blockedNames.addAll(resources.openRawResource(R.raw.block_list).reader().readLines())

            try {
                val builder = builder.setSession("AddBloo")
                        .addDnsServer("8.8.8.8")
                        .addRoute("8.8.8.8", 32)
                        .addAddress(address, if (address.length > 15) 128 else 32)

                mInterface = builder.establish()

                DatagramChannel.open().use { tunnel ->
                    tunnel.connect(InetSocketAddress("8.8.8.8", 53))
                    tunnel.configureBlocking(false)
                    protect(tunnel.socket())

                    handlePackets(tunnel)
                }
            } catch (e: Exception) {
                "Some Error ".logE(e)
            } finally {
                stop()
            }
        }
    }

    private suspend fun handlePackets(tunnel: DatagramChannel) {
        val input = FileInputStream(mInterface!!.fileDescriptor)
        val out = FileOutputStream(mInterface!!.fileDescriptor)
        var lastSendTime = System.currentTimeMillis()
        var lastReceiveTime = System.currentTimeMillis()

        val packet = ByteBuffer.allocate(Short.MAX_VALUE + 0)

        // TODO if it will be slow consider spliiting into several threads
        while (true) {
            var idle = true

            var length = input.read(packet.array())
            if (length > 0) {
                if (allowedPacket(packet.array().copyOfRange(0, length))) {
                    packet.limit(length)
                    tunnel.write(packet)
                }
                packet.clear()

                idle = false
                lastReceiveTime = System.currentTimeMillis()
            }

            length = tunnel.read(packet)
            if (length > 0) {
                if (packet.get(0) != 0.toByte()) {
                    out.write(packet.array(), 0, length)
                }
                packet.clear()

                idle = false
                lastReceiveTime = System.currentTimeMillis()
            }

            if (idle) {
                delay(SLEEP_INT)

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
                    throw IllegalStateException("Timed out")
                }
            }
        }
    }

    private fun stop() {
        try {
            job?.cancel()
            mInterface?.close()
            mInterface = null
        } catch (e: Exception) {}
    }

    private fun allowedPacket(packet: ByteArray): Boolean {
        val dns = DnsPacket.fromArray(packet) ?: return true // allowing non UDP/TCP packets
        return blockedNames.intersect(dns.queries).isEmpty()
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
                    if (!inetAddress.isLoopbackAddress) {
                        "IS NOT LOOPBACK ADDRESS: ${inetAddress.hostAddress}".log()
                        return inetAddress.hostAddress.toString()
                    }
                }
            }
        } catch (ex: SocketException) {
            "Error".logE(ex)
        }

        return null
    }

    private fun updateForegroundNotification(message: Int) {
        startForeground(1, Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build())
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
}