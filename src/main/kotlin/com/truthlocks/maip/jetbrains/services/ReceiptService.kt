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

package com.truthlocks.maip.jetbrains.services

import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.truthlocks.maip.jetbrains.client.MAIPConfig
import com.truthlocks.maip.jetbrains.notifications.MAIPNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Service for creating, verifying, and browsing integrity receipts.
 *
 * Provides higher-level receipt operations on top of the raw API client,
 * including SHA-256 hashing of file contents and notification dispatch.
 */
class ReceiptService(private val project: Project) {

    private val log = Logger.getInstance(ReceiptService::class.java)

    /**
     * Creates a receipt for a virtual file.
     *
     * Reads the file content, computes a SHA-256 hash, and sends a
     * receipt creation request to the MAIP API. Shows a balloon
     * notification with the result.
     *
     * @param file        The virtual file to create a receipt for.
     * @param description An optional description. Defaults to the file path.
     * @return The created receipt JSON object, or `null` on failure.
     */
    suspend fun createReceiptForFile(
        file: VirtualFile,
        description: String? = null
    ): JsonObject? = withContext(Dispatchers.IO) {
        val client = MAIPProjectService.getInstance(project).getClient()
        if (client == null) {
            MAIPNotifier.error(project, "MAIP", "Plugin not configured. Open Settings > Tools > MAIP.")
            return@withContext null
        }

        val config = MAIPConfig.fromSettings()
        if (config.agentId.isBlank()) {
            MAIPNotifier.error(project, "MAIP", "No agent ID configured. Set it in Settings > Tools > MAIP.")
            return@withContext null
        }

        try {
            val content = file.contentsToByteArray()
            val hash = sha256Hex(content)
            val desc = description ?: "Receipt for ${file.path}"

            val receipt = client.createReceipt(
                receiptType = "action",
                action = desc,
                agentId = config.agentId,
                payload = mapOf(
                    "file_path" to file.path,
                    "file_name" to file.name,
                    "file_size" to content.size.toString(),
                    "content_type" to file.fileType.name,
                    "artifact_hash" to hash
                )
            )

            if (receipt != null) {
                val receiptId = receipt.get("receipt_id")?.asString ?: receipt.get("id")?.asString ?: "unknown"
                MAIPNotifier.info(project, "MAIP Receipt Created", "Receipt ID: $receiptId")
                MAIPProjectService.getInstance(project).refreshReceipts()
            } else {
                MAIPNotifier.error(project, "MAIP", "Failed to create receipt for ${file.name}")
            }

            receipt
        } catch (e: Exception) {
            log.warn("Failed to create receipt for ${file.path}", e)
            MAIPNotifier.error(project, "MAIP", "Error creating receipt: ${e.message}")
            null
        }
    }

    /**
     * Creates a receipt for a VCS commit.
     *
     * @param commitHash   The VCS commit hash.
     * @param message      The commit message.
     * @param author       The commit author.
     * @param changedFiles The list of changed file paths.
     * @return The created receipt JSON object, or `null` on failure.
     */
    suspend fun createReceiptForCommit(
        commitHash: String,
        message: String,
        author: String,
        changedFiles: List<String>
    ): JsonObject? = withContext(Dispatchers.IO) {
        val client = MAIPProjectService.getInstance(project).getClient() ?: return@withContext null
        val config = MAIPConfig.fromSettings()
        if (config.agentId.isBlank()) return@withContext null

        try {
            val receipt = client.createReceipt(
                receiptType = "action",
                action = "git_commit: ${message.take(100)}",
                agentId = config.agentId,
                payload = mapOf(
                    "commit_hash" to commitHash,
                    "commit_message" to message,
                    "author" to author,
                    "changed_files" to changedFiles.joinToString(","),
                    "changed_file_count" to changedFiles.size.toString()
                )
            )

            if (receipt != null) {
                val receiptId = receipt.get("receipt_id")?.asString ?: receipt.get("id")?.asString ?: "unknown"
                MAIPNotifier.info(
                    project,
                    "MAIP Commit Receipt",
                    "Receipt $receiptId created for commit ${commitHash.take(8)}"
                )
                MAIPProjectService.getInstance(project).refreshReceipts()
            }

            receipt
        } catch (e: Exception) {
            log.warn("Failed to create commit receipt", e)
            null
        }
    }

    /**
     * Verifies a receipt by its identifier.
     *
     * @param receiptId The receipt ID to verify.
     * @return The verification result JSON object, or `null` on failure.
     */
    suspend fun verifyReceipt(receiptId: String): JsonObject? = withContext(Dispatchers.IO) {
        val client = MAIPProjectService.getInstance(project).getClient()
        if (client == null) {
            MAIPNotifier.error(project, "MAIP", "Plugin not configured.")
            return@withContext null
        }

        try {
            val result = client.verifyReceipt(receiptId)
            if (result != null) {
                val valid = result.get("valid")?.asBoolean ?: false
                if (valid) {
                    MAIPNotifier.info(project, "MAIP Verification", "Receipt $receiptId is VALID")
                } else {
                    MAIPNotifier.warn(project, "MAIP Verification", "Receipt $receiptId is INVALID")
                }
            } else {
                MAIPNotifier.error(project, "MAIP", "Verification failed for receipt $receiptId")
            }
            result
        } catch (e: Exception) {
            log.warn("Failed to verify receipt $receiptId", e)
            MAIPNotifier.error(project, "MAIP", "Verification error: ${e.message}")
            null
        }
    }

    /**
     * Retrieves the delegation chain for a receipt.
     *
     * @param receiptId The receipt ID.
     * @return The delegation chain JSON object, or `null` on failure.
     */
    suspend fun getDelegationTree(agentId: String): JsonObject? = withContext(Dispatchers.IO) {
        val client = MAIPProjectService.getInstance(project).getClient() ?: return@withContext null
        try {
            client.getDelegationTree(agentId)
        } catch (e: Exception) {
            log.warn("Failed to get delegation tree for $agentId", e)
            null
        }
    }

    companion object {
        /**
         * Returns the project-level [ReceiptService] instance.
         */
        fun getInstance(project: Project): ReceiptService {
            return project.getService(ReceiptService::class.java)
        }

        /**
         * Computes the SHA-256 hex digest of the given byte array.
         *
         * @param data The input bytes.
         * @return The lowercase hex string of the SHA-256 digest.
         */
        fun sha256Hex(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(data).joinToString("") { "%02x".format(it) }
        }
    }
}
