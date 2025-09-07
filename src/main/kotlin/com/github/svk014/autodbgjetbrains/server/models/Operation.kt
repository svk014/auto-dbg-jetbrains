package com.github.svk014.autodbgjetbrains.server.models

import kotlinx.serialization.Serializable

@Serializable
sealed class Operation {
    abstract val id: String
    abstract val requestedAt: Long

    @Serializable
    data class TraceFunctionCalls(
        override val id: String,
        override val requestedAt: Long,
        val functionName: String,
        val maxCalls: Int,
        val captureArgs: Boolean = true,
        val captureReturn: Boolean = true,
        val currentCallCount: Int = 0,
        val collectedTraces: MutableList<FunctionTraceData> = mutableListOf()
    ) : Operation()

    @Serializable
    data class FindValueChange(
        override val id: String,
        override val requestedAt: Long,
        val variableName: String,
        val expectedValue: String? = null,
        val maxSteps: Int = 100,
        var currentSteps: Int = 0,
        val valueHistory: MutableList<ValueSnapshotData> = mutableListOf()
    ) : Operation()

    @Serializable
    data class StepUntilCondition(
        override val id: String,
        override val requestedAt: Long,
        val condition: String,
        val maxSteps: Int = 50,
        var currentSteps: Int = 0
    ) : Operation()
}
