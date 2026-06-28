package com.danielchang.taskpilot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import java.util.Calendar
import kotlin.math.roundToInt

object RuleEngine {
    private const val CHANNEL_ID = "taskpilot_default"

    fun executeRule(context: Context, rule: AutomationRule, source: String) {
        if (!RuleRepository.isGlobalEnabled(context) || !rule.enabled) return
        if (!checkCondition(context, rule.condition)) {
            RuleRepository.addLog(context, rule, false, "条件不满足，来源：$source")
            return
        }
        val result = runCatching { executeAction(context, rule.action) }
        val message = result.fold(
            onSuccess = { "执行成功：$it" },
            onFailure = { "执行失败：${it.message}" }
        )
        RuleRepository.addLog(
            context,
            rule,
            result.isSuccess,
            message
        )
    }

    fun executeMatching(context: Context, triggerType: TriggerType, sourceText: String, matcher: (AutomationRule) -> Boolean = { true }) {
        RuleRepository.getRules(context)
            .filter { it.enabled && it.trigger.type == triggerType && matcher(it) }
            .forEach { executeRule(context, it, sourceText) }
    }

    private fun checkCondition(context: Context, condition: ConditionConfig): Boolean = when (condition.type) {
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

    private fun executeAction(context: Context, action: ActionConfig): String = when (action.type) {
        ActionType.SET_RINGER_SILENT -> setRinger(context, AudioManager.RINGER_MODE_SILENT, "静音")
        ActionType.SET_RINGER_VIBRATE -> setRinger(context, AudioManager.RINGER_MODE_VIBRATE, "震动")
        ActionType.SET_RINGER_NORMAL -> setRinger(context, AudioManager.RINGER_MODE_NORMAL, "声音")
        ActionType.SET_MEDIA_VOLUME -> setVolume(context, AudioManager.STREAM_MUSIC, action.value, "媒体音量")
        ActionType.SET_RING_VOLUME -> setVolume(context, AudioManager.STREAM_RING, action.value, "铃声音量")
        ActionType.SET_NOTIFICATION_VOLUME -> setVolume(context, AudioManager.STREAM_NOTIFICATION, action.value, "通知音量")
        ActionType.OPEN_WIFI_SETTINGS -> openActivity(context, Intent(Settings.ACTION_WIFI_SETTINGS), "打开 Wi-Fi 设置")
        ActionType.OPEN_BLUETOOTH_SETTINGS -> openActivity(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "打开蓝牙设置")
        ActionType.SET_BRIGHTNESS -> "亮度动作暂未直接修改系统设置，请后续接入写入系统设置权限"
        ActionType.OPEN_APP -> openApp(context, action.text)
        ActionType.OPEN_URL -> openUrl(context, action.text)
        ActionType.SHOW_NOTIFICATION -> showNotification(context, action.text.ifBlank { "自动任务已执行" })
        ActionType.CLEAR_OWN_NOTIFICATIONS -> clearOwnNotifications(context)
        ActionType.SEND_SMS -> sendSms(context, action.text)
        ActionType.CALL_PHONE -> callPhone(context, action.text)
        ActionType.TOAST -> showToast(context, action.text.ifBlank { "自动任务已执行" })
        ActionType.LOG -> action.text.ifBlank { "记录日志" }
    }

    private fun setRinger(context: Context, mode: Int, label: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = mode
        return "已切换为$label"
    }

    private fun setVolume(context: Context, stream: Int, percent: Int, label: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(stream)
        val value = (max * percent.coerceIn(0, 100) / 100f).roundToInt().coerceIn(0, max)
        audioManager.setStreamVolume(stream, value, 0)
        return "$label 已设置为 $percent%"
    }

    private fun showNotification(context: Context, text: String): String {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "TaskPilot", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return "通知权限未允许"
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
        return "已显示通知：$text"
    }

    private fun clearOwnNotifications(context: Context): String {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        return "已清除本应用通知"
    }

    private fun showToast(context: Context, text: String): String {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        vibrate(context, 80)
        return "已弹出提示：$text"
    }

    private fun openApp(context: Context, packageName: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "未找到应用：$packageName"
        return openActivity(context, intent, "已打开应用：$packageName")
    }

    private fun openUrl(context: Context, url: String): String {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        return openActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(normalized)), "已打开网页：$normalized")
    }

    private fun openActivity(context: Context, intent: Intent, success: String): String {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return success
    }

    private fun sendSms(context: Context, raw: String): String {
        val parts = raw.split("|", limit = 2)
        if (parts.size < 2) return "短信格式应为：号码|内容"
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return "短信权限未允许"
        SmsManager.getDefault().sendTextMessage(parts[0], null, parts[1], null, null)
        return "已发送短信到 ${parts[0]}"
    }

    private fun callPhone(context: Context, phone: String): String {
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return "拨号权限未允许"
        return openActivity(context, Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")), "已拨打电话：$phone")
    }

    private fun vibrate(context: Context, millis: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)) else vibrator.vibrate(millis)
    }

    private fun ringerMode(context: Context): Int = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode

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
