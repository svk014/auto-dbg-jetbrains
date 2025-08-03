package com.github.svk014.autodbgjetbrains.debugger.models

data class VariableModifiers(
    val accessModifiers: List<String>,
    val isStatic: Boolean,
    val isFinal: Boolean
)
