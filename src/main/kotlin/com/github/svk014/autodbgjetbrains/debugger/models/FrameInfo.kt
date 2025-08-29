package com.github.svk014.autodbgjetbrains.debugger.models

import com.github.svk014.autodbgjetbrains.models.SourceLine
import kotlinx.serialization.Serializable

/**
 * Represents a stack frame in the debugger.
 */
@Serializable
data class FrameInfo(
    val methodName: String,
    val lineNumber: SourceLine?,
    val filePath: String
)
