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

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persistent settings for the MAIP plugin.
 *
 * Non-sensitive fields (API URL, tenant ID, agent ID, toggles) are serialized
 * to the IDE's configuration directory. The API key is stored separately in
 * the platform [PasswordSafe] so that it is never written to plain-text XML.
 */
@State(
    name = "com.truthlocks.maip.settings",
    storages = [Storage("maip-settings.xml")]
)
class MAIPSettingsState : PersistentStateComponent<MAIPSettingsState.State> {

    /**
     * Serializable state holder. Only non-secret fields are persisted here.
     */
    data class State(
        var apiUrl: String = "https://api.truthlocks.com/maip/v1",
        var tenantId: String = "",
        var agentId: String = "",
        var autoReceiptOnCommit: Boolean = true,
        var autoReceiptOnSave: Boolean = false,
        var autoReceiptOnBuild: Boolean = false,
        var timeoutMs: Long = 30_000L,
        var maxRetries: Int = 3
    )

    private var internalState = State()

    /** The MAIP API base URL. */
    var apiUrl: String
        get() = internalState.apiUrl
        set(value) { internalState.apiUrl = value }

    /** The tenant identifier. */
    var tenantId: String
        get() = internalState.tenantId
        set(value) { internalState.tenantId = value }

    /** The default agent ID for receipt creation. */
    var agentId: String
        get() = internalState.agentId
        set(value) { internalState.agentId = value }

    /** Whether to auto-generate receipts on VCS commits. */
    var autoReceiptOnCommit: Boolean
        get() = internalState.autoReceiptOnCommit
        set(value) { internalState.autoReceiptOnCommit = value }

    /** Whether to auto-generate receipts on file save. */
    var autoReceiptOnSave: Boolean
        get() = internalState.autoReceiptOnSave
        set(value) { internalState.autoReceiptOnSave = value }

    /** Whether to auto-generate receipts on build completion. */
    var autoReceiptOnBuild: Boolean
        get() = internalState.autoReceiptOnBuild
        set(value) { internalState.autoReceiptOnBuild = value }

    /** HTTP request timeout in milliseconds. */
    var timeoutMs: Long
        get() = internalState.timeoutMs
        set(value) { internalState.timeoutMs = value }

    /** Maximum retry count for transient HTTP failures. */
    var maxRetries: Int
        get() = internalState.maxRetries
        set(value) { internalState.maxRetries = value }

    override fun getState(): State = internalState

    override fun loadState(state: State) {
        internalState = state
    }

    /**
     * Retrieves the API key from the platform credential store.
     *
     * @return The stored API key, or an empty string if none is set.
     */
    fun getApiKey(): String {
        val attributes = createCredentialAttributes()
        return PasswordSafe.instance.getPassword(attributes) ?: ""
    }

    /**
     * Stores the API key in the platform credential store.
     *
     * @param apiKey The API key to store.
     */
    fun setApiKey(apiKey: String) {
        val attributes = createCredentialAttributes()
        val credentials = Credentials("maip-api-key", apiKey)
        PasswordSafe.instance.set(attributes, credentials)
    }

    /**
     * Returns `true` if the minimum required configuration is present.
     */
    fun isConfigured(): Boolean {
        return apiUrl.isNotBlank() && getApiKey().isNotBlank() && tenantId.isNotBlank()
    }

    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("MAIP", "api-key")
        )
    }

    companion object {
        /**
         * Returns the singleton instance of [MAIPSettingsState].
         */
        fun getInstance(): MAIPSettingsState {
            return ApplicationManager.getApplication().getService(MAIPSettingsState::class.java)
        }
    }
}
