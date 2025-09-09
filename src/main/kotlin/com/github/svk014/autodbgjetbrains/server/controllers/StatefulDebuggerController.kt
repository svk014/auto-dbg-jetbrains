package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.models.ApiResponse
import com.github.svk014.autodbgjetbrains.models.OperationResult
import com.github.svk014.autodbgjetbrains.server.controllers.breakpoints.BreakpointManager
import com.github.svk014.autodbgjetbrains.server.controllers.commands.CommandHandler
import com.github.svk014.autodbgjetbrains.server.controllers.evaluation.ExpressionEvaluator
import com.github.svk014.autodbgjetbrains.server.controllers.events.DebuggerEventHandler
import com.github.svk014.autodbgjetbrains.server.controllers.operations.OperationManager
import com.github.svk014.autodbgjetbrains.server.models.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

class StatefulDebuggerController(private val project: Project) {

    private val debuggerService: DebuggerIntegrationService by lazy {
        project.service<DebuggerIntegrationService>()
    }

    private val logger = thisLogger()

    // Component instances
    private val operationManager = OperationManager()
    private val breakpointManager = BreakpointManager(project)
    private val expressionEvaluator = ExpressionEvaluator(project, debuggerService)
    private val eventHandler = DebuggerEventHandler(
        operationManager,
        breakpointManager,
        expressionEvaluator,
        debuggerService
    )
    private val commandHandler = CommandHandler(
        operationManager,
        breakpointManager,
        debuggerService
    )

    /**
     * Main entry point for high-level DSL commands
     */
    suspend fun executeHighLevelCommand(command: String, parameters: Map<String, Any>): ApiResponse {
        return commandHandler.executeCommand(command, parameters)
    }

    /**
     * Public method to inject debugger events (called by debugger integration layer)
     */
    fun notifyDebuggerEvent(event: DebuggerEvent) {
        eventHandler.notifyDebuggerEvent(event)
    }

    /**
     * Get the current operation state
     */
    suspend fun getCurrentState(): DebuggerState {
        return operationManager.getCurrentState()
    }

    /**
     * Get the current operation
     */
    suspend fun getCurrentOperation(): Operation? {
        return operationManager.getCurrentOperation()
    }

    /**
     * Check if the controller is idle and ready for new operations
     */
    suspend fun isIdle(): Boolean {
        return operationManager.isIdle()
    }

    /**
     * Get the result of a specific operation
     */
    fun getOperationResult(operationId: String): OperationResult? {
        return operationManager.getOperationResult(operationId)
    }

    /**
     * Cleanup resources when the controller is no longer needed
     */
    fun dispose() {
        eventHandler.dispose()
        breakpointManager.cleanupControllerBreakpoints()
        breakpointManager.restoreUserBreakpoints()
    }
}
