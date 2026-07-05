package com.notialarm.app

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.regex.Pattern

class MyNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val settings = SettingsManager(this)

        if (settings.diagnosticLogging) {
            Log.d("NotiAlarm", "Received notification: App=$packageName, Title=$title, Text=$text")
        }

        val rules = settings.getRules()
        for (rule in rules) {
            // Check if this rule is for this app (support package match or wildcard)
            val appMatches = rule.appPackage.equals(packageName, ignoreCase = true) || rule.appPackage == "*" || rule.appPackage.isEmpty()
            if (!appMatches) continue

            // Evaluate keyword match
            val keyword = rule.keyword
            val matched = evaluateMatch(title, text, keyword, rule.matchType, rule.caseSensitive)

            if (matched) {
                Log.d("NotiAlarm", "Match found! Triggering alarm for $packageName. Keyword: $keyword")
                
                // Add to history
                val pm = packageManager
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                } catch (e: Exception) {
                    packageName
                }

                val historyItem = HistoryItem(
                    timestamp = System.currentTimeMillis(),
                    appPackage = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    matchedKeyword = keyword
                )
                settings.addHistoryItem(historyItem)

                // Trigger Foreground Service
                val serviceIntent = Intent(this, AlarmForegroundService::class.java).apply {
                    putExtra("app", packageName)
                    putExtra("title", title)
                    putExtra("text", text)
                    putExtra("keyword", keyword)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                break // Stop evaluating other rules once alarm is triggered
            }
        }
    }

    private fun evaluateMatch(title: String, text: String, keyword: String, matchType: String, caseSensitive: Boolean): Boolean {
        val target = "$title $text"
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE

        return when (matchType) {
            "Exact" -> {
                if (caseSensitive) {
                    title.equals(keyword, ignoreCase = false) || text.equals(keyword, ignoreCase = false)
                } else {
                    title.equals(keyword, ignoreCase = true) || text.equals(keyword, ignoreCase = true)
                }
            }
            "Regex" -> {
                try {
                    val pattern = Pattern.compile(keyword, flags)
                    pattern.matcher(title).find() || pattern.matcher(text).find()
                } catch (e: Exception) {
                    false
                }
            }
            else -> { // Contains
                if (caseSensitive) {
                    target.contains(keyword)
                } else {
                    target.lowercase().contains(keyword.lowercase())
                }
            }
        }
    }
}
