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

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
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
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(30_000L, 5_000L, 120_000L, 1_000L))
    private val maxRetriesSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1))
    private val autoCommitCheckbox = JBCheckBox("Auto-receipt on VCS commit")
    private val autoSaveCheckbox = JBCheckBox("Auto-receipt on file save")
    private val autoBuildCheckbox = JBCheckBox("Auto-receipt on build completion")

    override fun getDisplayName(): String = "MAIP"

    override fun createComponent(): JComponent {
        apiKeyField.columns = 40

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API URL:"), apiUrlField, 1, false)
            .addLabeledComponent(JBLabel("API Key:"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("Tenant ID:"), tenantIdField, 1, false)
            .addLabeledComponent(JBLabel("Default Agent ID:"), agentIdField, 1, false)
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

    override fun isModified(): Boolean {
        val state = MAIPSettingsState.getInstance()
        return apiUrlField.text != state.apiUrl ||
            String(apiKeyField.password) != state.getApiKey() ||
            tenantIdField.text != state.tenantId ||
            agentIdField.text != state.agentId ||
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
        state.timeoutMs = (timeoutSpinner.value as Number).toLong()
        state.maxRetries = (maxRetriesSpinner.value as Number).toInt()
        state.autoReceiptOnCommit = autoCommitCheckbox.isSelected
        state.autoReceiptOnSave = autoSaveCheckbox.isSelected
        state.autoReceiptOnBuild = autoBuildCheckbox.isSelected
    }

    override fun reset() {
        val state = MAIPSettingsState.getInstance()
        apiUrlField.text = state.apiUrl
        apiKeyField.text = state.getApiKey()
        tenantIdField.text = state.tenantId
        agentIdField.text = state.agentId
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
