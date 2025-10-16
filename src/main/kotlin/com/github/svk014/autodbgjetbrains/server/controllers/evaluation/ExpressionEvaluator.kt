package com.github.svk014.autodbgjetbrains.server.controllers.evaluation

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.debugger.java.SmartSerializer
import com.github.svk014.autodbgjetbrains.debugger.models.ArraySummary
import com.github.svk014.autodbgjetbrains.debugger.models.BasicValue
import com.github.svk014.autodbgjetbrains.debugger.models.LlmVariableValue
import com.github.svk014.autodbgjetbrains.debugger.models.ObjectSummary
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Handles expression evaluation using IntelliJ's debugger APIs
 */
class ExpressionEvaluator(
    private val project: Project,
    private val debuggerService: DebuggerIntegrationService
) {
    private val logger = thisLogger()

    suspend fun evaluateExpression(expression: String, frameIndex: Int): LlmVariableValue? {
        logger.info("Evaluating expression: $expression at frame index: $frameIndex")
        val session = XDebuggerManager.getInstance(project).currentSession as? XDebugSessionImpl
        val stackFrame: XStackFrame? = session?.currentStackFrame
        val evaluator = stackFrame?.evaluator

        if (evaluator == null) {
            logger.warn("IntelliJ evaluator not available for frame index: $frameIndex")
            return null
        }

        val xValue = evaluateWithIntelliJ(evaluator, expression)
        return SmartSerializer.serializeValue(xValue)
    }

    suspend fun evaluateCondition(condition: String): Boolean {
        return try {
            val session = XDebuggerManager.getInstance(project).currentSession as? XDebugSessionImpl
            val stackFrame = session?.currentStackFrame
            val evaluator = stackFrame?.evaluator

            if (evaluator == null) {
                logger.debug("IntelliJ evaluator not available, falling back to basic evaluation")
                return evaluateConditionBasic(condition)
            }

            val xValue = evaluateWithIntelliJ(evaluator, condition)
            val result = SmartSerializer.serializeValue(xValue)
            when (result.toString().lowercase()) {
                "true" -> true
                "false" -> false
                else -> result.toString().isNotEmpty() && result.toString() != "null" && result.toString() != "undefined"
            }
        } catch (e: Exception) {
            logger.warn("Failed to evaluate condition: $condition", e)
            false
        }
    }

    private suspend fun evaluateWithIntelliJ(evaluator: XDebuggerEvaluator, expression: String): XValue {
        return suspendCoroutine { continuation ->
            val xExpression = XDebuggerUtil.getInstance()
                .createExpression(expression, null, null, EvaluationMode.EXPRESSION)

            val callback = object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(xValue: XValue) {
                    continuation.resume(xValue)
                }

                override fun errorOccurred(errorMessage: String) {
                    logger.warn("Expression evaluation error: $errorMessage")
                    continuation.resumeWithException(RuntimeException(errorMessage))
                }
            }
            evaluator.evaluate(xExpression, callback, null)
        }
    }

    private fun evaluateConditionBasic(condition: String): Boolean {
        // Implementation remains the same, but it should rarely be used now
        return try {
            val trimmedCondition = condition.trim().lowercase()

            when (trimmedCondition) {
                "true" -> true
                "false" -> false
                else -> false
            }
        } catch (e: Exception) {
            logger.warn("Basic condition evaluation failed for '$condition': ${e.message}")
            false
        }
    }

    suspend fun getCurrentVariableValue(variableName: String): String? {
        return try {
            val variables = debuggerService.getFrameVariables("0")
            variables[variableName]?.let { variable ->
                when (val value = variable.value) {
                    is BasicValue -> value.value
                    is ObjectSummary -> value.summary
                    is ArraySummary -> "Array[${value.size}]: ${value.firstElements.joinToString(", ")}"
                    else -> value.toString()
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to get variable value for $variableName", e)
            null
        }
    }
}
