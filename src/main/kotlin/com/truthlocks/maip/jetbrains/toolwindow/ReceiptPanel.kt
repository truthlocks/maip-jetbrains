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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.truthlocks.maip.jetbrains.icons.MAIPIcons
import com.truthlocks.maip.jetbrains.notifications.MAIPNotifier
import com.truthlocks.maip.jetbrains.services.MAIPProjectService
import com.truthlocks.maip.jetbrains.services.ReceiptService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Receipt explorer panel for the MAIP tool window.
 *
 * Displays a filterable list of receipts with a custom cell renderer
 * showing type, description, and timestamp. Supports double-click to
 * open receipt details and right-click context menu with verify,
 * copy ID, and view delegation chain actions.
 */
class ReceiptPanel(private val project: Project) : Disposable {

    private val listModel = DefaultListModel<JsonObject>()
    private val receiptList = JBList(listModel)
    private val filterCombo = JComboBox(arrayOf("All", "code", "commit", "build"))
    private val mainPanel = JPanel(BorderLayout())

    /** The root Swing component for embedding in the tool window. */
    val component: JComponent get() = mainPanel

    private val listener = object : MAIPProjectService.DataChangeListener {
        override fun onReceiptsChanged(receipts: List<JsonObject>) {
            ApplicationManager.getApplication().invokeLater {
                updateList(receipts)
            }
        }
    }

    init {
        setupUI()
        setupListeners()

        MAIPProjectService.getInstance(project).addListener(listener)

        val cached = MAIPProjectService.getInstance(project).receipts
        if (cached.isNotEmpty()) {
            updateList(cached)
        }
    }

    private fun setupUI() {
        receiptList.cellRenderer = ReceiptCellRenderer()
        receiptList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val filterPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("Type: "))
            add(filterCombo)
            add(Box.createHorizontalGlue())
        }

        val toolbar = ToolbarDecorator.createDecorator(receiptList)
            .disableAddAction()
            .disableRemoveAction()
            .addExtraAction(object : ToolbarDecorator.ElementActionButton("Refresh", MAIPIcons.REFRESH) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    MAIPProjectService.getInstance(project).refreshReceipts()
                }

                override fun isEnabled(): Boolean = true
            })
            .createPanel()

        mainPanel.add(filterPanel, BorderLayout.NORTH)
        mainPanel.add(toolbar, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        filterCombo.addActionListener {
            val selected = filterCombo.selectedItem as? String ?: "All"
            val allReceipts = MAIPProjectService.getInstance(project).receipts
            if (selected == "All") {
                updateList(allReceipts)
            } else {
                updateList(allReceipts.filter {
                    it.get("type")?.asString == selected
                })
            }
        }

        receiptList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = receiptList.selectedValue ?: return
                    showReceiptDetail(selected)
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) showPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showPopup(e)
            }
        })
    }

    private fun showPopup(e: MouseEvent) {
        val index = receiptList.locationToIndex(e.point)
        if (index < 0) return
        receiptList.selectedIndex = index
        val receipt = listModel.getElementAt(index) ?: return
        val receiptId = receipt.get("id")?.asString ?: return

        val popup = JPopupMenu()

        popup.add(JMenuItem("Verify").apply {
            addActionListener {
                CoroutineScope(Dispatchers.IO).launch {
                    ReceiptService.getInstance(project).verifyReceipt(receiptId)
                }
            }
        })

        popup.add(JMenuItem("Copy ID").apply {
            addActionListener {
                val selection = StringSelection(receiptId)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                MAIPNotifier.info(project, "MAIP", "Receipt ID copied to clipboard")
            }
        })

        popup.add(JMenuItem("View Delegation Chain").apply {
            addActionListener {
                CoroutineScope(Dispatchers.IO).launch {
                    val chain = ReceiptService.getInstance(project).getDelegationChain(receiptId)
                    if (chain != null) {
                        ApplicationManager.getApplication().invokeLater {
                            DelegationChainDialog(receiptId, chain.toString()).show()
                        }
                    } else {
                        MAIPNotifier.warn(project, "MAIP", "No delegation chain found for $receiptId")
                    }
                }
            }
        })

        popup.show(receiptList, e.x, e.y)
    }

    private fun showReceiptDetail(receipt: JsonObject) {
        val receiptId = receipt.get("id")?.asString ?: "N/A"
        ReceiptDetailDialog(receiptId, receipt.toString()).show()
    }

    private fun updateList(receipts: List<JsonObject>) {
        listModel.clear()
        receipts.forEach { listModel.addElement(it) }
    }

    override fun dispose() {
        MAIPProjectService.getInstance(project).removeListener(listener)
    }

    /**
     * Custom cell renderer for receipt list items.
     */
    private class ReceiptCellRenderer : ColoredListCellRenderer<JsonObject>() {
        override fun customizeCellRenderer(
            list: JList<out JsonObject>,
            value: JsonObject?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            if (value == null) return

            val type = value.get("type")?.asString ?: "unknown"
            val description = value.get("description")?.asString ?: "No description"
            val timestamp = value.get("created_at")?.asString
                ?: value.get("timestamp")?.asString
                ?: ""
            val id = value.get("id")?.asString?.take(8) ?: ""

            icon = when (type) {
                "code" -> MAIPIcons.RECEIPT
                "commit" -> MAIPIcons.RECEIPT_VERIFIED
                "build" -> MAIPIcons.AUDIT
                else -> MAIPIcons.RECEIPT
            }

            append("[$type] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
            append(description.take(60), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (id.isNotEmpty()) {
                append("  #$id", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            if (timestamp.isNotEmpty()) {
                append("  $timestamp", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
        }
    }

    /**
     * Dialog showing full receipt details.
     */
    private class ReceiptDetailDialog(
        receiptId: String,
        private val json: String
    ) : DialogWrapper(true) {

        init {
            title = "Receipt: $receiptId"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val area = JBTextArea(json, 20, 60).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
            }
            val scroll = JBScrollPane(area)
            scroll.preferredSize = Dimension(600, 400)
            return scroll
        }
    }

    /**
     * Dialog showing the delegation chain for a receipt.
     */
    private class DelegationChainDialog(
        receiptId: String,
        private val json: String
    ) : DialogWrapper(true) {

        init {
            title = "Delegation Chain: $receiptId"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val area = JBTextArea(json, 20, 60).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
            }
            val scroll = JBScrollPane(area)
            scroll.preferredSize = Dimension(600, 400)
            return scroll
        }
    }
}
