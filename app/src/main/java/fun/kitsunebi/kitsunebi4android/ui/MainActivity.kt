package `fun`.kitsunebi.kitsunebi4android.ui

import `fun`.kitsunebi.kitsunebi4android.R
import `fun`.kitsunebi.kitsunebi4android.service.SimpleVpnService
import `fun`.kitsunebi.kitsunebi4android.storage.Preferences
import `fun`.kitsunebi.kitsunebi4android.ui.perapp.PerAppActivity
import `fun`.kitsunebi.kitsunebi4android.ui.proxylog.ProxyLogActivity
import `fun`.kitsunebi.kitsunebi4android.ui.settings.SettingsActivity
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.json.JSONException
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    var running = false
    private var starting = false
    private var stopping = false
//    val mNotificationId = 1
    //    var mNotificationManager: NotificationManager? = null

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                "vpn_stopped" -> {
                    running = false
                    stopping = false
                    fab.setImageResource(android.R.drawable.ic_media_play)
//                    mNotificationManager?.cancel(mNotificationId)
                }
                "vpn_started" -> {
                    running = true
                    starting = false
                    fab.setImageResource(android.R.drawable.ic_media_pause)
//                    startNotification()
                }
                "vpn_start_err" -> {
                    running = false
                    starting = false
                    fab.setImageResource(android.R.drawable.ic_media_play)
                    showAlert("Start VPN service failed")
                }
                "vpn_start_err_dns" -> {
                    running = false
                    starting = false
                    fab.setImageResource(android.R.drawable.ic_media_play)
                    showAlert("Start VPN service failed: Not configuring DNS right, must has at least 1 dns server and mustn't include \"localhost\"")
                }
                "pong" -> {
                    fab.setImageResource(android.R.drawable.ic_media_pause)
                    running = true
                    Preferences.putBool(applicationContext, getString(R.string.vpn_is_running), true)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

//        mNotificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        registerReceiver(broadcastReceiver, IntentFilter("vpn_stopped"))
        registerReceiver(broadcastReceiver, IntentFilter("vpn_started"))
        registerReceiver(broadcastReceiver, IntentFilter("vpn_start_err"))
        registerReceiver(broadcastReceiver, IntentFilter("vpn_start_err_dns"))
        registerReceiver(broadcastReceiver, IntentFilter("pong"))

        sendBroadcast(Intent("ping"))

        var configString = Preferences.getString(applicationContext, getString(R.string.preference_config_key), getString(R.string.default_config))
        configString?.let {
            formatJsonString(it).let {
                configView.setText(it, TextView.BufferType.EDITABLE)
            }
        }

        fab.setOnClickListener { view ->
            if (!running && !starting) {
                starting = true
                fab.setImageResource(android.R.drawable.ic_media_ff)
                configString = configView.text.toString()
                Preferences.putString(applicationContext, getString(R.string.preference_config_key), configString)
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else {
                    onActivityResult(1, Activity.RESULT_OK, null);
                }
            } else if (running && !stopping) {
                stopping = true
                sendBroadcast(Intent("stop_vpn"))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, SimpleVpnService::class.java)
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        sendBroadcast(Intent("ping"))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.format_btn -> {
                val prettyText = formatJsonString(configView.text.toString())
                prettyText?.let {
                    configView.setText(it, TextView.BufferType.EDITABLE)
                }
                return true
            }
            R.id.save_btn -> {
                val config = configView.text.toString()
                val prettyText = formatJsonString(config)
                if (prettyText == null) {
                    showAlert("Invalid JSON")
                    return true
                }
                Preferences.putString(applicationContext, getString(R.string.preference_config_key), prettyText)
                return true
            }
            R.id.log_btn -> {
                val intent = Intent(this, ProxyLogActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.logcat_btn -> {
                val intent = Intent(this, LogcatActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.settings_btn -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.help_btn -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/eycorsican/kitsunebi-android"))
                startActivity(intent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    fun formatJsonString(json: String): String? {
        return try {
            JSONObject(json).toString(2)
        } catch (e: JSONException) {
            showAlert("Invalid JSON")
            return null
        }
    }

    fun showAlert(msg: String) {
        val dialog = AlertDialog.Builder(this).setTitle("Message").setMessage(msg)
                .setPositiveButton("Ok", { dialog, i ->
                })
        dialog.show()
    }

//    private fun startNotification() {
//        // Build Notification , setOngoing keeps the notification always in status bar
//        val mBuilder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
//                .setContentTitle("Kitsunebi")
//                .setContentText("Touch to open the app")
//                .setSmallIcon(R.drawable.notification_icon_background)
//                .setWhen(System.currentTimeMillis())
//                .setOngoing(true)
//
//        // Create pending intent, mention the Activity which needs to be
//        //triggered when user clicks on notification(StopScript.class in this case)
//        val contentIntent = PendingIntent.getActivity(this, 0,
//                Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
//
//        mBuilder.setContentIntent(contentIntent)
//
//        // Builds the notification and issues it.
//        mNotificationManager?.notify(mNotificationId, mBuilder.build())
//    }
}