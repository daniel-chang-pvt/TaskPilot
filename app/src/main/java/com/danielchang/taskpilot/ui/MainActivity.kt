package com.danielchang.taskpilot.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.danielchang.taskpilot.R
import com.danielchang.taskpilot.data.RuleRepository
import com.danielchang.taskpilot.engine.RuleEngine
import com.danielchang.taskpilot.model.ActionConfig
import com.danielchang.taskpilot.model.ActionType
import com.danielchang.taskpilot.model.AutomationRule
import com.danielchang.taskpilot.model.ConditionConfig
import com.danielchang.taskpilot.model.ConditionType
import com.danielchang.taskpilot.model.TriggerConfig
import com.danielchang.taskpilot.model.TriggerType
import com.danielchang.taskpilot.model.isEnglish
import com.danielchang.taskpilot.model.label
import com.danielchang.taskpilot.scheduler.AutomationScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面层。
 *
 * 这个 Activity 只负责使用原生 View 组装页面和收集用户输入；它不直接判断条件、不直接执行系统动作、
 * 不直接管理 AlarmManager。业务职责分别交给：
 *
 * - data.RuleRepository：规则和日志持久化
 * - scheduler.AutomationScheduler：时间/间隔触发调度
 * - engine.RuleEngine：规则执行编排
 *
 * 这样界面以后即使改成 XML、Compose 或多 Activity，也不会影响核心自动化逻辑。
 */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var tab: Tab = Tab.RULES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (RuleRepository.isGlobalEnabled(this)) AutomationScheduler.scheduleAll(this)
        if (::root.isInitialized) render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addSpace(12)
        addTabs()
        root.addSpace(12)

        when (tab) {
            Tab.RULES -> renderRules()
            Tab.LOGS -> renderLogs()
            Tab.PERMISSIONS -> renderPermissions()
            Tab.SETTINGS -> renderSettings()
        }
    }

    private fun addTabs() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Tab.values().forEach { item ->
            row.addView(Button(this).apply {
                text = item.label(this@MainActivity)
                isEnabled = item != tab
                setOnClickListener {
                    tab = item
                    render()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(row)
    }

    private fun renderRules() {
        addGlobalSwitch()
        root.addSpace(12)
        root.addView(Button(this).apply {
            text = getString(R.string.add_rule)
            setOnClickListener { showRuleDialog(AutomationRule(id = System.currentTimeMillis())) }
        })
        root.addSpace(12)

        val rules = RuleRepository.getRules(this).sortedWith(compareBy<AutomationRule> { it.category }.thenBy { it.name })
        if (rules.isEmpty()) {
            root.addView(TextView(this).apply {
                text = getString(R.string.no_rules)
                textSize = 16f
            })
        } else {
            rules.forEach { addRuleCard(it) }
        }
    }

    private fun addGlobalSwitch() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFE8EEFF.toInt())
        }
        row.addView(TextView(this).apply {
            text = getString(R.string.global_enabled) + "\n" + if (RuleRepository.isGlobalEnabled(this@MainActivity)) getString(R.string.global_on) else getString(R.string.global_off)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = RuleRepository.isGlobalEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                RuleRepository.setGlobalEnabled(this@MainActivity, checked)
                render()
            }
        })
        root.addView(row)
    }

    private fun addRuleCard(rule: AutomationRule) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFF4F4F4.toInt())
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = rule.displayName(this@MainActivity)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Switch(this).apply {
            isChecked = rule.enabled
            setOnCheckedChangeListener { _, checked ->
                RuleRepository.upsertRule(this@MainActivity, rule.copy(enabled = checked))
                render()
            }
        })
        card.addView(top)
        card.addView(TextView(this).apply {
            text = "${rule.category} · ${rule.trigger.type.label(this@MainActivity)} · ${rule.action.type.label(this@MainActivity)}"
            textSize = 14f
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = getString(R.string.edit_rule)
            setOnClickListener { showRuleDialog(rule) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = getString(R.string.run_now)
            setOnClickListener { RuleEngine.executeRule(this@MainActivity, rule, "manual") }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = getString(R.string.copy)
            setOnClickListener {
                RuleRepository.copyRule(this@MainActivity, rule)
                render()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(buttons)
        root.addView(card)
        root.addSpace(10)
    }

    private fun showRuleDialog(initial: AutomationRule) {
        var enabled = initial.enabled
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val enabledSwitch = Switch(this).apply {
            text = getString(R.string.rule_enabled)
            isChecked = enabled
            setOnCheckedChangeListener { _, checked -> enabled = checked }
        }
        container.addView(enabledSwitch)

        val nameInput = edit(initial.name, getString(R.string.rule_name), false)
        val categoryInput = edit(initial.category, getString(R.string.category), false)
        container.addView(nameInput)
        container.addView(categoryInput)

        val triggerSpinner = enumSpinner(TriggerType.values(), initial.trigger.type) { it.label(this) }
        container.addLabeled(getString(R.string.trigger), triggerSpinner)
        val hourInput = edit(initial.trigger.hour.toString(), getString(R.string.time_hour), true)
        val minuteInput = edit(initial.trigger.minute.toString(), getString(R.string.time_minute), true)
        val intervalInput = edit(initial.trigger.intervalMinutes.toString(), getString(R.string.interval_minutes), true)
        val percentInput = edit(initial.trigger.percent.toString(), getString(R.string.battery_percent), true)
        val triggerTextInput = edit(initial.trigger.text, getString(R.string.value), false)
        container.addView(hourInput)
        container.addView(minuteInput)
        container.addView(intervalInput)
        container.addView(percentInput)
        container.addView(triggerTextInput)

        val dayRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dayChecks = (1..7).map { day ->
            CheckBox(this).apply {
                text = if (isEnglish()) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[day - 1] else listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day - 1]
                isChecked = day in initial.trigger.repeatDays
            }.also { dayRow.addView(it) }
        }
        container.addLabeled(if (isEnglish()) "Repeat days" else "重复星期", dayRow)

        val conditionSpinner = enumSpinner(ConditionType.values(), initial.condition.type) { it.label(this) }
        container.addLabeled(getString(R.string.condition), conditionSpinner)
        val conditionPercentInput = edit(initial.condition.percent.toString(), getString(R.string.battery_percent), true)
        val conditionTextInput = edit(initial.condition.text, getString(R.string.value), false)
        container.addView(conditionPercentInput)
        container.addView(conditionTextInput)

        val actionSpinner = enumSpinner(ActionType.values(), initial.action.type) { it.label(this) }
        container.addLabeled(getString(R.string.action), actionSpinner)
        val actionValueInput = edit(initial.action.value.toString(), getString(R.string.value), true)
        val actionTextInput = edit(initial.action.text, getString(R.string.message), false)
        container.addView(actionValueInput)
        container.addView(actionTextInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_rule))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val trigger = TriggerConfig(
                    type = TriggerType.values()[triggerSpinner.selectedItemPosition],
                    hour = number(hourInput, 8, 0, 23),
                    minute = number(minuteInput, 0, 0, 59),
                    repeatDays = dayChecks.mapIndexedNotNull { index, box -> if (box.isChecked) index + 1 else null }.toSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) },
                    intervalMinutes = number(intervalInput, 60, 1, 1440),
                    percent = number(percentInput, 50, 0, 100),
                    text = triggerTextInput.text.toString()
                )
                val rule = AutomationRule(
                    id = initial.id,
                    name = nameInput.text.toString(),
                    enabled = enabled,
                    category = categoryInput.text.toString().ifBlank { "默认" },
                    trigger = trigger,
                    condition = ConditionConfig(
                        type = ConditionType.values()[conditionSpinner.selectedItemPosition],
                        percent = number(conditionPercentInput, 50, 0, 100),
                        text = conditionTextInput.text.toString()
                    ),
                    action = ActionConfig(
                        type = ActionType.values()[actionSpinner.selectedItemPosition],
                        value = number(actionValueInput, 50, 0, 100),
                        text = actionTextInput.text.toString()
                    )
                )
                if (RuleRepository.hasTimeConflict(this, rule)) {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.time_conflict_title))
                        .setMessage(getString(R.string.time_conflict_message))
                        .setPositiveButton(getString(R.string.ok), null)
                        .show()
                } else {
                    RuleRepository.upsertRule(this, rule)
                    render()
                }
            }
            .setNeutralButton(getString(R.string.delete)) { _, _ ->
                RuleRepository.deleteRule(this, initial.id)
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun renderLogs() {
        root.addView(TextView(this).apply {
            text = getString(R.string.execution_log)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addSpace(8)
        val logs = RuleRepository.getLogs(this)
        if (logs.isEmpty()) {
            root.addView(TextView(this).apply { text = getString(R.string.no_logs) })
        } else {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            logs.forEach { log ->
                root.addView(TextView(this).apply {
                    text = "${formatter.format(Date(log.timestampMillis))}\n${log.ruleName}\n${if (log.success) "成功" else "失败"}：${log.message}"
                    textSize = 14f
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = backgroundDrawable(if (log.success) 0xFFEFFAF0.toInt() else 0xFFFFEEEE.toInt())
                })
                root.addSpace(8)
            }
        }
    }

    private fun renderPermissions() {
        root.addView(TextView(this).apply {
            text = getString(R.string.permissions_title)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addSpace(8)
        permissionRow(getString(R.string.battery_optimization), isIgnoringBatteryOptimizations()) { openBatteryOptimizationSettings() }
        permissionRow(getString(R.string.notification_policy), hasNotificationPolicyAccess()) { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        permissionRow(getString(R.string.notification_permission), hasPostNotifications()) { requestNotificationPermission() }
        permissionRow(getString(R.string.usage_access), false) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        permissionRow(getString(R.string.accessibility), false) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun permissionRow(title: String, granted: Boolean, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFF4F4F4.toInt())
        }
        row.addView(TextView(this).apply {
            text = "$title：${if (granted) getString(R.string.granted) else getString(R.string.not_granted)}"
            typeface = Typeface.DEFAULT_BOLD
        })
        row.addView(Button(this).apply {
            text = getString(R.string.open_settings)
            setOnClickListener { action() }
        })
        root.addView(row)
        root.addSpace(8)
    }

    private fun renderSettings() {
        root.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addSpace(8)
        root.addView(TextView(this).apply { text = getString(R.string.language_note) })
        root.addSpace(8)
        root.addView(TextView(this).apply { text = "${getString(R.string.version_label)}：1.0" })
    }

    private fun edit(value: String, hintText: String, number: Boolean): EditText = EditText(this).apply {
        setText(value)
        hint = hintText
        if (number) inputType = InputType.TYPE_CLASS_NUMBER
        setSelectAllOnFocus(true)
    }

    private fun <T> enumSpinner(values: Array<T>, selected: T, label: (T) -> String): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values.map(label))
        setSelection(values.indexOf(selected).coerceAtLeast(0))
    }

    private fun LinearLayout.addLabeled(label: String, view: View) {
        addView(TextView(this@MainActivity).apply {
            text = label
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(view)
    }

    private fun number(input: EditText, fallback: Int, min: Int, max: Int): Int =
        input.text.toString().toIntOrNull()?.coerceIn(min, max) ?: fallback

    private fun hasNotificationPolicyAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return manager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }
            runCatching { startActivity(intent) }.onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun hasPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun LinearLayout.addSpace(dp: Int) {
        addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(dp)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun backgroundDrawable(color: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(12).toFloat()
        }

    private enum class Tab {
        RULES, LOGS, PERMISSIONS, SETTINGS;

        fun label(context: Context): String = when (this) {
            RULES -> context.getString(R.string.tab_rules)
            LOGS -> context.getString(R.string.tab_logs)
            PERMISSIONS -> context.getString(R.string.tab_permissions)
            SETTINGS -> context.getString(R.string.tab_settings)
        }
    }
}
