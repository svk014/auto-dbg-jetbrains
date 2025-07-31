package com.github.svk014.autodbgjetbrains.debugger.models

import kotlinx.serialization.Serializable

/**
 * Represents a stack frame in the debugger.
 */
@Serializable
data class FrameInfo(
    val methodName: String,
    val lineNumber: Int,
    val filePath: String
)
