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

package com.truthlocks.maip.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.truthlocks.maip.jetbrains.services.RegistrationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings UI panel for the MAIP plugin, accessible via
 * Settings > Tools > MAIP.
 *
 * Allows users to configure the API endpoint, credentials, default
 * agent ID, and auto-receipt preferences.
 */
class MAIPSettingsConfigurable : Configurable {

    private var panel: JPanel? = null

    private val apiUrlField = JBTextField(40)
    private val apiKeyField = JBPasswordField()
    private val tenantIdField = JBTextField(30)
    private val agentIdField = JBTextField(30)
    private val algorithmCombo = ComboBox(DefaultComboBoxModel(MAIPSettingsState.SUPPORTED_ALGORITHMS.toTypedArray()))
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(30_000L, 5_000L, 120_000L, 1_000L))
    private val maxRetriesSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))
    private val autoCommitCheckbox = JBCheckBox("Auto-receipt on VCS commit")
    private val autoSaveCheckbox = JBCheckBox("Auto-receipt on file save")
    private val autoBuildCheckbox = JBCheckBox("Auto-receipt on build completion")
    private val registerButton = JButton("Register")

    override fun getDisplayName(): String = "MAIP"

    override fun createComponent(): JComponent {
        apiKeyField.columns = 40

        registerButton.toolTipText = "Register for a free API key using your email"
        registerButton.addActionListener { showRegistrationDialog() }

        val apiKeyPanel = JPanel(BorderLayout(8, 0)).apply {
            add(apiKeyField, BorderLayout.CENTER)
            add(registerButton, BorderLayout.EAST)
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API URL:"), apiUrlField, 1, false)
            .addLabeledComponent(JBLabel("API Key:"), apiKeyPanel, 1, false)
            .addLabeledComponent(JBLabel("Tenant ID (optional reference):"), tenantIdField, 1, false)
            .addLabeledComponent(JBLabel("Default Agent ID:"), agentIdField, 1, false)
            .addLabeledComponent(JBLabel("Requested algorithm (metadata only):"), algorithmCombo, 1, false)
            .addSeparator()
            .addLabeledComponent(JBLabel("Timeout (ms):"), timeoutSpinner, 1, false)
            .addLabeledComponent(JBLabel("Max Retries:"), maxRetriesSpinner, 1, false)
            .addSeparator()
            .addComponent(autoCommitCheckbox, 1)
            .addComponent(autoSaveCheckbox, 1)
            .addComponent(autoBuildCheckbox, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel!!
    }

    /**
     * Opens a dialog prompting for email, then registers the developer
     * and stores the returned API key in PasswordSafe.
     */
    private fun showRegistrationDialog() {
        val dialog = RegistrationDialog()
        if (dialog.showAndGet()) {
            val email = dialog.getEmail()
            if (email.isBlank()) {
                Messages.showWarningDialog(
                    "Please enter a valid email address.",
                    "Registration"
                )
                return
            }

            registerButton.isEnabled = false
            registerButton.text = "Registering..."

            CoroutineScope(Dispatchers.IO).launch {
                val service = RegistrationService.getInstance()
                val result = service.register(email)

                ApplicationManager.getApplication().invokeLater {
                    registerButton.isEnabled = true
                    registerButton.text = "Register"

                    when (result) {
                        is RegistrationService.RegistrationResult.Success -> {
                            service.storeApiKey(result.apiKey)
                            apiKeyField.text = result.apiKey
                            if (result.tenantId.isNotBlank()) {
                                tenantIdField.text = result.tenantId
                            }
                            apiUrlField.text = "https://sandbox-api.truthlocks.com/v1"
                            Messages.showInfoMessage(
                                "Your sandbox key is stored securely. Apply these settings, verify your email, then register an agent. Production access requires workspace onboarding.",
                                "Truthlocks Registration"
                            )
                        }
                        is RegistrationService.RegistrationResult.Error -> {
                            Messages.showErrorDialog(
                                result.message,
                                "Registration Failed"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Dialog that asks the user for their email address during registration.
     */
    private class RegistrationDialog : DialogWrapper(true) {
        private val emailField = JBTextField(30)

        init {
            title = "Register with Truthlocks"
            setOKButtonText("Register")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout(0, 8))
            panel.preferredSize = Dimension(400, 80)
            panel.add(
                JBLabel("Enter your email to receive a free API key:"),
                BorderLayout.NORTH
            )
            panel.add(emailField, BorderLayout.CENTER)
            return panel
        }

        fun getEmail(): String = emailField.text.trim()
    }

    override fun isModified(): Boolean {
        val state = MAIPSettingsState.getInstance()
        return apiUrlField.text != state.apiUrl ||
            String(apiKeyField.password) != state.getApiKey() ||
            tenantIdField.text != state.tenantId ||
            agentIdField.text != state.agentId ||
            algorithmCombo.selectedItem as String != state.signingAlgorithm ||
            (timeoutSpinner.value as Number).toLong() != state.timeoutMs ||
            (maxRetriesSpinner.value as Number).toInt() != state.maxRetries ||
            autoCommitCheckbox.isSelected != state.autoReceiptOnCommit ||
            autoSaveCheckbox.isSelected != state.autoReceiptOnSave ||
            autoBuildCheckbox.isSelected != state.autoReceiptOnBuild
    }

    override fun apply() {
        val state = MAIPSettingsState.getInstance()
        state.apiUrl = apiUrlField.text.trim()
        state.setApiKey(String(apiKeyField.password).trim())
        state.tenantId = tenantIdField.text.trim()
        state.agentId = agentIdField.text.trim()
        state.signingAlgorithm = algorithmCombo.selectedItem as String
        state.timeoutMs = (timeoutSpinner.value as Number).toLong()
        state.maxRetries = (maxRetriesSpinner.value as Number).toInt()
        state.autoReceiptOnCommit = autoCommitCheckbox.isSelected
        state.autoReceiptOnSave = autoSaveCheckbox.isSelected
        state.autoReceiptOnBuild = autoBuildCheckbox.isSelected
        com.intellij.openapi.project.ProjectManager.getInstance().openProjects.forEach { project ->
            val service = com.truthlocks.maip.jetbrains.services.MAIPProjectService.getInstance(project)
            service.rebuildClient()
            service.refreshAll()
        }
    }

    override fun reset() {
        val state = MAIPSettingsState.getInstance()
        apiUrlField.text = state.apiUrl
        apiKeyField.text = state.getApiKey()
        tenantIdField.text = state.tenantId
        agentIdField.text = state.agentId
        algorithmCombo.selectedItem = state.signingAlgorithm
        timeoutSpinner.value = state.timeoutMs
        maxRetriesSpinner.value = state.maxRetries
        autoCommitCheckbox.isSelected = state.autoReceiptOnCommit
        autoSaveCheckbox.isSelected = state.autoReceiptOnSave
        autoBuildCheckbox.isSelected = state.autoReceiptOnBuild
    }

    override fun disposeUIResources() {
        panel = null
    }
}
