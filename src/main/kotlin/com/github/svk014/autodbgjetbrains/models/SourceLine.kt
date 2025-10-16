package com.github.svk014.autodbgjetbrains.models

import kotlinx.serialization.Serializable

@Serializable
enum class LineNumbering {
    ZERO_BASED,
    ONE_BASED,
}

@Serializable
data class SourceLine(
    private val number: Int,
    val numbering: LineNumbering = LineNumbering.ONE_BASED,
) {

    val zeroBasedNumber: Int
        get() {
            return when (numbering) {
                LineNumbering.ZERO_BASED -> number
                LineNumbering.ONE_BASED -> number - 1
            }
        }

    val oneBasedNumber: Int
        get() {
            return when (numbering) {
                LineNumbering.ZERO_BASED -> number + 1
                LineNumbering.ONE_BASED -> number
            }
        }

    companion object {
        fun zeroToOneBased(i: Int?): SourceLine? {
            if (i == null) {
                return null
            }
            return SourceLine(i + 1, LineNumbering.ONE_BASED)
        }

        fun oneToZeroBased(i: Int?): SourceLine? {
            if (i == null) {
                return null
            }
            return SourceLine(i - 1, LineNumbering.ZERO_BASED)
        }
    }
}
