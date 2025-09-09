package com.github.svk014.autodbgjetbrains.server.controllers.evaluation

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.debugger.models.ArraySummary
import com.github.svk014.autodbgjetbrains.debugger.models.BasicValue
import com.github.svk014.autodbgjetbrains.debugger.models.ObjectSummary
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
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

    /**
     * Evaluates a condition expression and returns true/false.
     * This method is now a suspend function to correctly handle the asynchronous evaluation.
     */
    suspend fun evaluateCondition(condition: String): Boolean {
        return try {
            val session = XDebuggerManager.getInstance(project).currentSession as? XDebugSessionImpl
            val stackFrame = session?.currentStackFrame
            val evaluator = stackFrame?.evaluator

            if (evaluator == null) {
                logger.debug("IntelliJ evaluator not available, falling back to basic evaluation")
                return evaluateConditionBasic(condition)
            }

            evaluateConditionWithIntelliJ(evaluator, condition)
        } catch (e: Exception) {
            logger.warn("Failed to evaluate condition: $condition", e)
            false
        }
    }

    /**
     * Evaluates a condition using IntelliJ's robust expression evaluator.
     * This is a suspend function that correctly handles the asynchronous callback.
     */
    private suspend fun evaluateConditionWithIntelliJ(evaluator: XDebuggerEvaluator, condition: String): Boolean {
        return suspendCoroutine { continuation ->
            val xExpression = XDebuggerUtil.getInstance()
                .createExpression(condition, null, null, EvaluationMode.EXPRESSION)

            val callback = object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(xValue: XValue) {
                    val presentation = object : XValueNode {
                        override fun setPresentation(icon: javax.swing.Icon?, type: String?, value: String, hasChildren: Boolean) {
                            val result = when (value.lowercase()) {
                                "true" -> true
                                "false" -> false
                                else -> value.isNotEmpty() && value != "null"
                            }
                            continuation.resume(result)
                        }

                        override fun setPresentation(icon: javax.swing.Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
                            val result = presentation.type?.lowercase() == "true"
                            continuation.resume(result)
                        }

                        override fun setFullValueEvaluator(fullValueEvaluator: com.intellij.xdebugger.frame.XFullValueEvaluator) {}
                    }
                    xValue.computePresentation(presentation, XValuePlace.TREE)
                }

                override fun errorOccurred(errorMessage: String) {
                    logger.warn("Expression evaluation error: $errorMessage")
                    continuation.resume(false)
                }
            }
            evaluator.evaluate(xExpression, callback, null)
        }
    }

    /**
     * Basic condition evaluation as a fallback.
     */
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

    /**
     * Gets the current variable value for monitoring operations.
     * This method is now a suspend function and uses the debugger service to get the correct frame.
     */
    suspend fun getCurrentVariableValue(variableName: String): String? {
        return try {
            val variables = debuggerService.getFrameVariables()
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
