package com.github.svk014.autodbgjetbrains.debugger.models

import com.github.svk014.autodbgjetbrains.debugger.models.TypeInfo
import kotlinx.serialization.Serializable

/**
 * Represents a variable with its value and type information
 */
@Serializable
data class Variable(
    val name: String,
    val value: String,
    val type: String,
    val fullyQualifiedType: String = type,
    val genericType: String? = null,
    val isNull: Boolean = false,
    val hasChildren: Boolean = false,
    val objectId: String? = null,
    val modifiers: List<String> = emptyList(),
    val isStatic: Boolean = false,
    val isFinal: Boolean = false,
    val isEnum: Boolean = false,
    val isInterface: Boolean = false,
    val isLambda: Boolean = false,
    val children: Map<String, Variable> = emptyMap(),
    val arraySize: Int? = null,
    val collectionSize: Int? = null,
    val isError: Boolean = false,
    val typeInfo: TypeInfo? = null
)
