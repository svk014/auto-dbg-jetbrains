package com.github.svk014.autodbgjetbrains.debugger.models

import kotlinx.serialization.Serializable

/**
 * Represents a variable with its value and type information
 */
@Serializable
data class Variable(
    val name: String,
    val value: String,
    val type: String,
    val children: Map<String, Variable> = emptyMap()
)
