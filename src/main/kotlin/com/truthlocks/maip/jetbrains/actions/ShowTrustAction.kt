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
import com.intellij.util.ui.FormBuilder
import com.truthlocks.maip.jetbrains.icons.MAIPIcons
import com.truthlocks.maip.jetbrains.services.TrustService
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Action that displays a trust score dialog for a given agent ID.
 *
 * Prompts the user for an agent ID, queries the MAIP trust API,
 * and presents the score with color-coded trust level indicators.
 */
class ShowTrustAction : AnAction() {

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

        val defaultAgentId = MAIPSettingsState.getInstance().agentId
        val agentId = Messages.showInputDialog(
            project,
            "Enter the agent ID:",
            "Show Trust Score",
            null,
            defaultAgentId,
            null
        )

        if (agentId.isNullOrBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            val trustService = TrustService.getInstance(project)
            val result = trustService.getTrustScore(agentId.trim())

            ApplicationManager.getApplication().invokeLater {
                if (result != null) {
                    val score = TrustService.extractScore(result)
                    TrustScoreDialog(agentId.trim(), score, result.toString()).show()
                } else {
                    Messages.showErrorDialog(
                        project,
                        "Failed to retrieve trust score for agent: $agentId",
                        "MAIP Trust Score"
                    )
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * Dialog displaying a trust score with visual indicators.
     */
    private class TrustScoreDialog(
        agentId: String,
        private val score: Double,
        private val rawJson: String
    ) : DialogWrapper(true) {

        init {
            title = "Trust Score: $agentId"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val color = TrustService.trustColor(score)
            val level = TrustService.trustLevel(score)
            val icon = MAIPIcons.trustIcon(score)
            val formatted = TrustService.formatScore(score)

            val scoreLabel = JBLabel("$formatted ($level)").apply {
                this.icon = icon
                foreground = color
                font = font.deriveFont(Font.BOLD, 18f)
            }

            val agentLabel = JBLabel("Agent: ${this@TrustScoreDialog.title?.removePrefix("Trust Score: ") ?: "N/A"}")
            val detailLabel = JBLabel("<html><pre>${rawJson.take(2000)}</pre></html>")

            return FormBuilder.createFormBuilder()
                .addComponent(agentLabel, 1)
                .addComponent(scoreLabel, 1)
                .addSeparator()
                .addLabeledComponent(JBLabel("Details:"), detailLabel, 1, true)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }
    }
}
