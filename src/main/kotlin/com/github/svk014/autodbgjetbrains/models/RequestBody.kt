package com.github.svk014.autodbgjetbrains.models

import kotlinx.serialization.Serializable

@Serializable
data class ParsedBreakpointRequest(
    val file: String,
    val line: Int,
    val lambdaOrdinal: Int?,
    val breakPointType: BreakpointType,
)
