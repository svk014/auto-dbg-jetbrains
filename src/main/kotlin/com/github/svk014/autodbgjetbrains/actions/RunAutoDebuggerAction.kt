package com.github.svk014.autodbgjetbrains.actions

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.server.DebuggerApiServer
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import javax.swing.Icon
import com.intellij.openapi.application.ApplicationManager

class RunAutoDebuggerAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    fun getDebuggerService(project: Project): DebuggerIntegrationService {
        return project.service<DebuggerIntegrationService>()
    }

    fun getApiServer(project: Project): DebuggerApiServer {
        return project.service<DebuggerApiServer>()
    }

    fun waitForDebugSessionAsync(
        project: Project, config: RunConfiguration, timeoutMs: Long = 10000, onSessionReady: (XDebugSession) -> Unit
    ) {
        Thread {
            val startTime = System.currentTimeMillis()
            var session: XDebugSession? = null

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                session = XDebuggerManager.getInstance(project).debugSessions.find { it.runProfile == config }

                if (session != null) break

                Thread.sleep(50)
            }

            session?.let {
                // Switch to UI thread to interact with the IDE
                ApplicationManager.getApplication().invokeLater {
                    onSessionReady(it)
                }
            }
        }.start()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val apiServer = getApiServer(project)
        if (!apiServer.isRunning()) {
            apiServer.startServer()
        }

        val debuggerService = getDebuggerService(project)
        // Check if a debugger session is already running
        val debugSessions = debuggerService.getDebugSessions()
        if (debugSessions.isNotEmpty()) {
            // Stop all running debug sessions
            debugSessions.forEach { it.stop() }
            return
        }

        val dataContext = e.dataContext
        val context = ConfigurationContext.getFromContext(dataContext, "unknown")

        val createdFromContext = RunConfigurationProducer.getProducers(project).asSequence()
            .mapNotNull { it.createConfigurationFromContext(context) }.firstOrNull()

        if (createdFromContext == null) {
            println("No run configuration could be created from the current context.")
            return
        }

        val configuration = createdFromContext.configurationSettings.configuration
        val executor = DefaultDebugExecutor.getDebugExecutorInstance() ?: return
        val runner = ProgramRunner.getRunner(executor.id, configuration)

        if (runner == null) {
            println("Cannot find a runner for ${configuration.name} with the Debug executor.")
            return
        }

        val environment = ExecutionEnvironmentBuilder.createOrNull(project, executor, configuration)?.build() ?: return


        try {
            runner.execute(environment)
            waitForDebugSessionAsync(project, configuration, 10000, onSessionReady = { session ->
                debuggerService.connectToSession(session.sessionName)
            })
        } catch (ex: ExecutionException) {
            ex.printStackTrace()
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation

        presentation.isVisible = true

        if (project == null) {
            presentation.isEnabled = false
            return
        }

        val dataContext = e.dataContext
        val context = ConfigurationContext.getFromContext(dataContext, "unknown")
        val createdFromContext = RunConfigurationProducer.getProducers(project).asSequence()
            .mapNotNull { it.createConfigurationFromContext(context) }.firstOrNull()

        val isActionEnabled = createdFromContext != null
        presentation.isEnabled = isActionEnabled

        if (!isActionEnabled) {
            presentation.icon = ICON_DEBUG
            return
        }

        val isRunning = getDebuggerService(project).getDebugSessions().isNotEmpty()
        presentation.icon = if (isRunning) ICON_STOP else ICON_DEBUG
    }
}

val ICON_DEBUG: Icon = IconLoader.getIcon("/icons/run.svg", RunAutoDebuggerAction::class.java)
val ICON_STOP: Icon = IconLoader.getIcon("/icons/stop.svg", RunAutoDebuggerAction::class.java)