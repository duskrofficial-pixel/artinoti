package com.notialarm.app

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.WindowManager
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import java.util.*

class AlarmActivity : Activity() {

    private var flashHandler: Handler? = null
    private var isFlashColor = false
    private var rootLayout: RelativeLayout? = null

    private val flashingRunnable = object : Runnable {
        override fun run() {
            rootLayout?.setBackgroundColor(
                if (isFlashColor) Color.parseColor("#D32F2F") else Color.parseColor("#B71C1C")
            )
            isFlashColor = !isFlashColor
            flashHandler?.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake window configurations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)

        rootLayout = findViewById(R.id.root_alarm_layout)
        val tvApp: TextView = findViewById(R.id.tv_trigger_app)
        val tvTitle: TextView = findViewById(R.id.tv_trigger_title)
        val tvText: TextView = findViewById(R.id.tv_trigger_text)
        val tvRule: TextView = findViewById(R.id.tv_trigger_rule)

        val appPackage = intent.getStringExtra("app") ?: "Test App"
        val title = intent.getStringExtra("title") ?: "Test Title"
        val text = intent.getStringExtra("text") ?: "Test Text"
        val keyword = intent.getStringExtra("keyword") ?: "Test Keyword"

        tvApp.text = "App: $appPackage"
        tvTitle.text = "Title: $title"
        tvText.text = "Text: $text"
        tvRule.text = "Matched Rule: $keyword"

        // Stop Button
        findViewById<Button>(R.id.btn_stop_alarm).setOnClickListener {
            stopForegroundService()
            finish()
        }

        // Snooze Button
        findViewById<Button>(R.id.btn_snooze_alarm).setOnClickListener {
            val settings = SettingsManager(this)
            val durationMin = settings.snoozeDurationMin
            scheduleSnooze(durationMin, appPackage, title, text, keyword)
            stopForegroundService()
            finish()
        }

        // Open App Button
        findViewById<Button>(R.id.btn_open_app).setOnClickListener {
            stopForegroundService()
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(appPackage)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            } catch (e: Exception) {
                // Ignore if package cannot be opened
            }
            finish()
        }

        flashHandler = Handler(Looper.getMainLooper())
        flashHandler?.post(flashingRunnable)
    }

    private fun stopForegroundService() {
        val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = "STOP_ALARM"
        }
        startService(stopIntent)
    }

    private fun scheduleSnooze(minutes: Int, app: String, title: String, text: String, keyword: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmForegroundService::class.java).apply {
            putExtra("app", app)
            putExtra("title", title)
            putExtra("text", text)
            putExtra("keyword", keyword)
        }
        val pendingIntent = PendingIntent.getService(
            this,
            1234,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        flashHandler?.removeCallbacks(flashingRunnable)
    }
}
