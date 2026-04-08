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

package com.truthlocks.maip.jetbrains.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.truthlocks.maip.jetbrains.notifications.MAIPNotifier
import com.truthlocks.maip.jetbrains.services.MAIPProjectService
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Action that opens a dialog to register a new machine agent
 * with the MAIP API.
 */
class RegisterAgentAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        if (!MAIPSettingsState.getInstance().isConfigured()) {
            Messages.showErrorDialog(
                project,
                "MAIP is not configured. Please set your API URL, key, and tenant ID in Settings > Tools > MAIP.",
                "MAIP Not Configured"
            )
            return
        }

        val dialog = RegisterAgentDialog()
        if (!dialog.showAndGet()) return

        val name = dialog.agentName.trim()
        val capabilities = dialog.capabilities
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val parentId = dialog.parentId.trim().ifEmpty { null }

        if (name.isBlank()) {
            Messages.showErrorDialog(project, "Agent name is required.", "Validation Error")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val client = MAIPProjectService.getInstance(project).getClient()
            if (client == null) {
                MAIPNotifier.error(project, "MAIP", "API client not available.")
                return@launch
            }

            val result = client.registerAgent(name, capabilities, parentId)
            if (result != null) {
                val agentId = result.get("id")?.asString ?: "unknown"
                MAIPNotifier.info(project, "Agent Registered", "Agent '$name' registered with ID: $agentId")
                MAIPProjectService.getInstance(project).refreshAgents()
            } else {
                MAIPNotifier.error(project, "MAIP", "Failed to register agent '$name'")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * Dialog for capturing new agent registration details.
     */
    private class RegisterAgentDialog : DialogWrapper(true) {

        private val nameField = JBTextField(30)
        private val capabilitiesField = JBTextField(30)
        private val parentIdField = JBTextField(30)

        val agentName: String get() = nameField.text
        val capabilities: String get() = capabilitiesField.text
        val parentId: String get() = parentIdField.text

        init {
            title = "Register New Agent"
            init()
        }

        override fun createCenterPanel(): JComponent {
            return FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Agent Name:"), nameField, 1, false)
                .addLabeledComponent(
                    JBLabel("Capabilities (comma-separated):"),
                    capabilitiesField,
                    1,
                    false
                )
                .addLabeledComponent(JBLabel("Parent Agent ID (optional):"), parentIdField, 1, false)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }

        override fun getPreferredFocusedComponent(): JComponent = nameField
    }
}
