package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.models.ApiResponse
import com.github.svk014.autodbgjetbrains.server.controllers.evaluation.ExpressionEvaluator
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

class StatefulDebuggerController(private val project: Project) {

    private val debuggerService: DebuggerIntegrationService by lazy {
        project.service<DebuggerIntegrationService>()
    }

    private val logger = thisLogger()

    // Minimal helpers retained
    private val expressionEvaluator = ExpressionEvaluator(project, debuggerService)

    /**
     * Evaluate an expression in the current debugger frame and return an ApiResponse.
     * Higher-level orchestration (breakpoints, tracing, operation lifecycle) is removed from this class.
     */
    suspend fun evaluateExpression(expression: String, frameIndex: Int): ApiResponse {
        return try {
            val serializedResult = expressionEvaluator.evaluateExpression(expression, frameIndex)
            ApiResponse.success(mapOf("result" to serializedResult))
        } catch (e: Exception) {
            logger.error("Error evaluating expression: ${e.message}", e)
            ApiResponse.error("Failed to evaluate expression: ${e.message}")
        }
    }
}
