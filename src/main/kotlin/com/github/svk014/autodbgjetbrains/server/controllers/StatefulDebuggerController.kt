package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.debugger.models.LlmVariableValue
import com.github.svk014.autodbgjetbrains.models.ApiResponse
import com.github.svk014.autodbgjetbrains.models.BreakpointType
import com.github.svk014.autodbgjetbrains.models.SourceLine
import com.github.svk014.autodbgjetbrains.server.controllers.breakpoints.BreakpointManager
import com.github.svk014.autodbgjetbrains.server.controllers.evaluation.ExpressionEvaluator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume

@Serializable
data class TraceHit(
    val hitNumber: Int,
    val location: String,
    val frameIndex: Int,
    val expressionResult: LlmVariableValue?,
)

@Serializable
data class TraceBreakpointResult(
    val hits: Int,
    val traces: List<TraceHit>,
    val breakpointRemoved: Boolean
)

@Service(Service.Level.PROJECT)
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

    /**
     * Perform a blocking trace by setting a breakpoint at file:line, disabling user breakpoints,
     * and collecting evaluations of the provided expression each time the breakpoint is hit.
     * Uses polling to detect pauses. Cleans up breakpoint and restores user breakpoints afterward.
     */
    suspend fun traceBreakpoint(
        file: String,
        line: Int,
        breakpointType: BreakpointType = BreakpointType.LINE,
        lambdaOrdinal: Int? = null,
        expression: String,
        maxHits: Int = 10,
        timeoutMs: Long = 30_000L
    ): ApiResponse {
        val bpManager = BreakpointManager(project)
        bpManager.disableAllUserBreakpoints()

        try {
            val setOk = debuggerService.setBreakpoint(file, SourceLine(line), null, breakpointType, lambdaOrdinal)
            if (!setOk) {
                return ApiResponse.error("Failed to set breakpoint at $file:$line")
            }

            val traces = mutableListOf<TraceHit>()
            var hits = 0

            while (hits < maxHits) {
                val paused = withTimeoutOrNull(timeoutMs) {
                    suspendUntilDebuggerPaused()
                    true
                }
                if (paused == null) {
                    logger.warn("Timed out waiting for debugger to pause at breakpoint $file:$line")
                    break
                }
                // After suspension, validate if paused at correct location
                val topFrame = try {
                    debuggerService.getFrameAt(0)
                } catch (_: Exception) {
                    null
                }
                val topFile = topFrame?.filePath
                val topLine = topFrame?.lineNumber?.oneBasedNumber
                if (topFile != file || topLine != line) {
                    // Not the intended breakpoint, resume and continue
                    debuggerService.continueExecution()
                    continue
                }

                // Collect trace data here
                hits++
                val result = expressionEvaluator.evaluateExpression("i", 0)
                if (result != null) {
                    traces.add(
                        TraceHit(
                            hitNumber = hits,
                            location = "$file:$line",
                            frameIndex = 0,
                            expressionResult = result,
                        )
                    )
                }

                debuggerService.continueExecution()
            }

            // Cleanup breakpoint
            val removed = debuggerService.removeBreakpoint(file, SourceLine(line), null, breakpointType, lambdaOrdinal)

            return ApiResponse.success(
                TraceBreakpointResult(
                    hits = hits,
                    traces = traces,
                    breakpointRemoved = removed
                )
            )
        } catch (e: Exception) {
            logger.error("traceBreakpoint failed: ${e.message}", e)
            return ApiResponse.error("trace_breakpoint failed: ${e.message}")
        } finally {
            try {
                bpManager.restoreUserBreakpoints()
            } catch (e: Exception) {
                logger.warn("Failed to restore user breakpoints: ${e.message}")
            }
        }
    }

    /**
     * Suspends until the debugger session is paused (e.g., after hitting a breakpoint or step).
     * Uses XDebugSessionListener to resume the coroutine when a pause event occurs.
     */
    private suspend fun suspendUntilDebuggerPaused() {
        val session = debuggerService.getCurrentSession() ?: throw IllegalStateException("No active debug session")
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val listener = object : com.intellij.xdebugger.XDebugSessionListener {
                override fun sessionPaused() {
                    session.removeSessionListener(this)
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun sessionStopped() {
                    session.removeSessionListener(this)
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
            session.addSessionListener(listener)
            // If already paused, resume immediately
            if (session.isPaused) {
                session.removeSessionListener(listener)
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            continuation.invokeOnCancellation {
                session.removeSessionListener(listener)
            }
        }
    }
}
