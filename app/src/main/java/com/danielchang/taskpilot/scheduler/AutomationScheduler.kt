package com.danielchang.taskpilot.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.danielchang.taskpilot.data.RuleRepository
import com.danielchang.taskpilot.model.AutomationRule
import com.danielchang.taskpilot.model.TriggerConfig
import com.danielchang.taskpilot.model.TriggerType
import com.danielchang.taskpilot.receiver.AutomationReceiver
import com.danielchang.taskpilot.ui.MainActivity
import java.util.Calendar

object AutomationScheduler {
    const val ACTION_RULE_ALARM = "com.danielchang.taskpilot.ACTION_RULE_ALARM"
    const val ACTION_BATTERY_CHECK = "com.danielchang.taskpilot.ACTION_BATTERY_CHECK"
    const val EXTRA_RULE_ID = "rule_id"
    private const val REQUEST_BATTERY_CHECK = 900001

    fun scheduleAll(context: Context) {
        cancelAll(context)
        if (!RuleRepository.isGlobalEnabled(context)) return

        val rules = RuleRepository.getRules(context).filter { it.enabled }
        rules.forEach { rule ->
            when (rule.trigger.type) {
                TriggerType.TIME, TriggerType.INTERVAL -> scheduleRule(context, rule)
                else -> Unit
            }
        }
        if (rules.any { it.trigger.type == TriggerType.BATTERY_BELOW || it.trigger.type == TriggerType.BATTERY_ABOVE }) {
            scheduleBatteryCheck(context)
        }
    }

    fun scheduleRule(context: Context, rule: AutomationRule) {
        if (!RuleRepository.isGlobalEnabled(context) || !rule.enabled) return
        val triggerAt = when (rule.trigger.type) {
            TriggerType.TIME -> nextTimeMillis(rule.trigger)
            TriggerType.INTERVAL -> System.currentTimeMillis() + rule.trigger.intervalMinutes.coerceAtLeast(1) * 60_000L
            else -> return
        }
        alarmManager(context).setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent(context, rule.id)),
            rulePendingIntent(context, rule.id)
        )
    }

    fun cancelRule(context: Context, ruleId: Long) {
        alarmManager(context).cancel(rulePendingIntent(context, ruleId))
    }

    fun cancelAll(context: Context) {
        RuleRepository.getRules(context).forEach { cancelRule(context, it.id) }
        alarmManager(context).cancel(batteryCheckPendingIntent(context))
    }

    fun scheduleBatteryCheck(context: Context) {
        val triggerAt = System.currentTimeMillis() + 15 * 60_000L
        alarmManager(context).setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent(context, REQUEST_BATTERY_CHECK.toLong())),
            batteryCheckPendingIntent(context)
        )
    }

    private fun nextTimeMillis(trigger: TriggerConfig): Long {
        val now = Calendar.getInstance()
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, trigger.hour)
                set(Calendar.MINUTE, trigger.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.after(now) && currentDayFor(candidate) in trigger.repeatDays) return candidate.timeInMillis
        }
        return now.timeInMillis + 24 * 60 * 60_000L
    }

    private fun currentDayFor(calendar: Calendar): Int = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }

    private fun rulePendingIntent(context: Context, ruleId: Long): PendingIntent {
        val intent = Intent(context, AutomationReceiver::class.java).apply {
            action = ACTION_RULE_ALARM
            putExtra(EXTRA_RULE_ID, ruleId)
        }
        return PendingIntent.getBroadcast(context, ruleId.hashCode(), intent, flags())
    }

    private fun batteryCheckPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutomationReceiver::class.java).apply { action = ACTION_BATTERY_CHECK }
        return PendingIntent.getBroadcast(context, REQUEST_BATTERY_CHECK, intent, flags())
    }

    private fun showPendingIntent(context: Context, request: Long): PendingIntent =
        PendingIntent.getActivity(context, request.hashCode(), Intent(context, MainActivity::class.java), flags())

    private fun alarmManager(context: Context): AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
