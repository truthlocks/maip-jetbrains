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
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.truthlocks.maip.jetbrains.client.MAIPApiClient
import com.truthlocks.maip.jetbrains.client.MAIPConfig
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-level service that owns the [MAIPApiClient] and caches
 * agents, receipts, and trust scores. Panels in the tool window
 * subscribe to data-change events through [DataChangeListener].
 *
 * All network operations run on a dedicated coroutine scope bound
 * to the project lifecycle.
 */
class MAIPProjectService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(MAIPProjectService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var client: MAIPApiClient? = null

    /** Cached agent list. Thread-safe via copy-on-write. */
    @Volatile
    var agents: List<JsonObject> = emptyList()
        private set

    /** Cached receipt list. Thread-safe via copy-on-write. */
    @Volatile
    var receipts: List<JsonObject> = emptyList()
        private set

    /** Cached trust score list. Thread-safe via copy-on-write. */
    @Volatile
    var trustScores: List<JsonObject> = emptyList()
        private set

    private val listeners = CopyOnWriteArrayList<DataChangeListener>()

    /**
     * Listener interface for data change events.
     */
    interface DataChangeListener {
        /** Called when agent data is refreshed. */
        fun onAgentsChanged(agents: List<JsonObject>) {}
        /** Called when receipt data is refreshed. */
        fun onReceiptsChanged(receipts: List<JsonObject>) {}
        /** Called when trust score data is refreshed. */
        fun onTrustScoresChanged(trustScores: List<JsonObject>) {}
    }

    /**
     * Registers a listener for data change events.
     *
     * @param listener The listener to register.
     */
    fun addListener(listener: DataChangeListener) {
        listeners.add(listener)
    }

    /**
     * Unregisters a data change listener.
     *
     * @param listener The listener to remove.
     */
    fun removeListener(listener: DataChangeListener) {
        listeners.remove(listener)
    }

    /**
     * Initializes the service by creating the API client and loading
     * initial data from the MAIP API. Called on project open by [MAIPPlugin].
     */
    fun initialize() {
        if (!MAIPSettingsState.getInstance().isConfigured()) {
            log.info("MAIP not configured, skipping initialization")
            return
        }
        rebuildClient()
        refreshAll()
    }

    /**
     * Rebuilds the API client from current settings. Call this after
     * settings are changed to pick up new credentials or endpoints.
     */
    fun rebuildClient() {
        client?.shutdown()
        val config = MAIPConfig.fromSettings()
        if (config.isValid) {
            client = MAIPApiClient(config)
        }
    }

    /**
     * Returns the current API client, or `null` if not configured.
     */
    fun getClient(): MAIPApiClient? = client

    /**
     * Refreshes all cached data (agents, receipts, trust scores) from the API.
     */
    fun refreshAll() {
        refreshAgents()
        refreshReceipts()
        refreshTrustScores()
    }

    /**
     * Refreshes the cached agent list from the API.
     */
    fun refreshAgents() {
        val apiClient = client ?: return
        scope.launch {
            try {
                val result = apiClient.listAgents()
                agents = result
                listeners.forEach { it.onAgentsChanged(result) }
            } catch (e: Exception) {
                log.warn("Failed to refresh agents", e)
            }
        }
    }

    /**
     * Refreshes the cached receipt list from the API.
     */
    fun refreshReceipts() {
        val apiClient = client ?: return
        scope.launch {
            try {
                val result = apiClient.listReceipts()
                receipts = result
                listeners.forEach { it.onReceiptsChanged(result) }
            } catch (e: Exception) {
                log.warn("Failed to refresh receipts", e)
            }
        }
    }

    /**
     * Refreshes the cached trust scores from the API.
     */
    fun refreshTrustScores() {
        val apiClient = client ?: return
        scope.launch {
            try {
                val result = apiClient.listTrustScores()
                trustScores = result
                listeners.forEach { it.onTrustScoresChanged(result) }
            } catch (e: Exception) {
                log.warn("Failed to refresh trust scores", e)
            }
        }
    }

    override fun dispose() {
        scope.cancel("Project service disposed")
        client?.shutdown()
        listeners.clear()
    }

    companion object {
        /**
         * Convenience accessor for the project-level service instance.
         */
        fun getInstance(project: Project): MAIPProjectService {
            return project.getService(MAIPProjectService::class.java)
        }
    }
}
