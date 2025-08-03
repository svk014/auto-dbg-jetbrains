package com.github.svk014.autodbgjetbrains.debugger.models

import kotlinx.serialization.Serializable

@Serializable
data class TypeInfo(
    val rawType: String,
    val genericSignature: String? = null,
    val isInterface: Boolean = false,
    val isEnum: Boolean = false,
    val isLambda: Boolean = false
)