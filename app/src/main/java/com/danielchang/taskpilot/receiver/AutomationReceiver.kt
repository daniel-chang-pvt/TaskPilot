package com.danielchang.taskpilot.receiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.danielchang.taskpilot.data.RuleRepository
import com.danielchang.taskpilot.engine.RuleEngine
import com.danielchang.taskpilot.model.TriggerType
import com.danielchang.taskpilot.scheduler.AutomationScheduler
import com.danielchang.taskpilot.service.AutomationMonitorService
import com.danielchang.taskpilot.system.NetworkState

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!RuleRepository.isGlobalEnabled(context)) return
        when (intent.action) {
            AutomationScheduler.ACTION_RULE_ALARM -> handleRuleAlarm(context, intent)
            AutomationScheduler.ACTION_BATTERY_CHECK -> handleBatteryCheck(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AutomationScheduler.scheduleAll(context)
                AutomationMonitorService.start(context)
                RuleEngine.executeMatching(context, TriggerType.BOOT, "boot")
            }
            Intent.ACTION_POWER_CONNECTED -> RuleEngine.executeMatching(context, TriggerType.POWER_CONNECTED, "power_connected")
            Intent.ACTION_POWER_DISCONNECTED -> RuleEngine.executeMatching(context, TriggerType.POWER_DISCONNECTED, "power_disconnected")
            Intent.ACTION_SCREEN_ON -> RuleEngine.executeMatching(context, TriggerType.SCREEN_ON, "screen_on")
            Intent.ACTION_SCREEN_OFF -> RuleEngine.executeMatching(context, TriggerType.SCREEN_OFF, "screen_off")
            Intent.ACTION_USER_PRESENT -> RuleEngine.executeMatching(context, TriggerType.USER_PRESENT, "user_present")
            BluetoothAdapter.ACTION_STATE_CHANGED -> handleBluetoothState(context, intent)
            BluetoothDevice.ACTION_ACL_CONNECTED -> handleBluetoothDevice(context, intent, TriggerType.BLUETOOTH_DEVICE_CONNECTED)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleBluetoothDevice(context, intent, TriggerType.BLUETOOTH_DEVICE_DISCONNECTED)
            AutomationMonitorService.NETWORK_STATE_CHANGED_ACTION,
            AutomationMonitorService.CONNECTIVITY_ACTION -> handleWifi(context)
            Intent.ACTION_HEADSET_PLUG -> handleHeadset(context, intent)
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSms(context, intent)
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> handlePhone(context, intent)
            Intent.ACTION_NEW_OUTGOING_CALL -> RuleEngine.executeMatching(context, TriggerType.OUTGOING_CALL, "outgoing_call")
        }
    }

    private fun handleRuleAlarm(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(AutomationScheduler.EXTRA_RULE_ID, -1L)
        val rule = RuleRepository.getRules(context).firstOrNull { it.id == ruleId } ?: return
        RuleEngine.executeRule(context, rule, "alarm")
        if (rule.trigger.type == TriggerType.TIME || rule.trigger.type == TriggerType.INTERVAL) AutomationScheduler.scheduleRule(context, rule)
    }

    private fun handleBatteryCheck(context: Context) {
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        RuleEngine.executeMatching(context, TriggerType.BATTERY_BELOW, "battery_below") { level < it.trigger.percent }
        RuleEngine.executeMatching(context, TriggerType.BATTERY_ABOVE, "battery_above") { level > it.trigger.percent }
        AutomationScheduler.scheduleBatteryCheck(context)
    }

    private fun handleBluetoothState(context: Context, intent: Intent) {
        when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
            BluetoothAdapter.STATE_ON -> RuleEngine.executeMatching(context, TriggerType.BLUETOOTH_ON, "bluetooth_on")
            BluetoothAdapter.STATE_OFF -> RuleEngine.executeMatching(context, TriggerType.BLUETOOTH_OFF, "bluetooth_off")
        }
    }

    private fun handleBluetoothDevice(context: Context, intent: Intent, type: TriggerType) {
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val name = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            runCatching { device?.name.orEmpty() }.getOrDefault("")
        } else {
            ""
        }
        RuleEngine.executeMatching(context, type, type.name) { it.trigger.text.isBlank() || name.contains(it.trigger.text, ignoreCase = true) }
    }

    private fun handleWifi(context: Context) {
        if (NetworkState.isWifiConnected(context)) {
            val ssid = NetworkState.currentSsid(context)
            RuleEngine.executeMatching(context, TriggerType.WIFI_CONNECTED, "wifi_connected")
            RuleEngine.executeMatching(context, TriggerType.WIFI_SSID_CONNECTED, "wifi_ssid") { it.trigger.text.isBlank() || ssid.contains(it.trigger.text, ignoreCase = true) }
        } else {
            RuleEngine.executeMatching(context, TriggerType.WIFI_DISCONNECTED, "wifi_disconnected")
        }
    }

    private fun handleHeadset(context: Context, intent: Intent) {
        if (intent.getIntExtra("state", 0) == 1) RuleEngine.executeMatching(context, TriggerType.HEADSET_PLUGGED, "headset_plugged")
        else RuleEngine.executeMatching(context, TriggerType.HEADSET_UNPLUGGED, "headset_unplugged")
    }

    private fun handleSms(context: Context, intent: Intent) {
        val text = Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString("\n") { it.displayMessageBody ?: "" }
        RuleEngine.executeMatching(context, TriggerType.SMS_RECEIVED, "sms_received")
        RuleEngine.executeMatching(context, TriggerType.SMS_TEXT, "sms_text") { it.trigger.text.isBlank() || text.contains(it.trigger.text, ignoreCase = true) }
    }

    private fun handlePhone(context: Context, intent: Intent) {
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> RuleEngine.executeMatching(context, TriggerType.PHONE_RINGING, "phone_ringing")
            TelephonyManager.EXTRA_STATE_IDLE -> RuleEngine.executeMatching(context, TriggerType.CALL_ENDED, "call_ended")
        }
    }
}
