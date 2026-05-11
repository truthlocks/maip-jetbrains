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

package com.truthlocks.maip.jetbrains.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener

/**
 * File save listener that triggers auto-receipt generation.
 *
 * This listener is registered dynamically by [com.truthlocks.maip.jetbrains.services.AutoReceiptService]
 * when the `autoReceiptOnSave` setting is enabled. The actual receipt creation
 * logic is delegated to the [AutoReceiptService] to keep this listener thin.
 *
 * Note: This class serves as a structural placeholder for the message bus subscription.
 * The active subscription is created programmatically in [AutoReceiptService.registerFileSaveListener].
 */
class FileSaveListener : FileDocumentManagerListener {

    private val log = Logger.getInstance(FileSaveListener::class.java)

    /**
     * Called before a document is saved. Logs the save event for diagnostics.
     * The actual auto-receipt logic is handled by the [AutoReceiptService]
     * subscription to avoid coupling.
     *
     * @param document The document being saved.
     */
    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        log.debug("File save detected: ${file.path}")
    }
}
