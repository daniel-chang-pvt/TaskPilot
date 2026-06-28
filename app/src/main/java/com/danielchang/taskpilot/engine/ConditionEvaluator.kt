package com.danielchang.taskpilot.engine

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import com.danielchang.taskpilot.model.ConditionConfig
import com.danielchang.taskpilot.model.ConditionType
import com.danielchang.taskpilot.model.currentDayOfWeek
import com.danielchang.taskpilot.system.NetworkState
import java.util.Calendar

/**
 * 条件评估器。
 *
 * 条件是规则执行前的“闸门”。这里集中读取系统状态，避免 UI、Receiver、RuleEngine
 * 到处直接调用系统 API。以后要增加新条件，只需要在这里扩展。
 */
object ConditionEvaluator {
    fun matches(context: Context, condition: ConditionConfig): Boolean = when (condition.type) {
        ConditionType.NONE -> true
        ConditionType.TIME_RANGE -> isInTimeRange(condition)
        ConditionType.WEEKDAY -> currentDayOfWeek() in 1..5
        ConditionType.WEEKEND -> currentDayOfWeek() in 6..7
        ConditionType.BATTERY_BELOW -> batteryPercent(context) < condition.percent
        ConditionType.BATTERY_ABOVE -> batteryPercent(context) > condition.percent
        ConditionType.CHARGING -> isCharging(context)
        ConditionType.NOT_CHARGING -> !isCharging(context)
        ConditionType.WIFI_CONNECTED -> NetworkState.isWifiConnected(context)
        ConditionType.BLUETOOTH_ON -> BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        ConditionType.SCREEN_ON -> true
        ConditionType.SCREEN_OFF -> true
        ConditionType.RINGER_SILENT -> ringerMode(context) == AudioManager.RINGER_MODE_SILENT
        ConditionType.RINGER_VIBRATE -> ringerMode(context) == AudioManager.RINGER_MODE_VIBRATE
        ConditionType.RINGER_NORMAL -> ringerMode(context) == AudioManager.RINGER_MODE_NORMAL
        ConditionType.IN_CALL -> false
        ConditionType.NOT_IN_CALL -> true
        ConditionType.HEADSET_PLUGGED -> false
    }

    private fun ringerMode(context: Context): Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode

    private fun batteryPercent(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return (level * 100 / scale.coerceAtLeast(1)).coerceIn(0, 100)
    }

    private fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun isInTimeRange(condition: ConditionConfig): Boolean {
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = condition.startHour * 60 + condition.startMinute
        val end = condition.endHour * 60 + condition.endMinute
        return if (start <= end) current in start..end else current >= start || current <= end
    }
}
