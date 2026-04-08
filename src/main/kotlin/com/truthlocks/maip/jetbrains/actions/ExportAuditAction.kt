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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Action that exports an audit report from the MAIP API.
 *
 * Prompts for a date range and export format, calls the audit export
 * endpoint, and saves the result to a user-selected file.
 */
class ExportAuditAction : AnAction() {

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

        val dialog = AuditExportDialog()
        if (!dialog.showAndGet()) return

        val startDate = dialog.startDate.trim()
        val endDate = dialog.endDate.trim()
        val format = dialog.format

        if (startDate.isBlank() || endDate.isBlank()) {
            Messages.showErrorDialog(project, "Start and end dates are required.", "Validation Error")
            return
        }

        val extension = if (format == "csv") "csv" else "json"
        val descriptor = FileSaverDescriptor("Save Audit Report", "Choose where to save the audit report", extension)
        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = saveDialog.save("maip-audit-report.$extension") ?: return
        val outputFile = wrapper.file

        CoroutineScope(Dispatchers.IO).launch {
            val client = MAIPProjectService.getInstance(project).getClient()
            if (client == null) {
                MAIPNotifier.error(project, "MAIP", "API client not available.")
                return@launch
            }

            val result = client.exportAudit(startDate, endDate, format)
            if (result != null) {
                outputFile.writeText(result)
                ApplicationManager.getApplication().invokeLater {
                    MAIPNotifier.info(
                        project,
                        "Audit Export Complete",
                        "Report saved to ${outputFile.absolutePath}"
                    )
                }
            } else {
                MAIPNotifier.error(project, "MAIP", "Failed to export audit report.")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * Dialog for configuring audit export parameters.
     */
    private class AuditExportDialog : DialogWrapper(true) {

        private val today = LocalDate.now()
        private val thirtyDaysAgo = today.minusDays(30)
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        private val startDateField = JBTextField(formatter.format(thirtyDaysAgo), 15)
        private val endDateField = JBTextField(formatter.format(today), 15)
        private val formatCombo = JComboBox(arrayOf("json", "csv"))

        val startDate: String get() = startDateField.text
        val endDate: String get() = endDateField.text
        val format: String get() = formatCombo.selectedItem as? String ?: "json"

        init {
            title = "Export Audit Report"
            init()
        }

        override fun createCenterPanel(): JComponent {
            return FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Start Date (YYYY-MM-DD):"), startDateField, 1, false)
                .addLabeledComponent(JBLabel("End Date (YYYY-MM-DD):"), endDateField, 1, false)
                .addLabeledComponent(JBLabel("Format:"), formatCombo, 1, false)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }

        override fun getPreferredFocusedComponent(): JComponent = startDateField
    }
}
