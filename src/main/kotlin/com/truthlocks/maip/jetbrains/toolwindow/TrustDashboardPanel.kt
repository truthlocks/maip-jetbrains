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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.truthlocks.maip.jetbrains.icons.MAIPIcons
import com.truthlocks.maip.jetbrains.services.MAIPProjectService
import com.truthlocks.maip.jetbrains.services.TrustService
import java.awt.*
import javax.swing.*

/**
 * Trust dashboard panel for the MAIP tool window.
 *
 * Displays an overview of trust scores across all agents with:
 * - KPI cards for total agents, average trust, and distribution breakdown
 * - A horizontal bar chart of trust distribution (high/medium/low)
 * - A recent activity list from cached receipts
 */
class TrustDashboardPanel(private val project: Project) : Disposable {

    private val mainPanel = JPanel(BorderLayout())
    private val kpiPanel = JPanel(GridLayout(1, 4, 8, 0))
    private val chartPanel = TrustDistributionChart()
    private val activityModel = DefaultListModel<String>()
    private val activityList = JList(activityModel)

    private val totalAgentsLabel = createKpiLabel("0")
    private val avgTrustLabel = createKpiLabel("0.0%")
    private val highTrustLabel = createKpiLabel("0")
    private val lowTrustLabel = createKpiLabel("0")

    /** The root Swing component for embedding in the tool window. */
    val component: JComponent get() = mainPanel

    private val listener = object : MAIPProjectService.DataChangeListener {
        override fun onTrustScoresChanged(trustScores: List<JsonObject>) {
            ApplicationManager.getApplication().invokeLater {
                updateDashboard(trustScores)
            }
        }

        override fun onReceiptsChanged(receipts: List<JsonObject>) {
            ApplicationManager.getApplication().invokeLater {
                updateActivity(receipts)
            }
        }
    }

    init {
        setupUI()
        MAIPProjectService.getInstance(project).addListener(listener)

        val cachedScores = MAIPProjectService.getInstance(project).trustScores
        if (cachedScores.isNotEmpty()) {
            updateDashboard(cachedScores)
        }
        val cachedReceipts = MAIPProjectService.getInstance(project).receipts
        if (cachedReceipts.isNotEmpty()) {
            updateActivity(cachedReceipts)
        }
    }

    private fun setupUI() {
        mainPanel.border = JBUI.Borders.empty(8)

        kpiPanel.add(createKpiCard("Total Agents", totalAgentsLabel, MAIPIcons.AGENT))
        kpiPanel.add(createKpiCard("Avg Trust", avgTrustLabel, MAIPIcons.TRUST))
        kpiPanel.add(createKpiCard("High Trust", highTrustLabel, MAIPIcons.TRUST_HIGH))
        kpiPanel.add(createKpiCard("Low Trust", lowTrustLabel, MAIPIcons.TRUST_LOW))

        val refreshButton = JButton("Refresh").apply {
            addActionListener {
                MAIPProjectService.getInstance(project).refreshTrustScores()
                MAIPProjectService.getInstance(project).refreshReceipts()
            }
        }

        val topPanel = JPanel(BorderLayout()).apply {
            add(kpiPanel, BorderLayout.CENTER)
            add(refreshButton, BorderLayout.EAST)
        }

        chartPanel.preferredSize = Dimension(300, 80)
        chartPanel.minimumSize = Dimension(200, 60)

        val activityScroll = JBScrollPane(activityList)
        activityScroll.preferredSize = Dimension(300, 150)

        val activityLabel = JBLabel("Recent Activity")
        activityLabel.font = activityLabel.font.deriveFont(Font.BOLD)

        val bottomPanel = JPanel(BorderLayout()).apply {
            add(activityLabel, BorderLayout.NORTH)
            add(activityScroll, BorderLayout.CENTER)
        }

        val centerPanel = JPanel(BorderLayout()).apply {
            add(chartPanel, BorderLayout.NORTH)
            add(bottomPanel, BorderLayout.CENTER)
        }

        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(centerPanel, BorderLayout.CENTER)
    }

    private fun updateDashboard(trustScores: List<JsonObject>) {
        val scores = trustScores.map { TrustService.extractScore(it) }

        val total = scores.size
        val avg = if (scores.isEmpty()) 0.0 else scores.average()
        val high = scores.count { it >= 0.8 }
        val medium = scores.count { it in 0.5..0.79 }
        val low = scores.count { it < 0.5 }

        totalAgentsLabel.text = total.toString()
        avgTrustLabel.text = TrustService.formatScore(avg)
        avgTrustLabel.foreground = TrustService.trustColor(avg)
        highTrustLabel.text = high.toString()
        highTrustLabel.foreground = TrustService.HIGH_TRUST_COLOR
        lowTrustLabel.text = low.toString()
        lowTrustLabel.foreground = TrustService.LOW_TRUST_COLOR

        chartPanel.setDistribution(high, medium, low)
    }

    private fun updateActivity(receipts: List<JsonObject>) {
        activityModel.clear()
        receipts.take(20).forEach { receipt ->
            val type = receipt.get("type")?.asString ?: "unknown"
            val desc = receipt.get("description")?.asString ?: "No description"
            val timestamp = receipt.get("created_at")?.asString
                ?: receipt.get("timestamp")?.asString
                ?: ""
            activityModel.addElement("[$type] ${desc.take(50)} - $timestamp")
        }
    }

    private fun createKpiCard(title: String, valueLabel: JBLabel, icon: Icon): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            val titleLabel = JBLabel(title, icon, SwingConstants.LEFT).apply {
                font = font.deriveFont(Font.PLAIN, 11f)
            }
            add(titleLabel, BorderLayout.NORTH)
            add(valueLabel, BorderLayout.CENTER)
        }
    }

    private fun createKpiLabel(initialText: String): JBLabel {
        return JBLabel(initialText).apply {
            font = font.deriveFont(Font.BOLD, 20f)
            horizontalAlignment = SwingConstants.CENTER
        }
    }

    override fun dispose() {
        MAIPProjectService.getInstance(project).removeListener(listener)
    }

    /**
     * Custom panel that paints a horizontal stacked bar chart of trust distribution.
     */
    private class TrustDistributionChart : JPanel() {

        private var high = 0
        private var medium = 0
        private var low = 0

        fun setDistribution(high: Int, medium: Int, low: Int) {
            this.high = high
            this.medium = medium
            this.low = low
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val total = high + medium + low
            if (total == 0) {
                g2.color = Color.GRAY
                g2.font = font.deriveFont(12f)
                g2.drawString("No trust data available", 10, height / 2 + 4)
                return
            }

            val barHeight = 30
            val barY = (height - barHeight) / 2
            val usableWidth = width - 20
            val startX = 10

            val highWidth = (high.toDouble() / total * usableWidth).toInt()
            val medWidth = (medium.toDouble() / total * usableWidth).toInt()
            val lowWidth = usableWidth - highWidth - medWidth

            var x = startX

            if (highWidth > 0) {
                g2.color = TrustService.HIGH_TRUST_COLOR
                g2.fillRoundRect(x, barY, highWidth, barHeight, 6, 6)
                if (highWidth > 30) {
                    g2.color = Color.WHITE
                    g2.font = font.deriveFont(Font.BOLD, 11f)
                    g2.drawString("$high", x + 6, barY + barHeight / 2 + 4)
                }
                x += highWidth
            }

            if (medWidth > 0) {
                g2.color = TrustService.MEDIUM_TRUST_COLOR
                g2.fillRect(x, barY, medWidth, barHeight)
                if (medWidth > 30) {
                    g2.color = Color.WHITE
                    g2.font = font.deriveFont(Font.BOLD, 11f)
                    g2.drawString("$medium", x + 6, barY + barHeight / 2 + 4)
                }
                x += medWidth
            }

            if (lowWidth > 0) {
                g2.color = TrustService.LOW_TRUST_COLOR
                g2.fillRoundRect(x, barY, lowWidth, barHeight, 6, 6)
                if (lowWidth > 30) {
                    g2.color = Color.WHITE
                    g2.font = font.deriveFont(Font.BOLD, 11f)
                    g2.drawString("$low", x + 6, barY + barHeight / 2 + 4)
                }
            }

            // Legend
            val legendY = barY + barHeight + 16
            g2.font = font.deriveFont(10f)
            var legendX = startX

            g2.color = TrustService.HIGH_TRUST_COLOR
            g2.fillRect(legendX, legendY, 10, 10)
            g2.color = foreground
            g2.drawString("High (>=0.8)", legendX + 14, legendY + 9)
            legendX += 100

            g2.color = TrustService.MEDIUM_TRUST_COLOR
            g2.fillRect(legendX, legendY, 10, 10)
            g2.color = foreground
            g2.drawString("Medium (0.5-0.79)", legendX + 14, legendY + 9)
            legendX += 130

            g2.color = TrustService.LOW_TRUST_COLOR
            g2.fillRect(legendX, legendY, 10, 10)
            g2.color = foreground
            g2.drawString("Low (<0.5)", legendX + 14, legendY + 9)
        }
    }
}
