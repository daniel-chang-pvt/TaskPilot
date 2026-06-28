package com.danielchang.taskpilot.data

import android.content.Context
import com.danielchang.taskpilot.model.ActionConfig
import com.danielchang.taskpilot.model.ActionType
import com.danielchang.taskpilot.model.AutomationRule
import com.danielchang.taskpilot.model.ConditionConfig
import com.danielchang.taskpilot.model.ConditionType
import com.danielchang.taskpilot.model.ExecutionLog
import com.danielchang.taskpilot.model.TriggerConfig
import com.danielchang.taskpilot.model.TriggerType
import com.danielchang.taskpilot.scheduler.AutomationScheduler
import org.json.JSONArray
import org.json.JSONObject

object RuleRepository {
    private const val PREFS_NAME = "task_pilot"
    private const val KEY_GLOBAL_ENABLED = "global_enabled"
    private const val KEY_RULES = "rules"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 300

    fun isGlobalEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_GLOBAL_ENABLED, false)

    fun setGlobalEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
        if (enabled) AutomationScheduler.scheduleAll(context) else AutomationScheduler.cancelAll(context)
    }

    fun getRules(context: Context): List<AutomationRule> {
        val raw = prefs(context).getString(KEY_RULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) add(array.getJSONObject(index).toRule())
            }
        }.getOrDefault(emptyList())
    }

    fun saveRules(context: Context, rules: List<AutomationRule>) {
        val array = JSONArray()
        rules.sortedWith(compareBy<AutomationRule> { it.category }.thenBy { it.name }).forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_RULES, array.toString()).apply()
        if (isGlobalEnabled(context)) AutomationScheduler.scheduleAll(context) else AutomationScheduler.cancelAll(context)
    }

    fun upsertRule(context: Context, rule: AutomationRule) {
        val current = getRules(context)
        val updated = if (current.any { it.id == rule.id }) {
            current.map { if (it.id == rule.id) rule else it }
        } else {
            current + rule
        }
        saveRules(context, updated)
    }

    fun deleteRule(context: Context, ruleId: Long) {
        AutomationScheduler.cancelRule(context, ruleId)
        saveRules(context, getRules(context).filterNot { it.id == ruleId })
    }

    fun copyRule(context: Context, rule: AutomationRule) {
        upsertRule(context, rule.copy(id = System.currentTimeMillis(), name = rule.name + " Copy", enabled = false))
    }

    fun hasTimeConflict(context: Context, rule: AutomationRule): Boolean {
        if (rule.trigger.type != TriggerType.TIME) return false
        return getRules(context).any {
            it.id != rule.id &&
                it.trigger.type == TriggerType.TIME &&
                it.trigger.hour == rule.trigger.hour &&
                it.trigger.minute == rule.trigger.minute
        }
    }

    fun getLogs(context: Context): List<ExecutionLog> {
        val raw = prefs(context).getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(ExecutionLog(
                        timestampMillis = item.optLong("timestampMillis"),
                        ruleName = item.optString("ruleName"),
                        success = item.optBoolean("success"),
                        message = item.optString("message")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addLog(context: Context, rule: AutomationRule, success: Boolean, message: String) {
        val updated = (listOf(ExecutionLog(ruleName = rule.displayName(context), success = success, message = message)) + getLogs(context)).take(MAX_LOGS)
        val array = JSONArray()
        updated.forEach { log ->
            array.put(JSONObject().apply {
                put("timestampMillis", log.timestampMillis)
                put("ruleName", log.ruleName)
                put("success", log.success)
                put("message", log.message)
            })
        }
        prefs(context).edit().putString(KEY_LOGS, array.toString()).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun AutomationRule.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("category", category)
        put("trigger", trigger.toJson())
        put("condition", condition.toJson())
        put("action", action.toJson())
    }

    private fun TriggerConfig.toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("hour", hour)
        put("minute", minute)
        put("repeatDays", JSONArray().apply { repeatDays.sorted().forEach { put(it) } })
        put("intervalMinutes", intervalMinutes)
        put("percent", percent)
        put("text", text)
    }

    private fun ConditionConfig.toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("startHour", startHour)
        put("startMinute", startMinute)
        put("endHour", endHour)
        put("endMinute", endMinute)
        put("percent", percent)
        put("text", text)
    }

    private fun ActionConfig.toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("value", value)
        put("text", text)
    }

    private fun JSONObject.toRule(): AutomationRule = AutomationRule(
        id = optLong("id", System.currentTimeMillis()),
        name = optString("name"),
        enabled = optBoolean("enabled", true),
        category = optString("category", "默认"),
        trigger = optJSONObject("trigger")?.toTrigger() ?: TriggerConfig(),
        condition = optJSONObject("condition")?.toCondition() ?: ConditionConfig(),
        action = optJSONObject("action")?.toAction() ?: ActionConfig()
    )

    private fun JSONObject.toTrigger(): TriggerConfig = TriggerConfig(
        type = parseEnum(optString("type"), TriggerType.TIME),
        hour = optInt("hour", 8).coerceIn(0, 23),
        minute = optInt("minute", 0).coerceIn(0, 59),
        repeatDays = optJSONArray("repeatDays")?.toIntSet() ?: setOf(1, 2, 3, 4, 5, 6, 7),
        intervalMinutes = optInt("intervalMinutes", 60).coerceAtLeast(1),
        percent = optInt("percent", 50).coerceIn(0, 100),
        text = optString("text")
    )

    private fun JSONObject.toCondition(): ConditionConfig = ConditionConfig(
        type = parseEnum(optString("type"), ConditionType.NONE),
        startHour = optInt("startHour", 0).coerceIn(0, 23),
        startMinute = optInt("startMinute", 0).coerceIn(0, 59),
        endHour = optInt("endHour", 23).coerceIn(0, 23),
        endMinute = optInt("endMinute", 59).coerceIn(0, 59),
        percent = optInt("percent", 50).coerceIn(0, 100),
        text = optString("text")
    )

    private fun JSONObject.toAction(): ActionConfig = ActionConfig(
        type = parseEnum(optString("type"), ActionType.SET_RINGER_NORMAL),
        value = optInt("value", 50).coerceIn(0, 100),
        text = optString("text")
    )

    private inline fun <reified T : Enum<T>> parseEnum(name: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private fun JSONArray.toIntSet(): Set<Int> = buildSet {
        for (index in 0 until length()) add(optInt(index).coerceIn(1, 7))
    }
}
