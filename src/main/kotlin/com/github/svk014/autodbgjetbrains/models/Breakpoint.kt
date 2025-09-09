package com.github.svk014.autodbgjetbrains.models

import kotlinx.serialization.Serializable

enum class BreakpointType {
    LINE, METHOD
}

@Serializable
data class SerializableBreakpoint(
    val lambdaOrdinal: Int?,
    val file: String,
    val line: Int,
    val breakpointType: String?,
    val expression: String?,
)