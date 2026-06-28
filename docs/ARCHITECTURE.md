# TaskPilot 架构说明

## 设计目标

TaskPilot 是一个轻量自动化工具。核心模型与 MacroDroid 类似：

```text
触发器 Trigger
  -> 条件 Condition
  -> 动作 Action
  -> 执行日志 ExecutionLog
```

第一版目标是建立稳定可扩展的骨架，而不是一次性实现所有高级能力。

## 主要文件

- `AutomationModels.kt`
  - 规则模型、触发器类型、条件类型、动作类型、执行日志模型。

- `RuleRepository.kt`
  - 使用 `SharedPreferences` + JSON 保存总开关、规则列表和执行日志。

- `AutomationScheduler.kt`
  - 使用 `AlarmManager.setAlarmClock` 安排时间触发和间隔触发。
  - 使用 15 分钟周期检查电量阈值触发器。

- `AutomationReceiver.kt`
  - 接收系统广播和自定义闹钟广播。
  - 将不同广播映射为不同触发器。

- `RuleEngine.kt`
  - 检查规则条件。
  - 执行动作。
  - 写入执行日志。

- `TaskPilotNotificationListener.kt`
  - 通知监听服务。
  - 用户需要手动授予通知使用权后才会生效。

- `MainActivity.kt`
  - 原生 View 界面。
  - 提供规则、日志、权限、设置四个页面。

## 当前架构取舍

- 不使用 Compose，减少依赖和云端构建风险。
- 不使用数据库，第一版规则数量有限，`SharedPreferences` 足够。
- 时间触发使用 `setAlarmClock`，提高锁屏/熄屏触发可靠性。
- 高风险能力先预留模型和入口，后续逐个增强。

## 数据结构

规则 JSON 示例：

```json
{
  "id": 1710000000000,
  "name": "晚上静音",
  "enabled": true,
  "category": "声音",
  "trigger": {
    "type": "TIME",
    "hour": 22,
    "minute": 0,
    "repeatDays": [1, 2, 3, 4, 5, 6, 7],
    "intervalMinutes": 60,
    "percent": 50,
    "text": ""
  },
  "condition": {
    "type": "NONE",
    "percent": 50,
    "text": ""
  },
  "action": {
    "type": "SET_RINGER_SILENT",
    "value": 50,
    "text": ""
  }
}
```

## 后续开发建议

优先增强这些模块：

- 前台服务：提高屏幕、蓝牙、Wi-Fi、App 状态监听稳定性。
- 使用情况访问：实现 App 打开/关闭检测。
- 辅助功能：实现音量键触发、关闭 App、自动点击等高级能力。
- 通知监听：完善通知过滤、清除指定通知。
- 多动作列表：当前第一版是一条规则一个动作，后续可扩展为动作数组。
- 多条件列表：当前第一版是一个条件，后续可扩展为条件数组。

## 权限注意

很多权限需要用户在系统设置中手动开启，不能仅靠 Manifest 自动授权。权限页用于集中引导用户检查。
