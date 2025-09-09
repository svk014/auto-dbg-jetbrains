package com.github.svk014.autodbgjetbrains.server.controllers.operations

import com.github.svk014.autodbgjetbrains.models.OperationResult
import com.github.svk014.autodbgjetbrains.server.models.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages debugging operations lifecycle and state
 */
class OperationManager {
    private val stateMutex = Mutex()
    private var currentState: DebuggerState = DebuggerState.IDLE
    private var currentOperation: Operation? = null
    private val operationResults = ConcurrentHashMap<String, OperationResult>()
    private val operationIdGenerator = AtomicLong(0)

    suspend fun getCurrentState(): DebuggerState = stateMutex.withLock { currentState }

    suspend fun getCurrentOperation(): Operation? = stateMutex.withLock { currentOperation }

    suspend fun isIdle(): Boolean = stateMutex.withLock { currentState == DebuggerState.IDLE }

    suspend fun startOperation(operation: Operation, newState: DebuggerState): Boolean {
        return stateMutex.withLock {
            if (currentState != DebuggerState.IDLE) {
                false
            } else {
                currentOperation = operation
                currentState = newState

                // Store initial result
                operationResults[operation.id] = OperationResult(
                    operationId = operation.id,
                    status = "in_progress",
                    data = null,
                    message = "Started operation ${operation.id}"
                )
                true
            }
        }
    }

    suspend fun completeOperation(result: OperationResult) {
        stateMutex.withLock {
            currentOperation = null
            currentState = DebuggerState.IDLE
            operationResults[result.operationId] = result.copy(completedAt = System.currentTimeMillis())
        }
    }

    suspend fun updateState(newState: DebuggerState) {
        stateMutex.withLock {
            currentState = newState
        }
    }

    fun generateOperationId(prefix: String): String {
        return "${prefix}_${operationIdGenerator.incrementAndGet()}"
    }

    fun getOperationResult(operationId: String): OperationResult? {
        return operationResults[operationId]
    }

    fun storeOperationResult(operationId: String, result: OperationResult) {
        operationResults[operationId] = result
    }
}
