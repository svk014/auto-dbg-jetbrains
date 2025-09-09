package com.github.svk014.autodbgjetbrains.server.controllers.events

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.models.FunctionTraceData
import com.github.svk014.autodbgjetbrains.models.OperationResult
import com.github.svk014.autodbgjetbrains.models.SteppingResultData
import com.github.svk014.autodbgjetbrains.models.TracingResultData
import com.github.svk014.autodbgjetbrains.models.ValueChangeResultData
import com.github.svk014.autodbgjetbrains.models.ValueSnapshotData
import com.github.svk014.autodbgjetbrains.server.controllers.breakpoints.BreakpointManager
import com.github.svk014.autodbgjetbrains.server.controllers.evaluation.ExpressionEvaluator
import com.github.svk014.autodbgjetbrains.server.controllers.operations.OperationManager
import com.github.svk014.autodbgjetbrains.server.models.*
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Handles debugger events and coordinates responses based on current operation state
 */
class DebuggerEventHandler(
    private val operationManager: OperationManager,
    private val breakpointManager: BreakpointManager,
    private val expressionEvaluator: ExpressionEvaluator,
    private val debuggerService: DebuggerIntegrationService
) {
    private val logger = thisLogger()
    private val eventChannel = Channel<DebuggerEvent>(Channel.UNLIMITED)
    private val eventScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Start the event processing coroutine
        eventScope.launch {
            processDebuggerEvents()
        }
    }

    private suspend fun processDebuggerEvents() {
        for (event in eventChannel) {
            try {
                handleDebuggerEvent(event)
            } catch (e: Exception) {
                logger.error("Error processing debugger event", e)
            }
        }
    }

    private suspend fun handleDebuggerEvent(event: DebuggerEvent) {
        when (operationManager.getCurrentState()) {
            DebuggerState.TRACING_FUNCTION -> handleTracingFunctionEvent(event)
            DebuggerState.FINDING_VALUE_CHANGE -> handleFindingValueChangeEvent(event)
            DebuggerState.STEPPING_THROUGH -> handleSteppingThroughEvent(event)
            DebuggerState.PAUSED -> handlePausedEvent(event)
            DebuggerState.IDLE -> handleIdleEvent(event)
            DebuggerState.WAITING_FOR_CONDITION -> handleWaitingForConditionEvent(event)
        }
    }

    private suspend fun handleTracingFunctionEvent(event: DebuggerEvent) {
        val operation = operationManager.getCurrentOperation() as? Operation.TraceFunctionCalls ?: return

        when (event) {
            is DebuggerEvent.BreakpointHit -> {
                // Check if this is a controller breakpoint or a user breakpoint
                if (event.breakpoint != null && breakpointManager.isControllerBreakpoint(event.breakpoint)) {
                    // Handle controller breakpoint - proceed with tracing logic
                    logger.info("Controller breakpoint hit at ${event.location} during function tracing")
                    // Continue with normal tracing workflow
                } else {
                    // Handle a non-controller breakpoint - print message and continue
                    logger.warn("Hit non-controller breakpoint at ${event.location}, continuing execution...")
                    println("Hit non-controller breakpoint at ${event.location}, continuing execution...")
                    debuggerService.continueExecution()
                }
            }

            is DebuggerEvent.FunctionEntered -> {
                if (event.functionName == operation.functionName) {
                    val trace = FunctionTraceData(
                        callNumber = operation.currentCallCount + 1,
                        functionName = event.functionName,
                        entryLocation = getCurrentLocation(),
                        arguments = event.args.mapValues { it.value.toString() },
                        returnValue = null,
                        exitLocation = null,
                        timestamp = System.currentTimeMillis()
                    )
                    operation.collectedTraces.add(trace)

                    // Set breakpoint at function exit if needed
                    if (operation.captureReturn) {
                        breakpointManager.setBreakpointForFunctionExit(operation.functionName)
                    }
                }
            }

            is DebuggerEvent.FunctionExited -> {
                if (event.functionName == operation.functionName) {
                    val lastTrace = operation.collectedTraces.lastOrNull()
                    if (lastTrace != null && lastTrace.returnValue == null) {
                        // Update the trace with return value
                        val updatedTrace = lastTrace.copy(
                            returnValue = event.returnValue?.toString(),
                            exitLocation = getCurrentLocation()
                        )
                        operation.collectedTraces[operation.collectedTraces.size - 1] = updatedTrace
                    }

                    // Check if we've reached max calls
                    if (operation.collectedTraces.size >= operation.maxCalls) {
                        completeTracingOperation(operation)
                    } else {
                        // Continue execution to find the next call
                        debuggerService.continueExecution()
                    }
                }
            }

            else -> { /* Handle other events as needed */ }
        }
    }

    private suspend fun handleFindingValueChangeEvent(event: DebuggerEvent) {
        val operation = operationManager.getCurrentOperation() as? Operation.FindValueChange ?: return

        when (event) {
            is DebuggerEvent.StepCompleted -> {
                operation.currentSteps++

                // Check the current value after each step
                val currentValue = expressionEvaluator.getCurrentVariableValue(operation.variableName)
                if (currentValue != null) {
                    val lastValue = operation.valueHistory.lastOrNull()?.value
                    if (currentValue != lastValue) {
                        val snapshot = ValueSnapshotData(
                            stepNumber = operation.currentSteps,
                            location = getCurrentLocation(),
                            variableName = operation.variableName,
                            value = currentValue,
                            timestamp = System.currentTimeMillis()
                        )
                        operation.valueHistory.add(snapshot)

                        // Check if we found the expected value
                        if (operation.expectedValue != null && currentValue == operation.expectedValue) {
                            completeValueChangeOperation(operation, "Found expected value")
                            return
                        }
                    }
                }

                // Check if max steps reached
                if (operation.currentSteps >= operation.maxSteps) {
                    completeValueChangeOperation(operation, "Max steps reached without finding expected value")
                    return
                }

                // Continue stepping to monitor for changes
                try {
                    debuggerService.stepOver()
                } catch (e: Exception) {
                    logger.error("Failed to continue stepping", e)
                    completeValueChangeOperation(operation, "Error during stepping: ${e.message}")
                }
            }

            is DebuggerEvent.ErrorOccurred -> {
                completeValueChangeOperation(operation, "Error occurred: ${event.error}")
            }

            else -> { /* Handle other events */ }
        }
    }

    private suspend fun handleSteppingThroughEvent(event: DebuggerEvent) {
        val operation = operationManager.getCurrentOperation() as? Operation.StepUntilCondition ?: return

        when (event) {
            is DebuggerEvent.StepCompleted -> {
                operation.currentSteps++

                // Try to evaluate the condition
                val conditionMet = try {
                    expressionEvaluator.evaluateCondition(operation.condition)
                } catch (e: Exception) {
                    logger.warn("Failed to evaluate condition: ${operation.condition}", e)
                    false
                }

                if (conditionMet) {
                    // Condition met, complete the operation
                    completeStepUntilConditionOperation(operation, true, "Condition met")
                } else if (operation.currentSteps >= operation.maxSteps) {
                    // Max steps reached without meeting condition
                    completeStepUntilConditionOperation(operation, false, "Max steps reached without meeting condition")
                } else {
                    // Continue stepping
                    try {
                        debuggerService.stepOver()
                    } catch (e: Exception) {
                        logger.error("Failed to continue stepping", e)
                        completeStepUntilConditionOperation(operation, false, "Error during stepping: ${e.message}")
                    }
                }
            }

            is DebuggerEvent.ErrorOccurred -> {
                completeStepUntilConditionOperation(operation, false, "Error occurred: ${event.error}")
            }

            else -> { /* Handle other events as needed */ }
        }
    }

    private suspend fun handlePausedEvent(event: DebuggerEvent) {
        // Handle events when paused at breakpoint
        when (event) {
            is DebuggerEvent.BreakpointHit -> {
                logger.info("Breakpoint hit at ${event.location}")
                operationManager.updateState(DebuggerState.PAUSED)
            }

            else -> {
                logger.debug("Ignoring event $event while paused")
            }
        }
    }

    private suspend fun handleIdleEvent(event: DebuggerEvent) {
        // Handle events when idle - mainly for unexpected events or cleanup
        when (event) {
            is DebuggerEvent.ErrorOccurred -> {
                logger.warn("Unexpected error while idle: ${event.error}")
            }

            else -> {
                logger.debug("Ignoring event $event while idle")
            }
        }
    }

    private suspend fun handleWaitingForConditionEvent(event: DebuggerEvent) {
        // Handle events when waiting for a specific condition
        when (event) {
            is DebuggerEvent.OperationCompleted -> {
                logger.info("Operation ${event.operationId} completed while waiting")
                operationManager.updateState(DebuggerState.IDLE)
            }

            is DebuggerEvent.ErrorOccurred -> {
                logger.error("Error while waiting for condition: ${event.error}")
                operationManager.updateState(DebuggerState.IDLE)
            }

            else -> {
                logger.debug("Event $event received while waiting for condition")
            }
        }
    }

    private suspend fun completeTracingOperation(operation: Operation.TraceFunctionCalls) {
        val resultData = TracingResultData(
            functionName = operation.functionName,
            totalCalls = operation.collectedTraces.size,
            traces = operation.collectedTraces
        )

        val result = OperationResult(
            operationId = operation.id,
            status = "completed",
            data = resultData,
            message = "Traced ${operation.collectedTraces.size} calls to ${operation.functionName}"
        )

        operationManager.completeOperation(result)

        // Clean up any temporary breakpoints and restore user breakpoints
        breakpointManager.cleanupControllerBreakpoints()
        breakpointManager.restoreUserBreakpoints()
    }

    private suspend fun completeValueChangeOperation(operation: Operation.FindValueChange, message: String) {
        val resultData = ValueChangeResultData(
            variableName = operation.variableName,
            totalSteps = operation.valueHistory.size,
            changeHistory = operation.valueHistory
        )

        val result = OperationResult(
            operationId = operation.id,
            status = "completed",
            data = resultData,
            message = message
        )

        operationManager.completeOperation(result)

        // Clean up and restore breakpoints after operation completes
        breakpointManager.cleanupControllerBreakpoints()
        breakpointManager.restoreUserBreakpoints()
    }

    private suspend fun completeStepUntilConditionOperation(
        operation: Operation.StepUntilCondition,
        conditionMet: Boolean,
        message: String
    ) {
        val resultData = SteppingResultData(
            condition = operation.condition,
            totalSteps = operation.currentSteps,
            conditionMet = conditionMet,
            finalLocation = getCurrentLocation()
        )

        val result = OperationResult(
            operationId = operation.id,
            status = "completed",
            data = resultData,
            message = message
        )

        operationManager.completeOperation(result)

        // Clean up and restore breakpoints after operation completes
        breakpointManager.cleanupControllerBreakpoints()
        breakpointManager.restoreUserBreakpoints()
    }

    private fun getCurrentLocation(): String {
        return try {
            val frameInfo = debuggerService.getFrameAt(0)
            frameInfo?.let { frame ->
                val lineNum = frame.lineNumber?.oneBasedNumber ?: "unknown"
                "${frame.filePath}:$lineNum"
            } ?: "unknown location"
        } catch (e: Exception) {
            logger.warn("Failed to get current location", e)
            "unknown location"
        }
    }

    /**
     * Public method to inject debugger events (called by debugger integration layer)
     */
    fun notifyDebuggerEvent(event: DebuggerEvent) {
        eventChannel.trySend(event)
    }

    fun dispose() {
        eventScope.cancel()
        eventChannel.close()
    }
}
