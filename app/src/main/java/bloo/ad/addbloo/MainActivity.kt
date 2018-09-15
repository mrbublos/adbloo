package bloo.ad.addbloo

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.content.Intent



class MainActivity : AppCompatActivity() {

    var switchedOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchedOn = savedInstanceState?.getBoolean("switched") ?: false

        startButton.text = if (switchedOn) "Stop blocking" else "Start blocking"

        startButton.setOnClickListener {
            if (switchedOn) stopVpn() else startVpn()
            switchedOn = !switchedOn
            startButton.text = if (switchedOn) "Stop blocking" else "Start blocking"
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putBoolean("switched", switchedOn)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    private fun startVpn() {
        val intent = VpnService.prepare(applicationContext)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            onActivityResult(0, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, VpnBlocker::class.java)
            startService(intent.setAction(VpnBlocker.ACTION_CONNECT))
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, VpnBlocker::class.java)
        startService(intent.setAction(VpnBlocker.ACTION_DISCONNECT))
    }
}
