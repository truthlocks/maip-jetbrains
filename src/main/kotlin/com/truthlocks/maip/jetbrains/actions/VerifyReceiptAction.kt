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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.truthlocks.maip.jetbrains.services.ReceiptService
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Action that verifies an existing MAIP receipt by its ID.
 *
 * Prompts for a receipt ID, calls the verification API, and
 * displays the full verification result in a detail dialog.
 */
class VerifyReceiptAction : AnAction() {

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

        val receiptId = Messages.showInputDialog(
            project,
            "Enter the receipt ID to verify:",
            "Verify MAIP Receipt",
            null
        )

        if (receiptId.isNullOrBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            val receiptService = ReceiptService.getInstance(project)
            val result = receiptService.verifyReceipt(receiptId.trim())

            if (result != null) {
                ApplicationManager.getApplication().invokeLater {
                    VerificationResultDialog(receiptId.trim(), result.toString()).show()
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * Dialog displaying the full verification result as formatted JSON.
     */
    private class VerificationResultDialog(
        receiptId: String,
        private val resultJson: String
    ) : DialogWrapper(true) {

        private val resultArea = JBTextArea(resultJson, 20, 60).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }

        init {
            title = "Verification Result: $receiptId"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val scrollPane = JBScrollPane(resultArea)
            scrollPane.preferredSize = Dimension(600, 400)

            return FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Result:"), scrollPane, 1, true)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }
    }
}
