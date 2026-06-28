package com.danielchang.taskpilot.engine

import android.content.Context
import com.danielchang.taskpilot.data.RuleRepository
import com.danielchang.taskpilot.model.AutomationRule
import com.danielchang.taskpilot.model.TriggerType

/**
 * 规则执行编排器。
 *
 * 这个类只负责“流程编排”：找规则、检查总开关、判断条件、调用动作执行器、写日志。
 * 具体条件怎么判断交给 [ConditionEvaluator]，具体动作怎么执行交给 [ActionExecutor]。
 * 这样后续新增触发器/条件/动作时，不会把所有系统 API 都堆在一个大类里。
 */
object RuleEngine {
    fun executeRule(context: Context, rule: AutomationRule, source: String) {
        if (!RuleRepository.isGlobalEnabled(context) || !rule.enabled) return

        if (!ConditionEvaluator.matches(context, rule.condition)) {
            RuleRepository.addLog(context, rule, false, "条件不满足，来源：$source")
            return
        }

        val result = runCatching { ActionExecutor.execute(context, rule.action) }
        val message = result.fold(
            onSuccess = { "执行成功：$it" },
            onFailure = { "执行失败：${it.message}" }
        )
        RuleRepository.addLog(context, rule, result.isSuccess, message)
    }

    fun executeMatching(
        context: Context,
        triggerType: TriggerType,
        sourceText: String,
        matcher: (AutomationRule) -> Boolean = { true }
    ) {
        RuleRepository.getRules(context)
            .filter { it.enabled && it.trigger.type == triggerType && matcher(it) }
            .forEach { executeRule(context, it, sourceText) }
    }
}
