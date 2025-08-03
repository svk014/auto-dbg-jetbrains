package com.github.svk014.autodbgjetbrains.debugger.java

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.xdebugger.frame.presentation.XValuePresentation

class ValueTextRenderer(private val valueBuilder: StringBuilder) : XValuePresentation.XValueTextRenderer {
    override fun renderValue(value: String) {
        valueBuilder.append(value)
    }

    override fun renderValue(value: String, attributes: TextAttributesKey) {
        valueBuilder.append(value)
    }

    override fun renderStringValue(value: String) {
        valueBuilder.append(value)
    }

    override fun renderStringValue(
        value: String,
        prefix: String?,
        maxLength: Int
    ) {
        valueBuilder.append(value)
    }

    override fun renderNumericValue(value: String) {
        valueBuilder.append(value)
    }

    override fun renderKeywordValue(value: String) {
        valueBuilder.append(value)
    }

    override fun renderComment(value: String) {
        valueBuilder.append(value)
    }

    override fun renderSpecialSymbol(value: String) {
        valueBuilder.append(value)
    }

    override fun renderError(value: String) {
        valueBuilder.append(value)
    }
}

