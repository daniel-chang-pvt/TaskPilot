package com.danielchang.taskpilot.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
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
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * 这个 Activity 只负责页面展示和收集用户输入。规则存储、触发调度、条件判断和动作执行
 * 都在 data/scheduler/engine 包中完成，避免 UI 和业务逻辑混杂。
 *
 * 规则编辑采用“分步选择”的交互：主编辑页只显示概要，触发器、条件和动作分别进入独立
 * 选择界面，避免像测试表单一样把所有字段堆在一个很长的对话框里。
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
            text = getString(R.string.global_enabled) + "\n" +
                if (RuleRepository.isGlobalEnabled(this@MainActivity)) getString(R.string.global_on) else getString(R.string.global_off)
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
            text = "${rule.category} · ${rule.trigger.summary()} · ${rule.action.summary()}"
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
        var draft = initial
        var enabled = initial.enabled

        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val enabledSwitch = Switch(this).apply {
            text = getString(R.string.rule_enabled)
            isChecked = enabled
            setOnCheckedChangeListener { _, checked -> enabled = checked }
        }
        val nameInput = edit(initial.name, getString(R.string.rule_name), false)
        val categoryInput = edit(initial.category, getString(R.string.category), false)
        val triggerSummary = TextView(this)
        val conditionSummary = TextView(this)
        val actionSummary = TextView(this)

        fun refreshSummary() {
            triggerSummary.text = draft.trigger.summary()
            conditionSummary.text = draft.condition.summary()
            actionSummary.text = draft.action.summary()
        }

        container.addView(enabledSwitch)
        container.addView(nameInput)
        container.addView(categoryInput)
        container.addView(selectionBlock(
            title = t("触发器", "Trigger"),
            summaryView = triggerSummary,
            buttonText = t("选择触发器", "Choose trigger")
        ) { showTriggerChooser(draft.trigger) { draft = draft.copy(trigger = it); refreshSummary() } })
        container.addSpace(10)
        container.addView(selectionBlock(
            title = t("条件（可选）", "Condition (optional)"),
            summaryView = conditionSummary,
            buttonText = t("选择条件", "Choose condition")
        ) { showConditionChooser(draft.condition) { draft = draft.copy(condition = it); refreshSummary() } })
        container.addSpace(10)
        container.addView(selectionBlock(
            title = t("动作", "Action"),
            summaryView = actionSummary,
            buttonText = t("选择动作", "Choose action")
        ) { showActionChooser(draft.action) { draft = draft.copy(action = it); refreshSummary() } })
        refreshSummary()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_rule))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val rule = draft.copy(
                    name = nameInput.text.toString(),
                    enabled = enabled,
                    category = categoryInput.text.toString().ifBlank { t("默认", "Default") }
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

    private fun selectionBlock(title: String, summaryView: TextView, buttonText: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFF4F4F4.toInt())
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(summaryView.apply { textSize = 14f })
            addView(Button(this@MainActivity).apply {
                text = buttonText
                setOnClickListener { onClick() }
            })
        }

    private fun showTriggerChooser(current: TriggerConfig, onSelected: (TriggerConfig) -> Unit) {
        chooseEnum(t("选择触发器", "Choose trigger"), TriggerType.values(), current.type, { it.label(this) }) { type ->
            showTriggerDetail(current.copy(type = type), onSelected)
        }
    }

    private fun showTriggerDetail(current: TriggerConfig, onSelected: (TriggerConfig) -> Unit) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hour = edit(current.hour.toString(), getString(R.string.time_hour), true)
        val minute = edit(current.minute.toString(), getString(R.string.time_minute), true)
        val interval = edit(current.intervalMinutes.toString(), getString(R.string.interval_minutes), true)
        val percent = edit(current.percent.toString(), getString(R.string.battery_percent), true)
        val text = edit(current.text, triggerTextHint(current.type), false)
        val dayRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dayChecks = (1..7).map { day ->
            CheckBox(this).apply {
                this.text = dayName(day)
                isChecked = day in current.repeatDays
            }.also { dayRow.addView(it) }
        }

        when (current.type) {
            TriggerType.TIME -> {
                container.addView(hour)
                container.addView(minute)
                container.addLabeled(t("重复星期", "Repeat days"), dayRow)
            }
            TriggerType.INTERVAL -> container.addView(interval)
            TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE -> container.addView(percent)
            TriggerType.WIFI_SSID_CONNECTED,
            TriggerType.BLUETOOTH_DEVICE_CONNECTED,
            TriggerType.BLUETOOTH_DEVICE_DISCONNECTED,
            TriggerType.APP_OPENED,
            TriggerType.APP_CLOSED,
            TriggerType.NOTIFICATION_RECEIVED,
            TriggerType.NOTIFICATION_TEXT,
            TriggerType.SMS_TEXT -> container.addView(text)
            else -> container.addView(TextView(this).apply { this.text = t("此触发器无需额外参数。", "No extra settings are needed.") })
        }

        AlertDialog.Builder(this)
            .setTitle(current.type.label(this))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                onSelected(current.copy(
                    hour = number(hour, current.hour, 0, 23),
                    minute = number(minute, current.minute, 0, 59),
                    repeatDays = dayChecks.mapIndexedNotNull { index, box -> if (box.isChecked) index + 1 else null }.toSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) },
                    intervalMinutes = number(interval, current.intervalMinutes, 1, 1440),
                    percent = number(percent, current.percent, 0, 100),
                    text = text.text.toString()
                ))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showConditionChooser(current: ConditionConfig, onSelected: (ConditionConfig) -> Unit) {
        chooseEnum(t("选择条件", "Choose condition"), ConditionType.values(), current.type, { it.label(this) }) { type ->
            showConditionDetail(current.copy(type = type), onSelected)
        }
    }

    private fun showConditionDetail(current: ConditionConfig, onSelected: (ConditionConfig) -> Unit) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val startHour = edit(current.startHour.toString(), t("开始小时", "Start hour"), true)
        val startMinute = edit(current.startMinute.toString(), t("开始分钟", "Start minute"), true)
        val endHour = edit(current.endHour.toString(), t("结束小时", "End hour"), true)
        val endMinute = edit(current.endMinute.toString(), t("结束分钟", "End minute"), true)
        val percent = edit(current.percent.toString(), getString(R.string.battery_percent), true)
        val text = edit(current.text, getString(R.string.value), false)

        when (current.type) {
            ConditionType.TIME_RANGE -> {
                container.addView(startHour)
                container.addView(startMinute)
                container.addView(endHour)
                container.addView(endMinute)
            }
            ConditionType.BATTERY_BELOW, ConditionType.BATTERY_ABOVE -> container.addView(percent)
            ConditionType.NONE -> container.addView(TextView(this).apply { this.text = t("无条件，触发后直接执行动作。", "No condition. Action runs immediately after trigger.") })
            else -> container.addView(TextView(this).apply { this.text = t("此条件无需额外参数。", "No extra settings are needed.") })
        }
        if (current.type != ConditionType.NONE) container.addView(text)

        AlertDialog.Builder(this)
            .setTitle(current.type.label(this))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                onSelected(current.copy(
                    startHour = number(startHour, current.startHour, 0, 23),
                    startMinute = number(startMinute, current.startMinute, 0, 59),
                    endHour = number(endHour, current.endHour, 0, 23),
                    endMinute = number(endMinute, current.endMinute, 0, 59),
                    percent = number(percent, current.percent, 0, 100),
                    text = text.text.toString()
                ))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showActionChooser(current: ActionConfig, onSelected: (ActionConfig) -> Unit) {
        chooseEnum(t("选择动作", "Choose action"), ActionType.values(), current.type, { it.label(this) }) { type ->
            showActionDetail(current.copy(type = type), onSelected)
        }
    }

    private fun showActionDetail(current: ActionConfig, onSelected: (ActionConfig) -> Unit) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val value = edit(current.value.toString(), getString(R.string.value), true)
        val text = edit(current.text, actionTextHint(current.type), false)

        when (current.type) {
            ActionType.SET_MEDIA_VOLUME,
            ActionType.SET_RING_VOLUME,
            ActionType.SET_NOTIFICATION_VOLUME,
            ActionType.SET_BRIGHTNESS -> container.addView(value)
            ActionType.OPEN_APP,
            ActionType.OPEN_URL,
            ActionType.SHOW_NOTIFICATION,
            ActionType.SEND_SMS,
            ActionType.CALL_PHONE,
            ActionType.TOAST,
            ActionType.LOG -> container.addView(text)
            else -> container.addView(TextView(this).apply { this.text = t("此动作无需额外参数。", "No extra settings are needed.") })
        }

        AlertDialog.Builder(this)
            .setTitle(current.type.label(this))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                onSelected(current.copy(
                    value = number(value, current.value, 0, 100),
                    text = text.text.toString()
                ))
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
                    text = "${formatter.format(Date(log.timestampMillis))}\n${log.ruleName}\n${if (log.success) t("成功", "Success") else t("失败", "Failed")}：${log.message}"
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
        permissionRow(
            title = getString(R.string.battery_optimization),
            granted = isIgnoringBatteryOptimizations(),
            description = t("用于提高锁屏、熄屏后的后台触发稳定性。", "Improves background trigger reliability when the phone is locked."),
            actionText = getString(R.string.open_settings)
        ) { openBatteryOptimizationSettings() }
        permissionRow(
            title = getString(R.string.notification_policy),
            granted = hasNotificationPolicyAccess(),
            description = t("用于切换静音、震动、声音模式。进入后允许“自动任务”。", "Required for changing silent/vibrate/normal modes. Allow TaskPilot in settings."),
            actionText = getString(R.string.open_settings)
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        permissionRow(
            title = getString(R.string.notification_permission),
            granted = hasPostNotifications(),
            description = t("用于执行“显示通知”动作。", "Required by the Show Notification action."),
            actionText = getString(R.string.open_settings)
        ) { requestNotificationPermission() }
        permissionRow(
            title = getString(R.string.usage_access),
            granted = hasUsageAccess(),
            description = t("用于检测“打开某个 App / 关闭某个 App”。进入后找到“自动任务”并允许使用情况访问。", "Used for App opened/closed triggers. Find TaskPilot and allow usage access."),
            actionText = getString(R.string.open_settings)
        ) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        permissionRow(
            title = getString(R.string.accessibility),
            granted = false,
            description = t("当前版本未内置辅助功能服务，暂时不需要设置。后续实现音量键、关闭 App、自动点击时再开启。", "No accessibility service is included yet. It will be needed later for volume key, closing apps, or auto click actions."),
            actionText = t("暂不需要设置", "Not needed yet"),
            enabled = false
        ) {}
    }

    private fun permissionRow(
        title: String,
        granted: Boolean,
        description: String,
        actionText: String,
        enabled: Boolean = true,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFF4F4F4.toInt())
        }
        row.addView(TextView(this).apply {
            text = "$title：${if (granted) getString(R.string.granted) else getString(R.string.not_granted)}"
            typeface = Typeface.DEFAULT_BOLD
        })
        row.addView(TextView(this).apply {
            text = description
            textSize = 14f
        })
        row.addView(Button(this).apply {
            text = actionText
            isEnabled = enabled
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

    private fun <T> chooseEnum(title: String, values: Array<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
        val labels = values.map(label).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, values.indexOf(selected).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                onSelected(values[which])
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun edit(value: String, hintText: String, number: Boolean): EditText = EditText(this).apply {
        setText(value)
        hint = hintText
        if (number) inputType = InputType.TYPE_CLASS_NUMBER
        setSelectAllOnFocus(true)
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

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun TriggerConfig.summary(): String = when (type) {
        TriggerType.TIME -> "${type.label(this@MainActivity)} %02d:%02d · ${repeatDays.sorted().joinToString(" ") { dayName(it) }}".format(hour, minute)
        TriggerType.INTERVAL -> "${type.label(this@MainActivity)}：$intervalMinutes ${t("分钟", "minutes")}"
        TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE -> "${type.label(this@MainActivity)}：$percent%"
        TriggerType.WIFI_SSID_CONNECTED,
        TriggerType.BLUETOOTH_DEVICE_CONNECTED,
        TriggerType.BLUETOOTH_DEVICE_DISCONNECTED,
        TriggerType.APP_OPENED,
        TriggerType.APP_CLOSED,
        TriggerType.NOTIFICATION_RECEIVED,
        TriggerType.NOTIFICATION_TEXT,
        TriggerType.SMS_TEXT -> "${type.label(this@MainActivity)}：${text.ifBlank { t("未指定", "Not set") }}"
        else -> type.label(this@MainActivity)
    }

    private fun ConditionConfig.summary(): String = when (type) {
        ConditionType.NONE -> type.label(this@MainActivity)
        ConditionType.TIME_RANGE -> "%s %02d:%02d-%02d:%02d".format(type.label(this@MainActivity), startHour, startMinute, endHour, endMinute)
        ConditionType.BATTERY_BELOW, ConditionType.BATTERY_ABOVE -> "${type.label(this@MainActivity)}：$percent%"
        else -> type.label(this@MainActivity)
    }

    private fun ActionConfig.summary(): String = when (type) {
        ActionType.SET_MEDIA_VOLUME,
        ActionType.SET_RING_VOLUME,
        ActionType.SET_NOTIFICATION_VOLUME,
        ActionType.SET_BRIGHTNESS -> "${type.label(this@MainActivity)}：$value%"
        ActionType.OPEN_APP,
        ActionType.OPEN_URL,
        ActionType.SHOW_NOTIFICATION,
        ActionType.SEND_SMS,
        ActionType.CALL_PHONE,
        ActionType.TOAST,
        ActionType.LOG -> "${type.label(this@MainActivity)}：${text.ifBlank { t("未填写", "Empty") }}"
        else -> type.label(this@MainActivity)
    }

    private fun triggerTextHint(type: TriggerType): String = when (type) {
        TriggerType.WIFI_SSID_CONNECTED -> t("Wi-Fi 名称，例如 Home", "Wi-Fi name, e.g. Home")
        TriggerType.BLUETOOTH_DEVICE_CONNECTED,
        TriggerType.BLUETOOTH_DEVICE_DISCONNECTED -> t("蓝牙设备名称", "Bluetooth device name")
        TriggerType.APP_OPENED,
        TriggerType.APP_CLOSED -> t("应用包名，例如 com.android.chrome", "Package name, e.g. com.android.chrome")
        TriggerType.NOTIFICATION_RECEIVED -> t("应用包名，可留空", "App package name, optional")
        TriggerType.NOTIFICATION_TEXT -> t("通知关键词", "Notification keyword")
        TriggerType.SMS_TEXT -> t("短信关键词", "SMS keyword")
        else -> getString(R.string.value)
    }

    private fun actionTextHint(type: ActionType): String = when (type) {
        ActionType.OPEN_APP -> t("应用包名，例如 com.android.chrome", "Package name, e.g. com.android.chrome")
        ActionType.OPEN_URL -> t("网址，例如 https://example.com", "URL, e.g. https://example.com")
        ActionType.SHOW_NOTIFICATION -> t("通知内容", "Notification text")
        ActionType.SEND_SMS -> t("格式：号码|内容", "Format: phone|message")
        ActionType.CALL_PHONE -> t("电话号码", "Phone number")
        ActionType.TOAST -> t("提示文字", "Toast text")
        ActionType.LOG -> t("日志内容", "Log message")
        else -> getString(R.string.value)
    }

    private fun dayName(day: Int): String = if (isEnglish()) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[day - 1]
    } else {
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day - 1]
    }

    private fun t(zh: String, en: String): String = if (isEnglish()) en else zh

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
