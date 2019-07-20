package bloo.ad.addbloo

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private var switchedOn = false

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
        
        configButton.setOnClickListener {
            startActivity(Intent(this, FilterActivity::class.java))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("switched", switchedOn)
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
        super.onActivityResult(requestCode, resultCode, data)
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
