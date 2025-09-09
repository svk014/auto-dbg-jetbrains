package com.github.svk014.autodbgjetbrains.server.controllers.commands

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.server.controllers.breakpoints.BreakpointManager
import com.github.svk014.autodbgjetbrains.server.controllers.operations.OperationManager
import com.github.svk014.autodbgjetbrains.server.models.*
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Handles high-level debugging commands and coordinates their execution
 */
class CommandHandler(
    private val operationManager: OperationManager,
    private val breakpointManager: BreakpointManager,
    private val debuggerService: DebuggerIntegrationService
) {
    private val logger = thisLogger()

    suspend fun executeCommand(command: String, parameters: Map<String, Any>): ApiResponse {
        return when (command.lowercase()) {
            "trace_function_calls" -> startTraceFunctionCalls(parameters)
            "find_value_change" -> startFindValueChange(parameters)
            "step_until_condition" -> startStepUntilCondition(parameters)
            "get_operation_status" -> getOperationStatus(parameters)
            else -> ApiResponse.error("Unknown command: $command")
        }
    }

    /**
     * Helper function to reduce duplication in starting new operations.
     */
    private suspend fun <T : Operation> startNewOperation(
        operationType: String,
        debuggerState: DebuggerState,
        operationCreator: (String, Long) -> T,
        startAction: suspend (operationId: String) -> ApiResponse
    ): ApiResponse {
        if (!operationManager.isIdle()) {
            val currentOp = operationManager.getCurrentOperation()
            return ApiResponse.error("Controller is busy with operation: ${currentOp?.id}")
        }

        // Always disable user breakpoints before starting a controller-driven task
        breakpointManager.disableAllUserBreakpoints()

        val operationId = operationManager.generateOperationId(operationType)
        val operation = operationCreator(operationId, System.currentTimeMillis())

        val started = operationManager.startOperation(operation, debuggerState)
        if (!started) {
            breakpointManager.restoreUserBreakpoints()
            return ApiResponse.error("Failed to start operation - controller not idle")
        }

        return try {
            startAction(operationId)
        } catch (e: Exception) {
            operationManager.completeOperation(
                OperationResult(
                    operationId = operationId,
                    status = "failed",
                    data = null,
                    message = "Failed to start operation: ${e.message}"
                )
            )
            breakpointManager.restoreUserBreakpoints()
            ApiResponse.error("Failed to start operation: ${e.message}")
        }
    }

    private suspend fun startTraceFunctionCalls(parameters: Map<String, Any>): ApiResponse {
        val functionName = parameters["function"] as? String
            ?: return ApiResponse.error("Missing required parameter: function")
        val maxCalls = (parameters["max_calls"] as? Number)?.toInt() ?: 10
        val captureArgs = (parameters["capture_args"] as? Boolean) ?: true
        val captureReturn = (parameters["capture_return"] as? Boolean) ?: true

        return startNewOperation(
            operationType = "trace",
            debuggerState = DebuggerState.TRACING_FUNCTION,
            operationCreator = { id, timestamp ->
                Operation.TraceFunctionCalls(
                    id = id,
                    requestedAt = timestamp,
                    functionName = functionName,
                    maxCalls = maxCalls,
                    captureArgs = captureArgs,
                    captureReturn = captureReturn
                )
            },
            startAction = { operationId ->
                breakpointManager.setBreakpointForFunction(functionName)
                debuggerService.continueExecution()
                ApiResponse.success(
                    mapOf(
                        "operation_id" to operationId,
                        "status" to "started",
                        "message" to "Tracing function '$functionName' for up to $maxCalls calls"
                    )
                )
            }
        )
    }

    private suspend fun startFindValueChange(parameters: Map<String, Any>): ApiResponse {
        val variableName = parameters["variable"] as? String
            ?: return ApiResponse.error("Missing required parameter: variable")
        val expectedValue = parameters["expected_value"] as? String
        val maxSteps = (parameters["max_steps"] as? Number)?.toInt() ?: 100

        return startNewOperation(
            operationType = "find_change",
            debuggerState = DebuggerState.FINDING_VALUE_CHANGE,
            operationCreator = { id, timestamp ->
                Operation.FindValueChange(
                    id = id,
                    requestedAt = timestamp,
                    variableName = variableName,
                    expectedValue = expectedValue,
                    maxSteps = maxSteps
                )
            },
            startAction = { operationId ->
                // You'll likely need to set a breakpoint here to get to the initial state
                // for monitoring the variable, but that's a separate step for the future.
                ApiResponse.success(
                    mapOf(
                        "operation_id" to operationId,
                        "status" to "started",
                        "message" to "Monitoring variable '$variableName' for changes"
                    )
                )
            }
        )
    }

    private suspend fun startStepUntilCondition(parameters: Map<String, Any>): ApiResponse {
        val condition = parameters["condition"] as? String
            ?: return ApiResponse.error("Missing required parameter: condition")
        val maxSteps = (parameters["max_steps"] as? Number)?.toInt() ?: 50

        return startNewOperation(
            operationType = "step_until",
            debuggerState = DebuggerState.STEPPING_THROUGH,
            operationCreator = { id, timestamp ->
                Operation.StepUntilCondition(
                    id = id,
                    requestedAt = timestamp,
                    condition = condition,
                    maxSteps = maxSteps
                )
            },
            startAction = { operationId ->
                // For this command, you'll probably want to step or run until a specific
                // point, so you might need to set a temporary breakpoint here as well.
                ApiResponse.success(
                    mapOf(
                        "operation_id" to operationId,
                        "status" to "started",
                        "message" to "Stepping until condition: '$condition'"
                    )
                )
            }
        )
    }

    private fun getOperationStatus(parameters: Map<String, Any>): ApiResponse {
        val operationId = parameters["operation_id"] as? String
            ?: return ApiResponse.error("Missing required parameter: operation_id")

        val result = operationManager.getOperationResult(operationId)
            ?: return ApiResponse.error("Operation not found: $operationId")

        return ApiResponse.success(result)
    }
}
