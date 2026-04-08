/*
 * Copyright 2026 Truthlocks Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.truthlocks.maip.jetbrains.toolwindow

import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.truthlocks.maip.jetbrains.icons.MAIPIcons
import com.truthlocks.maip.jetbrains.notifications.MAIPNotifier
import com.truthlocks.maip.jetbrains.services.MAIPProjectService
import com.truthlocks.maip.jetbrains.services.TrustService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.*

/**
 * Agent list panel for the MAIP tool window.
 *
 * Displays agents grouped by status with trust score color indicators.
 * Supports actions: suspend, revoke, and refresh.
 */
class AgentPanel(private val project: Project) : Disposable {

    private val listModel = DefaultListModel<JsonObject>()
    private val agentList = JBList(listModel)
    private val mainPanel = JPanel(BorderLayout())

    /** The root Swing component for embedding in the tool window. */
    val component: JComponent get() = mainPanel

    private val listener = object : MAIPProjectService.DataChangeListener {
        override fun onAgentsChanged(agents: List<JsonObject>) {
            ApplicationManager.getApplication().invokeLater {
                updateList(agents)
            }
        }
    }

    init {
        setupUI()
        MAIPProjectService.getInstance(project).addListener(listener)

        val cached = MAIPProjectService.getInstance(project).agents
        if (cached.isNotEmpty()) {
            updateList(cached)
        }
    }

    private fun setupUI() {
        agentList.cellRenderer = AgentCellRenderer()
        agentList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val toolbar = ToolbarDecorator.createDecorator(agentList)
            .disableAddAction()
            .disableRemoveAction()
            .addExtraAction(object : ToolbarDecorator.ElementActionButton("Refresh", MAIPIcons.REFRESH) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    MAIPProjectService.getInstance(project).refreshAgents()
                }

                override fun isEnabled(): Boolean = true
            })
            .addExtraAction(object : ToolbarDecorator.ElementActionButton("Suspend", MAIPIcons.AGENT_SUSPENDED) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    suspendSelectedAgent()
                }

                override fun isEnabled(): Boolean = agentList.selectedValue != null
            })
            .addExtraAction(object : ToolbarDecorator.ElementActionButton("Revoke", MAIPIcons.AGENT_REVOKED) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    revokeSelectedAgent()
                }

                override fun isEnabled(): Boolean = agentList.selectedValue != null
            })
            .createPanel()

        mainPanel.add(toolbar, BorderLayout.CENTER)
    }

    private fun suspendSelectedAgent() {
        val agent = agentList.selectedValue ?: return
        val agentId = agent.get("id")?.asString ?: return
        val name = agent.get("name")?.asString ?: agentId

        val reason = Messages.showInputDialog(
            project,
            "Reason for suspending agent '$name':",
            "Suspend Agent",
            null
        ) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val client = MAIPProjectService.getInstance(project).getClient()
            if (client == null) {
                MAIPNotifier.error(project, "MAIP", "API client not available.")
                return@launch
            }

            val result = client.suspendAgent(agentId, reason)
            if (result != null) {
                MAIPNotifier.info(project, "Agent Suspended", "Agent '$name' has been suspended.")
                MAIPProjectService.getInstance(project).refreshAgents()
            } else {
                MAIPNotifier.error(project, "MAIP", "Failed to suspend agent '$name'.")
            }
        }
    }

    private fun revokeSelectedAgent() {
        val agent = agentList.selectedValue ?: return
        val agentId = agent.get("id")?.asString ?: return
        val name = agent.get("name")?.asString ?: agentId

        val confirm = Messages.showYesNoDialog(
            project,
            "Are you sure you want to permanently revoke agent '$name'? This action cannot be undone.",
            "Revoke Agent",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) return

        val reason = Messages.showInputDialog(
            project,
            "Reason for revoking agent '$name':",
            "Revoke Agent",
            null
        ) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val client = MAIPProjectService.getInstance(project).getClient()
            if (client == null) {
                MAIPNotifier.error(project, "MAIP", "API client not available.")
                return@launch
            }

            val result = client.revokeAgent(agentId, reason)
            if (result != null) {
                MAIPNotifier.warn(project, "Agent Revoked", "Agent '$name' has been permanently revoked.")
                MAIPProjectService.getInstance(project).refreshAgents()
            } else {
                MAIPNotifier.error(project, "MAIP", "Failed to revoke agent '$name'.")
            }
        }
    }

    private fun updateList(agents: List<JsonObject>) {
        listModel.clear()
        val sorted = agents.sortedBy { it.get("status")?.asString ?: "z" }
        sorted.forEach { listModel.addElement(it) }
    }

    override fun dispose() {
        MAIPProjectService.getInstance(project).removeListener(listener)
    }

    /**
     * Custom cell renderer for agent list items showing name, status, and trust score.
     */
    private class AgentCellRenderer : ColoredListCellRenderer<JsonObject>() {
        override fun customizeCellRenderer(
            list: JList<out JsonObject>,
            value: JsonObject?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            if (value == null) return

            val name = value.get("name")?.asString ?: "Unknown"
            val status = value.get("status")?.asString ?: "unknown"
            val agentId = value.get("id")?.asString?.take(8) ?: ""
            val trustScore = value.get("trust_score")?.asDouble

            icon = MAIPIcons.agentStatusIcon(status)

            append(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            append("  [$status]", when (status.lowercase()) {
                "active" -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, TrustService.HIGH_TRUST_COLOR)
                "suspended" -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, TrustService.MEDIUM_TRUST_COLOR)
                "revoked" -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, TrustService.LOW_TRUST_COLOR)
                else -> SimpleTextAttributes.GRAYED_ATTRIBUTES
            })

            if (trustScore != null) {
                val color = TrustService.trustColor(trustScore)
                append("  Trust: ${TrustService.formatScore(trustScore)}", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
            }

            if (agentId.isNotEmpty()) {
                append("  #$agentId", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}
