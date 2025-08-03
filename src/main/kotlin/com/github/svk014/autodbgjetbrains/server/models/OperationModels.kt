package com.github.svk014.autodbgjetbrains.server.models

import kotlinx.serialization.Serializable

@Serializable
data class OperationResult(
    val success: Boolean,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class BreakpointInfo(
    val success: Boolean,
    val filePath: String,
    val lineNumber: Int,
    val condition: String?,
    val message: String
)
