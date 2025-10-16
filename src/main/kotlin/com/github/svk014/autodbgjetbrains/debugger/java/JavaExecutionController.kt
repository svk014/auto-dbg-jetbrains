package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.ExecutionController
import com.github.svk014.autodbgjetbrains.models.BreakpointType
import com.github.svk014.autodbgjetbrains.models.SerializableBreakpoint
import com.github.svk014.autodbgjetbrains.models.SourceLine
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties

class JavaExecutionController(private val project: Project) : ExecutionController {

    override fun stepOver() {
        XDebuggerManager.getInstance(project).currentSession?.stepOver(false)
    }

    override fun stepInto() {
        XDebuggerManager.getInstance(project).currentSession?.stepInto()
    }

    override fun stepOut() {
        XDebuggerManager.getInstance(project).currentSession?.stepOut()
    }

    override fun continueExecution() {
        XDebuggerManager.getInstance(project).currentSession?.resume()
    }

    override fun getAllBreakpoints(): List<SerializableBreakpoint> {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager

        val breakpoints = breakpointManager.allBreakpoints.map { bp ->
            val file = bp.sourcePosition?.file?.path
            val line = bp.sourcePosition?.line
            if (file == null || line == null) {
                return@map null
            }
            val props = bp.properties
            val lambdaOrdinal = if (props is JavaLineBreakpointProperties) props.lambdaOrdinal else null
            val expression = bp.conditionExpression?.expression
            val breakpointType = when {
                (bp.type is JavaLineBreakpointType) -> {
                    BreakpointType.LINE
                }

                (bp.type is JavaMethodBreakpointType) -> {
                    BreakpointType.METHOD
                }

                else -> null
            }
            return@map SerializableBreakpoint(lambdaOrdinal, file, line, breakpointType?.name, expression)
        }
        return breakpoints.filterNotNull()
    }

    override fun setBreakpoint(
        file: String, line: SourceLine, condition: String?, type: BreakpointType?, lambdaOrdinal: Int?,
    ): Boolean {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val virtualFile =
            LocalFileSystem.getInstance().findFileByPath(file) ?: throw Exception("Can't find virtual file")

        var selectedBreakpointType: XLineBreakpointType<*>? = null
        ApplicationManager.getApplication().runReadAction {
            val sourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, line.zeroBasedNumber)
            if (sourcePosition != null) {
                val availableBreakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(
                    project, sourcePosition, null
                )

                selectedBreakpointType = breakpointType(type, availableBreakpointTypes)
            }
        }
        val finalBreakpointType =
            (selectedBreakpointType as XLineBreakpointType<in Any>?) ?: throw Exception("Unable to set breakpoint")


        val properties = finalBreakpointType.createBreakpointProperties(virtualFile, line.zeroBasedNumber)

        if (lambdaOrdinal != null && properties is JavaLineBreakpointProperties) {
            properties.encodedInlinePosition = lambdaOrdinal
        }

        ApplicationManager.getApplication().runWriteAction {
            val breakpoint = breakpointManager.addLineBreakpoint(
                finalBreakpointType, virtualFile.url, line.zeroBasedNumber, properties
            )

            if (condition != null) {
                breakpoint.setCondition(condition)
            }
        }
        return true

    }


    override fun removeBreakpoint(
        file: String, line: SourceLine, condition: String?, type: BreakpointType?, lambdaOrdinal: Int?
    ): Boolean {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(file)

        val breakpointToRemove = breakpointManager.allBreakpoints.find { bp ->
            val props = bp.properties
            if (props is JavaLineBreakpointProperties && props.lambdaOrdinal != lambdaOrdinal) return@find false
            return@find bp.sourcePosition?.file == virtualFile && bp.sourcePosition?.line == line.zeroBasedNumber && bp.conditionExpression?.expression == condition
        }
        if (breakpointToRemove != null) {
            ApplicationManager.getApplication().runWriteAction {
                breakpointManager.removeBreakpoint(breakpointToRemove)
            }
            return true
        }

        return false
    }

    private fun breakpointType(
        type: BreakpointType?, availableBreakpointTypes: List<XLineBreakpointType<*>>
    ): XLineBreakpointType<out XBreakpointProperties<in Any>>? = when (type) {
        BreakpointType.METHOD -> {
            availableBreakpointTypes.find { it is JavaMethodBreakpointType }
        }

        BreakpointType.LINE -> {
            // Find a breakpoint of the exact JavaLineBreakpointType class, not a subclass.
            availableBreakpointTypes.find { it is JavaLineBreakpointType }
        }

        else -> {
            // Default behavior: prefer a standard line breakpoint, but take any if that's not available.
            availableBreakpointTypes.find { it is JavaLineBreakpointType }
                ?: availableBreakpointTypes.firstOrNull()
        }
    }
}
