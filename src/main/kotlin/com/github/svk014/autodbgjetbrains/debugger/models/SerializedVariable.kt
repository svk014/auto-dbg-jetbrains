package com.github.svk014.autodbgjetbrains.debugger.models

import kotlinx.serialization.Serializable

@Serializable
sealed interface LlmVariableValue

@Serializable
data class BasicValue(
    val objectType: String,
    val value: String?
) : LlmVariableValue

@Serializable
data class ObjectSummary(
    val objectType: String,
    val summary: String
) : LlmVariableValue

@Serializable
data class ObjectFields(
    val objectType: String,
    val fields: Map<String, LlmVariableValue>
) : LlmVariableValue

@Serializable
data class ArraySummary(
    val objectType: String,
    val size: Int,
    val firstElements: List<String>
) : LlmVariableValue

@Serializable
data class SerializedVariable(
    val name: String,
    val value: LlmVariableValue
)