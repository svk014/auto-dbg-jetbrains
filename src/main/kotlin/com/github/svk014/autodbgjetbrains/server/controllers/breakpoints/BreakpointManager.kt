package com.github.svk014.autodbgjetbrains.server.controllers.breakpoints

import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.displayText
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages breakpoints to ensure controller operations are not interrupted by user-defined breakpoints
 */
class BreakpointManager(private val project: Project) {
    private val logger = thisLogger()

    // Stores disabled user breakpoints and their original enabled state
    private val disabledUserBreakpoints = ConcurrentHashMap<XBreakpoint<*>, Boolean>()

    // Tracks breakpoints created by the controller
    private val controllerBreakpoints = ConcurrentHashMap.newKeySet<XBreakpoint<*>>()

    /**
     * Disables all user-defined breakpoints to prevent interference with controller operations.
     * Stores the original enabled state so they can be restored later.
     */
    fun disableAllUserBreakpoints() {
        try {
            val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
            val allBreakpoints = breakpointManager.allBreakpoints

            allBreakpoints.forEach { breakpoint ->
                if (!isControllerBreakpoint(breakpoint) && breakpoint.isEnabled) {
                    // Store the original enabled state
                    disabledUserBreakpoints[breakpoint] = breakpoint.isEnabled
                    // Disable the breakpoint
                    breakpoint.isEnabled = false
                    logger.debug("Disabled user breakpoint at ${breakpoint.displayText}")
                }
            }

            logger.info("Disabled ${disabledUserBreakpoints.size} user breakpoints")
        } catch (e: Exception) {
            logger.error("Failed to disable user breakpoints", e)
        }
    }

    /**
     * Restores the original enabled state of user breakpoints that were disabled by the controller.
     */
    fun restoreUserBreakpoints() {
        try {
            disabledUserBreakpoints.forEach { (breakpoint, originalState) ->
                try {
                    breakpoint.isEnabled = originalState
                    logger.debug("Restored user breakpoint at ${breakpoint.displayText} to enabled=$originalState")
                } catch (e: Exception) {
                    logger.warn("Failed to restore user breakpoint at ${breakpoint.displayText}", e)
                }
            }

            logger.info("Restored ${disabledUserBreakpoints.size} user breakpoints")
            disabledUserBreakpoints.clear()
        } catch (e: Exception) {
            logger.error("Failed to restore user breakpoints", e)
        }
    }

    /**
     * Checks if a given XBreakpoint was set by the controller.
     * Uses a Set to track controller-created breakpoints.
     */
    fun isControllerBreakpoint(breakpoint: XBreakpoint<*>): Boolean {
        return controllerBreakpoints.contains(breakpoint)
    }

    /**
     * Registers a breakpoint as being created by the controller
     */
    fun addControllerBreakpoint(breakpoint: XBreakpoint<*>) {
        controllerBreakpoints.add(breakpoint)
    }

    /**
     * Removes and cleans up all controller-created breakpoints
     */
    fun cleanupControllerBreakpoints() {
        try {
            val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager

            // Remove all controller-created breakpoints
            controllerBreakpoints.forEach { breakpoint ->
                try {
                    breakpointManager.removeBreakpoint(breakpoint)
                    logger.debug("Removed controller breakpoint at ${breakpoint.displayText}")
                } catch (e: Exception) {
                    logger.warn("Failed to remove controller breakpoint at ${breakpoint.displayText}", e)
                }
            }

            logger.info("Cleaned up ${controllerBreakpoints.size} controller breakpoints")
            controllerBreakpoints.clear()
        } catch (e: Exception) {
            logger.error("Failed to cleanup controller breakpoints", e)
        }
    }

    /**
     * Sets a breakpoint for a function by finding method declarations and setting line breakpoints
     * Uses the same pattern as JavaExecutionController for proper breakpoint handling
     */
    fun setBreakpointForFunction(functionName: String): List<XBreakpoint<*>> {
        val createdBreakpoints = mutableListOf<XBreakpoint<*>>()

        try {
            val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager

            // Use IntelliJ's search API to find method declarations
            val searchScope = GlobalSearchScope.projectScope(project)
            val methodSearcher = PsiSearchHelper.getInstance(project)

            // Find all method declarations with the given name
            val methods = mutableListOf<PsiMethod>()
            methodSearcher.processElementsWithWord({ element, _ ->
                if (element is PsiMethod && element.name == functionName) {
                    methods.add(element)
                }
                true
            }, searchScope, functionName, UsageSearchContext.IN_CODE, true)

            // Create breakpoints for each method found
            methods.forEach { method ->
                try {
                    val containingFile = method.containingFile
                    val virtualFile = containingFile?.virtualFile

                    if (virtualFile != null) {
                        // Get the line number for the method declaration
                        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                        val lineNumber = document?.getLineNumber(method.textOffset) ?: return@forEach

                        // Use proper breakpoint type resolution like JavaExecutionController
                        var selectedBreakpointType: XLineBreakpointType<*>? = null
                        ApplicationManager.getApplication().runReadAction {
                            val sourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, lineNumber)
                            if (sourcePosition != null) {
                                val availableBreakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(
                                    project,
                                    sourcePosition,
                                    null
                                )
                                // Prefer JavaLineBreakpointType
                                selectedBreakpointType = availableBreakpointTypes.find { it.javaClass == JavaLineBreakpointType::class.java }
                                    ?: availableBreakpointTypes.firstOrNull()
                            }
                        }

                        val finalBreakpointType = selectedBreakpointType ?: return@forEach

                        // Create breakpoint in write action
                        ApplicationManager.getApplication().runWriteAction {
                            val breakpoint = breakpointManager.addLineBreakpoint(
                                finalBreakpointType,
                                virtualFile.url,
                                lineNumber,
                                null
                            )

                            createdBreakpoints.add(breakpoint)
                            addControllerBreakpoint(breakpoint)
                            logger.debug("Set breakpoint for function '$functionName' at ${virtualFile.name}:${lineNumber + 1}")
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to set breakpoint for method ${method.name} in ${method.containingFile?.name}", e)
                }
            }

            logger.info("Set ${createdBreakpoints.size} breakpoints for function: $functionName")

        } catch (e: Exception) {
            logger.error("Failed to set breakpoints for function: $functionName", e)
        }

        return createdBreakpoints
    }

    /**
     * Sets breakpoints at function exit points (return statements)
     * Uses the same pattern as JavaExecutionController for proper breakpoint handling
     */
    fun setBreakpointForFunctionExit(functionName: String): List<XBreakpoint<*>> {
        val createdBreakpoints = mutableListOf<XBreakpoint<*>>()

        try {
            val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
            val searchScope = GlobalSearchScope.projectScope(project)
            val methodSearcher = PsiSearchHelper.getInstance(project)

            // Find all method declarations with the given name
            val methods = mutableListOf<PsiMethod>()
            methodSearcher.processElementsWithWord({ element, _ ->
                if (element is PsiMethod && element.name == functionName) {
                    methods.add(element)
                }
                true
            }, searchScope, functionName, UsageSearchContext.IN_CODE, true)

            // For each method, find all return statements and set breakpoints
            methods.forEach { method ->
                try {
                    val containingFile = method.containingFile
                    val virtualFile = containingFile?.virtualFile

                    if (virtualFile != null) {
                        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)

                        // Find all return statements in the method
                        val returnStatements = mutableListOf<PsiReturnStatement>()
                        method.accept(object : JavaRecursiveElementVisitor() {
                            override fun visitReturnStatement(statement: PsiReturnStatement) {
                                returnStatements.add(statement)
                                super.visitReturnStatement(statement)
                            }
                        })

                        // Helper function to create breakpoint at a specific line
                        fun createBreakpointAtLine(lineNumber: Int, description: String) {
                            var selectedBreakpointType: XLineBreakpointType<*>? = null
                            ApplicationManager.getApplication().runReadAction {
                                val sourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, lineNumber)
                                if (sourcePosition != null) {
                                    val availableBreakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(
                                        project,
                                        sourcePosition,
                                        null
                                    )
                                    selectedBreakpointType = availableBreakpointTypes.find { it.javaClass == JavaLineBreakpointType::class.java }
                                        ?: availableBreakpointTypes.firstOrNull()
                                }
                            }

                            val finalBreakpointType = selectedBreakpointType ?: return

                            ApplicationManager.getApplication().runWriteAction {
                                val breakpoint = breakpointManager.addLineBreakpoint(
                                    finalBreakpointType,
                                    virtualFile.url,
                                    lineNumber,
                                    null
                                )

                                createdBreakpoints.add(breakpoint)
                                addControllerBreakpoint(breakpoint)
                                logger.debug("Set $description breakpoint for function '$functionName' at ${virtualFile.name}:${lineNumber + 1}")
                            }
                        }

                        // Handle implicit return at the end of void methods
                        if (method.returnType?.equalsToText("void") == true || returnStatements.isEmpty()) {
                            val methodBody = method.body
                            if (methodBody != null && document != null) {
                                val lastBraceOffset = methodBody.textRange.endOffset - 1
                                val lineNumber = document.getLineNumber(lastBraceOffset)
                                createBreakpointAtLine(lineNumber, "exit")
                            }
                        }

                        // Set breakpoints at each return statement
                        returnStatements.forEach { returnStatement ->
                            if (document != null) {
                                val lineNumber = document.getLineNumber(returnStatement.textOffset)
                                createBreakpointAtLine(lineNumber, "return")
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to set exit breakpoints for method ${method.name} in ${method.containingFile?.name}", e)
                }
            }

            logger.info("Set ${createdBreakpoints.size} exit breakpoints for function: $functionName")

        } catch (e: Exception) {
            logger.error("Failed to set exit breakpoints for function: $functionName", e)
        }

        return createdBreakpoints
    }
}
