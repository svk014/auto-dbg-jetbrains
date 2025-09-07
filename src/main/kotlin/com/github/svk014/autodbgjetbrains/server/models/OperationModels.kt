package com.github.svk014.autodbgjetbrains.server.models

import kotlinx.serialization.Serializable

/**
 * Operation result data types for stateful debugger operations
 * Following the same sealed interface pattern as LlmVariableValue for type-safe serialization
 */
@Serializable
sealed interface OperationResultData

@Serializable
data class TracingResultData(
    val functionName: String,
    val totalCalls: Int,
    val traces: List<FunctionTraceData>
) : OperationResultData

@Serializable
data class ValueChangeResultData(
    val variableName: String,
    val totalSteps: Int,
    val changeHistory: List<ValueSnapshotData>
) : OperationResultData

@Serializable
data class SteppingResultData(
    val condition: String,
    val totalSteps: Int,
    val conditionMet: Boolean,
    val finalLocation: String
) : OperationResultData

@Serializable
data class ErrorResultData(
    val errorType: String,
    val message: String,
    val details: String? = null
) : OperationResultData

@Serializable
data class FunctionTraceData(
    val callNumber: Int,
    val functionName: String,
    val entryLocation: String,
    val arguments: Map<String, String>,
    val returnValue: String? = null,
    val exitLocation: String? = null,
    val timestamp: Long
)

@Serializable
data class ValueSnapshotData(
    val stepNumber: Int,
    val location: String,
    val variableName: String,
    val value: String,
    val timestamp: Long
)

@Serializable
data class OperationResult(
    val operationId: String,
    val status: String, // "completed", "failed", "in_progress"
    val data: OperationResultData?,
    val message: String? = null,
    val completedAt: Long? = null
)
