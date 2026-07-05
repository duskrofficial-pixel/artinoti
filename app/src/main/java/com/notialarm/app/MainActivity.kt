package com.notialarm.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var rulesAdapter: RulesAdapter
    private lateinit var historyAdapter: HistoryAdapter

    private lateinit var sectionRules: View
    private lateinit var sectionHistory: View
    private lateinit var sectionSettings: View

    private val REQUEST_SOUND_PICKER = 1001
    private val REQUEST_EXPORT_PICKER = 1002
    private val REQUEST_IMPORT_PICKER = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SettingsManager(this)

        // Sections
        sectionRules = findViewById(R.id.section_rules)
        sectionHistory = findViewById(R.id.section_history)
        sectionSettings = findViewById(R.id.section_settings)

        // Navigation
        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_rules -> showSection(sectionRules)
                R.id.menu_history -> {
                    showSection(sectionHistory)
                    historyAdapter.updateItems(settingsManager.getHistory())
                }
                R.id.menu_settings -> showSection(sectionSettings)
            }
            true
        }

        // Rules Setup
        val rvRules: RecyclerView = findViewById(R.id.rv_rules)
        rvRules.layoutManager = LinearLayoutManager(this)
        rulesAdapter = RulesAdapter(settingsManager.getRules().toMutableList()) { rule ->
            settingsManager.removeRule(rule.id)
            rulesAdapter.updateItems(settingsManager.getRules())
        }
        rvRules.adapter = rulesAdapter

        findViewById<MaterialButton>(R.id.btn_add_rule).setOnClickListener {
            showAddRuleDialog()
        }

        // History Setup
        val rvHistory: RecyclerView = findViewById(R.id.rv_history)
        rvHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(settingsManager.getHistory())
        rvHistory.adapter = historyAdapter

        findViewById<MaterialButton>(R.id.btn_clear_history).setOnClickListener {
            settingsManager.clearHistory()
            historyAdapter.updateItems(emptyList())
        }

        // Settings Setup
        setupSettingsTab()
    }

    private fun showSection(section: View) {
        sectionRules.visibility = View.GONE
        sectionHistory.visibility = View.GONE
        sectionSettings.visibility = View.GONE
        section.visibility = View.VISIBLE
    }

    private fun setupSettingsTab() {
        // Permission Buttons
        findViewById<MaterialButton>(R.id.btn_perm_listener).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        findViewById<MaterialButton>(R.id.btn_perm_overlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Overlay permission not required on this version.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btn_perm_battery).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // Sound Picker
        val tvSound: TextView = findViewById(R.id.tv_selected_sound)
        tvSound.text = "Sound: " + (settingsManager.soundUri ?: "Default Alarm Ringtone")

        findViewById<MaterialButton>(R.id.btn_select_sound).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_SOUND_PICKER)
        }

        // Snooze config
        val etSnooze: EditText = findViewById(R.id.et_snooze_duration)
        etSnooze.setText(settingsManager.snoozeDurationMin.toString())
        etSnooze.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val mins = s.toString().toIntOrNull()
                if (mins != null && mins > 0) {
                    settingsManager.snoozeDurationMin = mins
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Debug Switch
        val switchDebug: SwitchMaterial = findViewById(R.id.switch_debug_log)
        switchDebug.isChecked = settingsManager.diagnosticLogging
        switchDebug.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.diagnosticLogging = isChecked
        }

        // Test Alarm
        findViewById<MaterialButton>(R.id.btn_test_alarm).setOnClickListener {
            val testIntent = Intent(this, AlarmForegroundService::class.java).apply {
                putExtra("app", packageName)
                putExtra("title", "Test Alarm Title")
                putExtra("text", "This is a test notification body containing keywords.")
                putExtra("keyword", "TEST_ALARM")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(testIntent)
            } else {
                startService(testIntent)
            }
        }

        // Test Match
        findViewById<MaterialButton>(R.id.btn_test_match).setOnClickListener {
            showTestMatchDialog()
        }

        // Export / Import Settings
        findViewById<MaterialButton>(R.id.btn_export_settings).setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "notialarm_config.json")
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_EXPORT_PICKER)
        }

        findViewById<MaterialButton>(R.id.btn_import_settings).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_IMPORT_PICKER)
        }
    }

    private fun showAddRuleDialog() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_rule, null)
        val etApp: EditText = view.findViewById(R.id.et_rule_app_pkg)
        val etKeyword: EditText = view.findViewById(R.id.et_rule_keyword)
        val spinnerMatch: Spinner = view.findViewById(R.id.spinner_match_type)
        val switchCase: SwitchMaterial = view.findViewById(R.id.switch_case_sensitive)

        // Setup match types
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Contains", "Exact", "Regex"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMatch.adapter = adapter

        builder.setView(view)
            .setTitle("Add Alarm Rule")
            .setPositiveButton("Save") { _, _ ->
                val app = etApp.text.toString().trim()
                val keyword = etKeyword.text.toString().trim()
                val matchType = spinnerMatch.selectedItem.toString()
                val caseSensitive = switchCase.isChecked

                if (keyword.isNotEmpty()) {
                    val newRule = AlarmRule(
                        id = UUID.randomUUID().toString(),
                        appPackage = if (app.isEmpty()) "*" else app,
                        keyword = keyword,
                        matchType = matchType,
                        caseSensitive = caseSensitive
                    )
                    settingsManager.addRule(newRule)
                    rulesAdapter.updateItems(settingsManager.getRules())
                    Toast.makeText(this, "Rule added successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Keyword cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTestMatchDialog() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_test_match, null)
        val etApp: EditText = view.findViewById(R.id.et_test_app)
        val etTitle: EditText = view.findViewById(R.id.et_test_title)
        val etBody: EditText = view.findViewById(R.id.et_test_body)

        builder.setView(view)
            .setTitle("Test Matcher Rules")
            .setPositiveButton("Evaluate") { _, _ ->
                val app = etApp.text.toString().trim()
                val title = etTitle.text.toString().trim()
                val body = etBody.text.toString().trim()

                var matchedRule: AlarmRule? = null
                val rules = settingsManager.getRules()
                for (rule in rules) {
                    val appMatches = rule.appPackage.equals(app, ignoreCase = true) || rule.appPackage == "*" || rule.appPackage.isEmpty()
                    if (!appMatches) continue

                    val target = "$title $body"
                    val caseSensitive = rule.caseSensitive
                    val keyword = rule.keyword

                    val isMatch = when (rule.matchType) {
                        "Exact" -> {
                            if (caseSensitive) {
                                title.equals(keyword, ignoreCase = false) || body.equals(keyword, ignoreCase = false)
                            } else {
                                title.equals(keyword, ignoreCase = true) || body.equals(keyword, ignoreCase = true)
                            }
                        }
                        "Regex" -> {
                            try {
                                val flags = if (caseSensitive) 0 else java.util.regex.Pattern.CASE_INSENSITIVE
                                val pattern = java.util.regex.Pattern.compile(keyword, flags)
                                pattern.matcher(title).find() || pattern.matcher(body).find()
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

                    if (isMatch) {
                        matchedRule = rule
                        break
                    }
                }

                if (matchedRule != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Success")
                        .setMessage("Matched keyword: \"${matchedRule.keyword}\" using type: ${matchedRule.matchType} (App: ${matchedRule.appPackage})")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("No Match")
                        .setMessage("The test notification parameters did not match any of your active rules.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_SOUND_PICKER -> {
                val uri = data.data
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    settingsManager.soundUri = uri.toString()
                    findViewById<TextView>(R.id.tv_selected_sound).text = "Sound: $uri"
                }
            }
            REQUEST_EXPORT_PICKER -> {
                val uri = data.data ?: return
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(settingsManager.exportConfigJson().toByteArray())
                    }
                    Toast.makeText(this, "Configuration exported!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_IMPORT_PICKER -> {
                val uri = data.data ?: return
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val sb = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            sb.append(line)
                        }
                        if (settingsManager.importConfigJson(sb.toString())) {
                            rulesAdapter.updateItems(settingsManager.getRules())
                            findViewById<TextView>(R.id.tv_selected_sound).text = "Sound: " + (settingsManager.soundUri ?: "Default Alarm Ringtone")
                            findViewById<SwitchMaterial>(R.id.switch_debug_log).isChecked = settingsManager.diagnosticLogging
                            findViewById<EditText>(R.id.et_snooze_duration).setText(settingsManager.snoozeDurationMin.toString())
                            Toast.makeText(this, "Configuration imported successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Import failed: Invalid configuration format.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Rules Adapter Inner Class
    private inner class RulesAdapter(
        private var items: MutableList<AlarmRule>,
        private val onDelete: (AlarmRule) -> Unit
    ) : RecyclerView.Adapter<RulesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvKeyword: TextView = view.findViewById(R.id.tv_rule_keyword)
            val tvApp: TextView = view.findViewById(R.id.tv_rule_app)
            val tvFlags: TextView = view.findViewById(R.id.tv_rule_flags)
            val btnDelete: View = view.findViewById(R.id.btn_delete_rule)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvKeyword.text = "Keyword: ${item.keyword}"
            holder.tvApp.text = "App package: ${if (item.appPackage == "*") "All Applications (*)" else item.appPackage}"
            holder.tvFlags.text = "Match: ${item.matchType} • Case Sensitive: ${item.caseSensitive}"
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<AlarmRule>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }

    // History Adapter Inner Class
    private inner class HistoryAdapter(
        private var items: List<HistoryItem>
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvApp: TextView = view.findViewById(R.id.tv_history_app)
            val tvTime: TextView = view.findViewById(R.id.tv_history_time)
            val tvTitle: TextView = view.findViewById(R.id.tv_history_title)
            val tvText: TextView = view.findViewById(R.id.tv_history_text)
            val tvKeyword: TextView = view.findViewById(R.id.tv_history_keyword)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvApp.text = item.appName
            holder.tvTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
            holder.tvTitle.text = "Title: ${item.title}"
            holder.tvText.text = "Body: ${item.text}"
            holder.tvKeyword.text = "Matched: ${item.matchedKeyword}"
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<HistoryItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
