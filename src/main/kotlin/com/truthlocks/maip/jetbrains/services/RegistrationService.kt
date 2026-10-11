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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Handles developer registration with the Truthlocks sandbox API.
 *
 * Sends a registration request and stores the returned API key in the
 * IDE credential store (PasswordSafe) so it persists securely across sessions.
 */
@Service(Service.Level.APP)
class RegistrationService {

    private val log = Logger.getInstance(RegistrationService::class.java)
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Result of a registration attempt.
     */
    sealed class RegistrationResult {
        data class Success(val apiKey: String, val tenantId: String) : RegistrationResult()
        data class Error(val message: String) : RegistrationResult()
    }

    /**
     * Registers a developer by email.
     *
     * POSTs to the sandbox registration endpoint and returns the API key
     * on success, or an error message on failure.
     *
     * @param email The developer's email address.
     * @return A [RegistrationResult] with either the API key or error details.
     */
    suspend fun register(email: String): RegistrationResult = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("email", email)
                addProperty("source", "jetbrains")
            }

            val request = Request.Builder()
                .url(REGISTRATION_URL)
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "MAIP-JetBrains/1.1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                val apiKey = json.get("api_key")?.asString
                    ?: json.get("apiKey")?.asString
                    ?: json.get("key")?.asString
                val tenantId = json.get("tenant_id")?.asString
                    ?: json.get("tenantId")?.asString
                    ?: ""

                if (apiKey.isNullOrBlank()) {
                    RegistrationResult.Error("Registration succeeded but no API key was returned.")
                } else {
                    RegistrationResult.Success(apiKey = apiKey, tenantId = tenantId)
                }
            } else {
                val errorMsg = if (responseBody != null) {
                    try {
                        val errorJson = gson.fromJson(responseBody, JsonObject::class.java)
                        errorJson.get("message")?.asString
                            ?: errorJson.get("error")?.asString
                            ?: "Registration failed (HTTP ${response.code})"
                    } catch (_: Exception) {
                        "Registration failed (HTTP ${response.code})"
                    }
                } else {
                    "Registration failed (HTTP ${response.code})"
                }
                RegistrationResult.Error(errorMsg)
            }
            }
        } catch (e: Exception) {
            log.warn("Registration request failed", e)
            RegistrationResult.Error("Network error: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Stores the API key in the IDE credential store (PasswordSafe).
     *
     * @param apiKey The API key to store securely.
     */
    fun storeApiKey(apiKey: String) {
        val attributes = CredentialAttributes(
            generateServiceName("MAIP", "api-key")
        )
        val credentials = Credentials("maip-api-key", apiKey)
        PasswordSafe.instance.set(attributes, credentials)
    }

    /**
     * Checks whether stored credentials exist in PasswordSafe.
     *
     * @return `true` if an API key is stored, `false` otherwise.
     */
    fun hasStoredCredentials(): Boolean {
        val attributes = CredentialAttributes(
            generateServiceName("MAIP", "api-key")
        )
        val password = PasswordSafe.instance.getPassword(attributes)
        return !password.isNullOrBlank()
    }

    companion object {
        private const val REGISTRATION_URL =
            "https://sandbox-api.truthlocks.com/v1/developers/register"

        fun getInstance(): RegistrationService {
            return ApplicationManager.getApplication().getService(RegistrationService::class.java)
        }
    }
}
