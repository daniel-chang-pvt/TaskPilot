package com.danielchang.taskpilot.model

import android.content.Context
import android.os.Build
import java.util.Calendar
import java.util.Locale

enum class TriggerType(val zh: String, val en: String) {
    TIME("指定时间", "Time"),
    INTERVAL("间隔重复", "Interval"),
    BOOT("开机完成", "Boot completed"),
    SCREEN_ON("屏幕打开", "Screen on"),
    SCREEN_OFF("屏幕关闭", "Screen off"),
    USER_PRESENT("解锁手机", "Phone unlocked"),
    BATTERY_BELOW("电量低于", "Battery below"),
    BATTERY_ABOVE("电量高于", "Battery above"),
    POWER_CONNECTED("开始充电", "Power connected"),
    POWER_DISCONNECTED("停止充电", "Power disconnected"),
    WIFI_CONNECTED("连接 Wi-Fi", "Wi-Fi connected"),
    WIFI_DISCONNECTED("断开 Wi-Fi", "Wi-Fi disconnected"),
    WIFI_SSID_CONNECTED("连接指定 Wi-Fi", "Specific Wi-Fi connected"),
    BLUETOOTH_ON("蓝牙开启", "Bluetooth on"),
    BLUETOOTH_OFF("蓝牙关闭", "Bluetooth off"),
    BLUETOOTH_DEVICE_CONNECTED("连接指定蓝牙设备", "Bluetooth device connected"),
    BLUETOOTH_DEVICE_DISCONNECTED("断开指定蓝牙设备", "Bluetooth device disconnected"),
    APP_OPENED("打开某个 App", "App opened"),
    APP_CLOSED("关闭某个 App", "App closed"),
    NOTIFICATION_RECEIVED("收到通知", "Notification received"),
    NOTIFICATION_TEXT("通知包含文字", "Notification contains text"),
    PHONE_RINGING("来电", "Incoming call"),
    OUTGOING_CALL("去电", "Outgoing call"),
    CALL_ENDED("通话结束", "Call ended"),
    SMS_RECEIVED("收到短信", "SMS received"),
    SMS_TEXT("短信包含关键词", "SMS contains text"),
    HEADSET_PLUGGED("插入耳机", "Headset plugged"),
    HEADSET_UNPLUGGED("拔出耳机", "Headset unplugged"),
    VOLUME_KEY("按音量键", "Volume key")
}

enum class ConditionType(val zh: String, val en: String) {
    NONE("无条件", "No condition"),
    TIME_RANGE("某个时间段内", "Within time range"),
    WEEKDAY("星期一到星期五", "Weekday"),
    WEEKEND("周末", "Weekend"),
    BATTERY_BELOW("电量小于", "Battery below"),
    BATTERY_ABOVE("电量大于", "Battery above"),
    CHARGING("正在充电", "Charging"),
    NOT_CHARGING("未充电", "Not charging"),
    WIFI_CONNECTED("已连接 Wi-Fi", "Wi-Fi connected"),
    BLUETOOTH_ON("蓝牙已开启", "Bluetooth on"),
    SCREEN_ON("屏幕开启", "Screen on"),
    SCREEN_OFF("屏幕关闭", "Screen off"),
    RINGER_SILENT("当前静音模式", "Silent mode"),
    RINGER_VIBRATE("当前震动模式", "Vibrate mode"),
    RINGER_NORMAL("当前声音模式", "Normal mode"),
    IN_CALL("正在通话", "In call"),
    NOT_IN_CALL("未在通话", "Not in call"),
    HEADSET_PLUGGED("已插入耳机", "Headset plugged")
}

enum class ActionType(val zh: String, val en: String) {
    SET_RINGER_SILENT("设置静音模式", "Set silent mode"),
    SET_RINGER_VIBRATE("设置震动模式", "Set vibrate mode"),
    SET_RINGER_NORMAL("设置声音模式", "Set normal mode"),
    SET_MEDIA_VOLUME("设置媒体音量", "Set media volume"),
    SET_RING_VOLUME("设置铃声音量", "Set ring volume"),
    SET_NOTIFICATION_VOLUME("设置通知音量", "Set notification volume"),
    OPEN_WIFI_SETTINGS("打开 Wi-Fi 设置", "Open Wi-Fi settings"),
    OPEN_BLUETOOTH_SETTINGS("打开蓝牙设置", "Open Bluetooth settings"),
    SET_BRIGHTNESS("调整亮度", "Set brightness"),
    OPEN_APP("打开 App", "Open app"),
    OPEN_URL("打开网页", "Open URL"),
    SHOW_NOTIFICATION("显示通知", "Show notification"),
    CLEAR_OWN_NOTIFICATIONS("清除本应用通知", "Clear own notifications"),
    SEND_SMS("发送短信", "Send SMS"),
    CALL_PHONE("拨打电话", "Call phone"),
    TOAST("弹出提示", "Toast"),
    LOG("记录日志", "Log")
}

data class TriggerConfig(
    val type: TriggerType = TriggerType.TIME,
    val hour: Int = 8,
    val minute: Int = 0,
    val repeatDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val intervalMinutes: Int = 60,
    val percent: Int = 50,
    val text: String = ""
)

data class ConditionConfig(
    val type: ConditionType = ConditionType.NONE,
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 23,
    val endMinute: Int = 59,
    val percent: Int = 50,
    val text: String = ""
)

data class ActionConfig(
    val type: ActionType = ActionType.SET_RINGER_NORMAL,
    val value: Int = 50,
    val text: String = ""
)

data class AutomationRule(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val enabled: Boolean = true,
    val category: String = "默认",
    val trigger: TriggerConfig = TriggerConfig(),
    val condition: ConditionConfig = ConditionConfig(),
    val action: ActionConfig = ActionConfig()
) {
    fun displayName(context: Context): String = name.ifBlank { trigger.type.label(context) }
}

data class ExecutionLog(
    val timestampMillis: Long = System.currentTimeMillis(),
    val ruleName: String,
    val success: Boolean,
    val message: String
)

fun TriggerType.label(context: Context): String = if (context.isEnglish()) en else zh
fun ConditionType.label(context: Context): String = if (context.isEnglish()) en else zh
fun ActionType.label(context: Context): String = if (context.isEnglish()) en else zh

fun Context.isEnglish(): Boolean {
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        resources.configuration.locales[0] ?: Locale.getDefault()
    } else {
        @Suppress("DEPRECATION")
        resources.configuration.locale ?: Locale.getDefault()
    }
    return locale.language == Locale.ENGLISH.language
}

fun currentDayOfWeek(): Int {
    val calendar = Calendar.getInstance()
    return when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }
}
