package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.debugger.models.ArraySummary
import com.github.svk014.autodbgjetbrains.debugger.models.BasicValue
import com.github.svk014.autodbgjetbrains.debugger.models.ObjectSummary
import com.github.svk014.autodbgjetbrains.server.models.*
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.breakpoints.displayText
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Stateful debugger controller implementing a state machine for complex debugging operations
 * Handles high-level DSL commands from LLMs and orchestrates multistep debugging workflows
 */
class StatefulDebuggerController(private val project: Project) {

    private val debuggerService: DebuggerIntegrationService by lazy {
        project.service<DebuggerIntegrationService>()
    }

    private val logger = thisLogger()
    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val stateMutex = Mutex()
    private var currentState: DebuggerState = DebuggerState.IDLE
    private var currentOperation: Operation? = null
    private val eventChannel = Channel<DebuggerEvent>(Channel.UNLIMITED)

    private val operationResults = ConcurrentHashMap<String, OperationResult>()
    private val operationIdGenerator = AtomicLong(0)

    // Breakpoint management system
    private val disabledUserBreakpoints = ConcurrentHashMap<XBreakpoint<*>, Boolean>()
    private val controllerBreakpoints = ConcurrentHashMap.newKeySet<XBreakpoint<*>>()

    init {
        // Start the event processing coroutine
        controllerScope.launch {
            processDebuggerEvents()
        }
    }

    /**
     * Main entry point for high-level DSL commands
     */
    suspend fun executeHighLevelCommand(command: String, parameters: Map<String, Any>): ApiResponse {
        return stateMutex.withLock {
            when (command.lowercase()) {
                "trace_function_calls" -> startTraceFunctionCalls(parameters)
                "find_value_change" -> startFindValueChange(parameters)
                "step_until_condition" -> startStepUntilCondition(parameters)
                "get_operation_status" -> getOperationStatus(parameters)
                else -> ApiResponse.error("Unknown command: $command")
            }
        }
    }

    private fun startTraceFunctionCalls(parameters: Map<String, Any>): ApiResponse {
        if (currentState != DebuggerState.IDLE) {
            return ApiResponse.error("Controller is busy with operation: ${currentOperation?.id}")
        }

        val functionName = parameters["function"] as? String
            ?: return ApiResponse.error("Missing required parameter: function")
        val maxCalls = (parameters["max_calls"] as? Number)?.toInt() ?: 10
        val captureArgs = (parameters["capture_args"] as? Boolean) ?: true
        val captureReturn = (parameters["capture_return"] as? Boolean) ?: true

        // Disable all user breakpoints to prevent interference with tracing operation
        disableAllUserBreakpoints()

        val operationId = "trace_${operationIdGenerator.incrementAndGet()}"
        val operation = Operation.TraceFunctionCalls(
            id = operationId,
            requestedAt = System.currentTimeMillis(),
            functionName = functionName,
            maxCalls = maxCalls,
            captureArgs = captureArgs,
            captureReturn = captureReturn
        )

        currentOperation = operation
        currentState = DebuggerState.TRACING_FUNCTION

        // Store initial result
        operationResults[operationId] = OperationResult(
            operationId = operationId,
            status = "in_progress",
            data = null,
            message = "Started tracing function '$functionName'"
        )

        // Set breakpoint at function entry
        try {
            setBreakpointForFunction(functionName)
            debuggerService.continueExecution()

            return ApiResponse.success(
                mapOf(
                    "operation_id" to operationId,
                    "status" to "started",
                    "message" to "Tracing function '$functionName' for up to $maxCalls calls"
                )
            )
        } catch (e: Exception) {
            currentState = DebuggerState.IDLE
            currentOperation = null
            // Restore user breakpoints if operation failed to start
            restoreUserBreakpoints()
            return ApiResponse.error("Failed to start tracing: ${e.message}")
        }
    }

    private fun startFindValueChange(parameters: Map<String, Any>): ApiResponse {
        if (currentState != DebuggerState.IDLE) {
            return ApiResponse.error("Controller is busy with operation: ${currentOperation?.id}")
        }

        val variableName = parameters["variable"] as? String
            ?: return ApiResponse.error("Missing required parameter: variable")
        val expectedValue = parameters["expected_value"] as? String
        val maxSteps = (parameters["max_steps"] as? Number)?.toInt() ?: 100

        val operationId = "find_change_${operationIdGenerator.incrementAndGet()}"
        val operation = Operation.FindValueChange(
            id = operationId,
            requestedAt = System.currentTimeMillis(),
            variableName = variableName,
            expectedValue = expectedValue,
            maxSteps = maxSteps
        )

        currentOperation = operation
        currentState = DebuggerState.FINDING_VALUE_CHANGE

        operationResults[operationId] = OperationResult(
            operationId = operationId,
            status = "in_progress",
            data = null,
            message = "Started monitoring variable '$variableName'"
        )

        return ApiResponse.success(
            mapOf(
                "operation_id" to operationId,
                "status" to "started",
                "message" to "Monitoring variable '$variableName' for changes"
            )
        )
    }

    private fun startStepUntilCondition(parameters: Map<String, Any>): ApiResponse {
        if (currentState != DebuggerState.IDLE) {
            return ApiResponse.error("Controller is busy with operation: ${currentOperation?.id}")
        }

        val condition = parameters["condition"] as? String
            ?: return ApiResponse.error("Missing required parameter: condition")
        val maxSteps = (parameters["max_steps"] as? Number)?.toInt() ?: 50

        val operationId = "step_until_${operationIdGenerator.incrementAndGet()}"
        val operation = Operation.StepUntilCondition(
            id = operationId,
            requestedAt = System.currentTimeMillis(),
            condition = condition,
            maxSteps = maxSteps
        )

        currentOperation = operation
        currentState = DebuggerState.STEPPING_THROUGH

        operationResults[operationId] = OperationResult(
            operationId = operationId,
            status = "in_progress",
            data = null,
            message = "Started stepping until condition: '$condition'"
        )

        return ApiResponse.success(
            mapOf(
                "operation_id" to operationId,
                "status" to "started",
                "message" to "Stepping until condition: '$condition'"
            )
        )
    }

    private fun getOperationStatus(parameters: Map<String, Any>): ApiResponse {
        val operationId = parameters["operation_id"] as? String
            ?: return ApiResponse.error("Missing required parameter: operation_id")

        val result = operationResults[operationId]
            ?: return ApiResponse.error("Operation not found: $operationId")

        return ApiResponse.success(result)
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
        stateMutex.withLock {
            when (currentState) {
                DebuggerState.TRACING_FUNCTION -> handleTracingFunctionEvent(event)
                DebuggerState.FINDING_VALUE_CHANGE -> handleFindingValueChangeEvent(event)
                DebuggerState.STEPPING_THROUGH -> handleSteppingThroughEvent(event)
                DebuggerState.PAUSED -> handlePausedEvent(event)
                DebuggerState.IDLE -> handleIdleEvent(event)
                DebuggerState.WAITING_FOR_CONDITION -> handleWaitingForConditionEvent(event)
            }
        }
    }

    private fun handleTracingFunctionEvent(event: DebuggerEvent) {
        val operation = currentOperation as? Operation.TraceFunctionCalls ?: return

        when (event) {
            is DebuggerEvent.BreakpointHit -> {
                // Check if this is a controller breakpoint or a user breakpoint
                if (event.breakpoint != null && isControllerBreakpoint(event.breakpoint)) {
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
                        setBreakpointForFunctionExit(operation.functionName)
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

    private fun handleFindingValueChangeEvent(event: DebuggerEvent) {
        val operation = currentOperation as? Operation.FindValueChange ?: return

        when (event) {
            is DebuggerEvent.VariableChanged -> {
                if (event.name == operation.variableName) {
                    val snapshot = ValueSnapshotData(
                        stepNumber = operation.currentSteps,
                        location = getCurrentLocation(),
                        variableName = event.name,
                        value = event.newValue?.toString() ?: "null",
                        timestamp = System.currentTimeMillis()
                    )
                    operation.valueHistory.add(snapshot)

                    // Check if we found the expected value
                    if (operation.expectedValue != null &&
                        snapshot.value == operation.expectedValue
                    ) {
                        completeValueChangeOperation(operation, "Found expected value")
                        return
                    }
                }
            }

            is DebuggerEvent.StepCompleted -> {
                operation.currentSteps++

                // Check the current value after each step
                val currentValue = getCurrentVariableValue(operation.variableName)
                if (currentValue != null) {
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

            else -> { /* Handle other events */ }
        }
    }

    private fun handleSteppingThroughEvent(event: DebuggerEvent) {
        val operation = currentOperation as? Operation.StepUntilCondition ?: return

        when (event) {
            is DebuggerEvent.StepCompleted -> {
                operation.currentSteps++

                // Try to evaluate the condition
                val conditionMet = try {
                    evaluateCondition(operation.condition)
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

    private fun handlePausedEvent(event: DebuggerEvent) {
        // Handle events when paused at breakpoint
        when (event) {
            is DebuggerEvent.BreakpointHit -> {
                logger.info("Breakpoint hit at ${event.location}")
                currentState = DebuggerState.PAUSED
            }

            else -> {
                logger.debug("Ignoring event $event while paused")
            }
        }
    }

    private fun handleIdleEvent(event: DebuggerEvent) {
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

    private fun handleWaitingForConditionEvent(event: DebuggerEvent) {
        // Handle events when waiting for a specific condition
        when (event) {
            is DebuggerEvent.OperationCompleted -> {
                logger.info("Operation ${event.operationId} completed while waiting")
                currentState = DebuggerState.IDLE
            }

            is DebuggerEvent.ErrorOccurred -> {
                logger.error("Error while waiting for condition: ${event.error}")
                currentState = DebuggerState.IDLE
            }

            else -> {
                logger.debug("Event $event received while waiting for condition")
            }
        }
    }

    private fun completeTracingOperation(operation: Operation.TraceFunctionCalls) {
        currentState = DebuggerState.IDLE
        currentOperation = null

        val resultData = TracingResultData(
            functionName = operation.functionName,
            totalCalls = operation.collectedTraces.size,
            traces = operation.collectedTraces
        )

        operationResults[operation.id] = OperationResult(
            operationId = operation.id,
            status = "completed",
            data = resultData,
            message = "Traced ${operation.collectedTraces.size} calls to ${operation.functionName}",
            completedAt = System.currentTimeMillis()
        )

        // Clean up any temporary breakpoints and restore user breakpoints
        cleanupTemporaryBreakpoints()
        restoreUserBreakpoints()
    }

    private fun completeValueChangeOperation(operation: Operation.FindValueChange, message: String) {
        currentState = DebuggerState.IDLE
        currentOperation = null

        val resultData = ValueChangeResultData(
            variableName = operation.variableName,
            totalSteps = operation.valueHistory.size,
            changeHistory = operation.valueHistory
        )

        operationResults[operation.id] = OperationResult(
            operationId = operation.id,
            status = "completed",
            data = resultData,
            message = message,
            completedAt = System.currentTimeMillis()
        )
    }

    private fun completeStepUntilConditionOperation(
        operation: Operation.StepUntilCondition,
        conditionMet: Boolean,
        message: String
    ) {
        currentState = DebuggerState.IDLE
        currentOperation = null

        val resultData = SteppingResultData(
            condition = operation.condition,
            totalSteps = operation.currentSteps,
            conditionMet = conditionMet,
            finalLocation = getCurrentLocation()
        )

        operationResults[operation.id] = OperationResult(
            operationId = operation.id,
            status = "completed",
            data = resultData,
            message = message,
            completedAt = System.currentTimeMillis()
        )
    }

    // Utility methods - implementing the core debugger integration functionality
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

    private fun setBreakpointForFunction(functionName: String) {
        // For now, this is a placeholder - would need to:
        // 1. Find all method declarations with the given name
        // 2. Set breakpoints at their entry points
        // 3. Store breakpoint references for cleanup
        logger.info("Setting breakpoint for function: $functionName")
        // TODO: Implement using IntelliJ's breakpoint API when method resolution is available
    }

    private fun setBreakpointForFunctionExit(functionName: String) {
        // For now, this is a placeholder - would need to:
        // 1. Find all return statements in the given function
        // 2. Set breakpoints at each return point
        // 3. Store breakpoint references for cleanup
        logger.info("Setting exit breakpoint for function: $functionName")
        // TODO: Implement using IntelliJ's breakpoint API when method resolution is available
    }

    private fun getCurrentVariableValue(variableName: String): String? {
        return try {
            val frameId = "frame_0"
            val variables = debuggerService.getFrameVariables(frameId)
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

    private fun cleanupTemporaryBreakpoints() {
        try {
            val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager

            // Remove all controller-created breakpoints
            controllerBreakpoints.forEach { breakpoint ->
                try {
                    breakpointManager.removeBreakpoint(breakpoint)
                    logger.debug("Removed controller breakpoint at ${breakpoint.displayText}")
                } catch (e: Exception) {
                    logger.warn("Failed to remove controller breakpoint at ${breakpoint.displayText}", e)
                }
            }

            logger.info("Cleaned up ${controllerBreakpoints.size} controller breakpoints")
            controllerBreakpoints.clear()
        } catch (e: Exception) {
            logger.error("Failed to cleanup temporary breakpoints", e)
        }
    }

    /**
     * Disables all user-defined breakpoints to prevent interference with controller operations.
     * Stores the original enabled state so they can be restored later.
     */
    private fun disableAllUserBreakpoints() {
        try {
            val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
            val allBreakpoints = breakpointManager.allBreakpoints

            allBreakpoints.forEach { breakpoint ->
                if (!isControllerBreakpoint(breakpoint) && breakpoint.isEnabled) {
                    // Store the original enabled state
                    disabledUserBreakpoints[breakpoint] = breakpoint.isEnabled
                    // Disable the breakpoint
                    breakpoint.isEnabled = false
                    logger.debug("Disabled user breakpoint at ${breakpoint.displayText}")
                }
            }

            logger.info("Disabled ${disabledUserBreakpoints.size} user breakpoints")
        } catch (e: Exception) {
            logger.error("Failed to disable user breakpoints", e)
        }
    }

    /**
     * Restores the original enabled state of user breakpoints that were disabled by the controller.
     */
    private fun restoreUserBreakpoints() {
        try {
            disabledUserBreakpoints.forEach { (breakpoint, originalState) ->
                try {
                    breakpoint.isEnabled = originalState
                    logger.debug("Restored user breakpoint at ${breakpoint.displayText} to enabled=$originalState")
                } catch (e: Exception) {
                    logger.warn("Failed to restore user breakpoint at ${breakpoint.displayText}", e)
                }
            }

            logger.info("Restored ${disabledUserBreakpoints.size} user breakpoints")
            disabledUserBreakpoints.clear()
        } catch (e: Exception) {
            logger.error("Failed to restore user breakpoints", e)
        }
    }

    /**
     * Checks if a given XBreakpoint was set by the controller.
     *
     * This implementation uses a Set to track controller-created breakpoints.
     * When the controller creates a breakpoint, it should add it to the controllerBreakpoints set.
     */
    private fun isControllerBreakpoint(breakpoint: XBreakpoint<*>): Boolean {
        return controllerBreakpoints.contains(breakpoint)
    }

    /**
     * Basic condition evaluation as fallback when IntelliJ's evaluator is not available.
     * This is a simplified implementation for common cases.
     */
    private fun evaluateConditionBasic(condition: String): Boolean {
        return try {
            // Simple string-based evaluation for basic boolean conditions
            val trimmedCondition = condition.trim().lowercase()

            when {
                trimmedCondition == "true" -> true
                trimmedCondition == "false" -> false
                trimmedCondition.isEmpty() -> false
                else -> {
                    // Try to evaluate simple variable checks using debugger service
                    val frameId = "frame_0"
                    val variables = debuggerService.getFrameVariables(frameId)

                    // Handle simple variable name checks (e.g., "someVariable")
                    if (trimmedCondition.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                        val variable = variables[trimmedCondition]
                        return when (val value = variable?.value) {
                            is BasicValue -> value.value?.lowercase() == "true"
                            else -> value != null
                        }
                    }

                    // Default to false for unhandled conditions
                    logger.warn("Could not evaluate condition: $condition")
                    false
                }
            }
        } catch (e: Exception) {
            logger.warn("Basic condition evaluation failed for '$condition': ${e.message}")
            false
        }
    }

    private fun evaluateCondition(condition: String): Boolean {
        return try {
            // Try to use IntelliJ's expression evaluator first
            val debuggerManager = XDebuggerManager.getInstance(project)
            val currentSession = debuggerManager.currentSession as? XDebugSessionImpl

            if (currentSession != null) {
                val suspendContext = currentSession.suspendContext as? SuspendContextImpl
                val frameProxy = suspendContext?.frameProxy

                if (suspendContext != null && frameProxy != null) {
                    return evaluateConditionWithIntelliJ(suspendContext, frameProxy, condition)
                }
            }

            logger.debug("IntelliJ evaluator not available, falling back to basic evaluation")
            evaluateConditionBasic(condition)
        } catch (e: Exception) {
            logger.warn("Failed to evaluate condition: $condition", e)
            false
        }
    }

    /**
     * Evaluates a condition using IntelliJ's robust expression evaluator
     */
    private fun evaluateConditionWithIntelliJ(
        suspendContext: SuspendContextImpl,
        frameProxy: StackFrameProxyImpl,
        condition: String
    ): Boolean {
        return try {
            // Create an XExpression object from the condition string
            val xExpression = XDebuggerUtil.getInstance()
                .createExpression(condition, null, null, EvaluationMode.EXPRESSION)

            // Get the expression evaluator for the current frame
            val evaluator = getExpressionEvaluator(suspendContext, frameProxy)
                ?: return evaluateConditionBasic(condition)

            // Evaluate the expression synchronously using a simplified approach
            var result = false
            var completed = false

            val callback = object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(xValue: XValue) {
                    // Create a simplified presentation node to extract the value
                    val presentation = object : XValueNode {
                        override fun setPresentation(icon: javax.swing.Icon?, type: String?, value: String, hasChildren: Boolean) {
                            result = when (value.lowercase()) {
                                "true" -> true
                                "false" -> false
                                else -> value.isNotEmpty() && value != "null"
                            }
                            completed = true
                        }

                        override fun setPresentation(icon: javax.swing.Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
                            result = presentation.type?.lowercase() == "true"
                            completed = true
                        }

                        override fun setFullValueEvaluator(fullValueEvaluator: com.intellij.xdebugger.frame.XFullValueEvaluator) {
                            // Not needed for our use case
                        }
                    }

                    xValue.computePresentation(presentation, XValuePlace.TREE)
                }

                override fun errorOccurred(errorMessage: String) {
                    logger.warn("Expression evaluation error: $errorMessage")
                    completed = true
                }
            }

            evaluator.evaluate(xExpression, callback, null)

            // Wait for evaluation to complete (with timeout)
            var attempts = 0
            while (!completed && attempts < 100) {
                Thread.sleep(10)
                attempts++
            }

            if (completed) {
                result
            } else {
                logger.warn("Expression evaluation timed out for: $condition")
                evaluateConditionBasic(condition)
            }
        } catch (e: Exception) {
            logger.warn("IntelliJ expression evaluation failed for '$condition': ${e.message}")
            evaluateConditionBasic(condition)
        }
    }

    /**
     * Helper function to get an ExpressionEvaluator from the current debugging context
     */
    private fun getExpressionEvaluator(
        suspendContext: SuspendContextImpl,
        frameProxy: StackFrameProxyImpl
    ): XDebuggerEvaluator? {
        return try {
            val debugProcess: DebugProcessImpl = suspendContext.debugProcess
            val debuggerSession: DebuggerSession? = debugProcess.session
            val xDebugSession = debuggerSession?.xDebugSession as? XDebugSessionImpl

            if (xDebugSession != null) {
                // Create evaluation context
                EvaluationContextImpl(suspendContext, frameProxy)
                // Return null to fall back to basic evaluation since evaluator property access is complex
                null
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to get expression evaluator", e)
            null
        }
    }

    /**
     * Public method to inject debugger events (called by debugger integration layer)
     */
    fun notifyDebuggerEvent(event: DebuggerEvent) {
        eventChannel.trySend(event)
    }

    fun dispose() {
        controllerScope.cancel()
        eventChannel.close()
    }
}
