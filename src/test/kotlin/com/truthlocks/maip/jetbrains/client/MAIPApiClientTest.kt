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

package com.truthlocks.maip.jetbrains.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [MAIPApiClient] covering retry logic, authentication headers,
 * and response parsing.
 */
class MAIPApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MAIPApiClient
    private val gson = Gson()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()

        val config = MAIPConfig(
            apiUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "test-api-key-12345",
            tenantId = "test-tenant-001",
            agentId = "test-agent-001",
            timeoutMs = 5_000L,
            maxRetries = 2
        )
        client = MAIPApiClient(config)
    }

    @AfterEach
    fun teardown() {
        client.shutdown()
        server.shutdown()
    }

    @Test
    fun `registerAgent sends correct headers and body`() = runTest {
        val responseBody = JsonObject().apply {
            addProperty("id", "agent-uuid-1234")
            addProperty("name", "test-coder")
            addProperty("status", "active")
        }
        server.enqueue(MockResponse().setBody(gson.toJson(responseBody)).setResponseCode(200))

        val result = client.registerAgent("test-coder", listOf("code", "review"))

        assertNotNull(result)
        assertEquals("agent-uuid-1234", result?.get("id")?.asString)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/agents", request.path)
        assertEquals("test-api-key-12345", request.getHeader("X-API-Key"))
        assertNull(request.getHeader("X-Tenant-ID"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent")!!.contains("MAIP-JetBrains"))

        val body = gson.fromJson(request.body.readUtf8(), JsonObject::class.java)
        assertEquals("test-coder", body.get("display_name").asString)
    }

    @Test
    fun `listAgents parses data array correctly`() = runTest {
        val agent1 = JsonObject().apply { addProperty("id", "a1"); addProperty("name", "agent-1") }
        val agent2 = JsonObject().apply { addProperty("id", "a2"); addProperty("name", "agent-2") }
        val responseBody = JsonObject().apply {
            add("agents", gson.toJsonTree(listOf(agent1, agent2)))
        }
        server.enqueue(MockResponse().setBody(gson.toJson(responseBody)).setResponseCode(200))

        val agents = client.listAgents(limit = 10, offset = 0)

        assertEquals(2, agents.size)
        assertEquals("a1", agents[0].get("id").asString)
        assertEquals("a2", agents[1].get("id").asString)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("limit=10"))
        assertTrue(request.path!!.contains("offset=0"))
    }

    @Test
    fun `createReceipt sends metadata correctly`() = runTest {
        val responseBody = JsonObject().apply {
            addProperty("id", "receipt-uuid-5678")
            addProperty("type", "code")
        }
        server.enqueue(MockResponse().setBody(gson.toJson(responseBody)).setResponseCode(201))

        val result = client.createReceipt(
            receiptType = "code",
            description = "Test receipt",
            artifactHash = "abc123def456",
            agentId = "test-agent-001",
            metadata = mapOf("file_path" to "/src/main.kt", "file_size" to "1024")
        )

        assertNotNull(result)
        assertEquals("receipt-uuid-5678", result?.get("id")?.asString)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val body = gson.fromJson(request.body.readUtf8(), JsonObject::class.java)
        assertEquals("action", body.get("receipt_type").asString)
        assertEquals("code", body.getAsJsonObject("metadata").get("kind").asString)
        assertEquals("Test receipt", body.get("action").asString)
        assertEquals("abc123def456", body.getAsJsonObject("metadata").get("artifact_hash").asString)
        assertNotNull(body.get("metadata"))
    }

    @Test
    fun `verifyReceipt uses signature verification GET endpoint`() = runTest {
        val responseBody = JsonObject().apply {
            addProperty("valid", true)
            addProperty("signature_verified", true)
            addProperty("verdict", "VALID")
            addProperty("receipt_id", "receipt-123")
        }
        server.enqueue(MockResponse().setBody(gson.toJson(responseBody)).setResponseCode(200))

        val result = client.verifyReceipt("receipt-123")

        assertNotNull(result)
        assertTrue(result!!.get("valid").asBoolean)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/agent-receipts/verify/receipt-123", request.path)
    }

    @Test
    fun `retries on HTTP 429 with exponential backoff`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429))
        val successResponse = JsonObject().apply { addProperty("id", "agent-1") }
        server.enqueue(MockResponse().setBody(gson.toJson(successResponse)).setResponseCode(200))

        val result = client.getAgent("agent-1")

        assertNotNull(result)
        assertEquals("agent-1", result?.get("id")?.asString)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `retries on HTTP 500 server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val successResponse = JsonObject().apply { addProperty("id", "agent-1") }
        server.enqueue(MockResponse().setBody(gson.toJson(successResponse)).setResponseCode(200))

        val result = client.getAgent("agent-1")

        assertNotNull(result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `returns null after exhausting retries`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client.getAgent("agent-1")

        assertNull(result)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `does not retry on HTTP 400 client error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error": "bad request"}"""))

        val result = client.registerAgent("", emptyList())

        assertNull(result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `does not retry on HTTP 404 not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.getAgent("nonexistent")

        assertNull(result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `getTrustScore returns parsed trust object`() = runTest {
        val responseBody = JsonObject().apply {
            addProperty("agent_id", "agent-1")
            addProperty("score", 0.85)
            addProperty("level", "high")
        }
        server.enqueue(MockResponse().setBody(gson.toJson(responseBody)).setResponseCode(200))

        val result = client.getTrustScore("agent-1")

        assertNotNull(result)
        assertEquals(0.85, result?.get("score")?.asDouble)
        assertEquals("high", result?.get("level")?.asString)
    }

    @Test
    fun `suspendAgent posts to lifecycle action with correct body`() = runTest {
        val responseBody = JsonObject().apply {
            addProperty("id", "agent-1")
            addProperty("status", "suspended")
        }
        server.enqueue(MockResponse().setBody(gson.toJson(responseBody)).setResponseCode(200))

        val result = client.suspendAgent("agent-1", "policy violation")

        assertNotNull(result)
        assertEquals("suspended", result?.get("status")?.asString)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/agents/agent-1/suspend", request.path)
        val body = gson.fromJson(request.body.readUtf8(), JsonObject::class.java)
        assertEquals("suspended", body.get("status").asString)
        assertEquals("policy violation", body.get("reason").asString)
    }

    @Test
    fun `exportAudit creates polls and downloads native export`() = runTest {
        val csvContent = "id,type,timestamp\nreceipt-1,code,2026-01-01"
        server.enqueue(MockResponse().setBody("""{"id":"job-1","status":"PENDING"}"""))
        server.enqueue(MockResponse().setBody("""{"id":"job-1","status":"COMPLETED"}"""))
        server.enqueue(MockResponse().setBody(csvContent).setResponseCode(200))

        val result = client.exportAudit("2026-01-01", "2026-01-31", "csv")

        assertNotNull(result)
        assertTrue(result!!.contains("receipt-1"))

        val request = server.takeRequest()
        assertEquals("/audit/exports", request.path)
        assertEquals("POST", request.method)
        val body = gson.fromJson(request.body.readUtf8(), JsonObject::class.java)
        assertEquals("2026-01-01T00:00:00Z", body.get("from_ts").asString)
        assertEquals("2026-02-01T00:00:00Z", body.get("to_ts").asString)
        assertEquals("CSV", body.get("format").asString)
        assertEquals("/audit/exports/job-1", server.takeRequest().path)
        assertEquals("/audit/exports/job-1/download", server.takeRequest().path)
    }

    @Test
    fun `config validation detects missing fields`() {
        val valid = MAIPConfig(
            apiUrl = "https://api.example.com",
            apiKey = "key123",
            tenantId = "tenant-1",
            agentId = "agent-1"
        )
        assertTrue(valid.isValid)

        val noUrl = valid.copy(apiUrl = "")
        assertFalse(noUrl.isValid)

        val noKey = valid.copy(apiKey = "")
        assertFalse(noKey.isValid)

        val noTenant = valid.copy(tenantId = "")
        assertTrue(noTenant.isValid)
    }
    @Test
    fun `write failure is never automatically replayed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        assertNull(client.createReceipt("code", "qa", "abc", "maip:qa:agent"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `status without signature verification is not valid`() = runTest {
        server.enqueue(MockResponse().setBody("""{"valid":true,"verdict":"VALID","status":"active"}"""))
        assertFalse(client.verifyReceipt("maip-receipt:qa")!!.get("valid").asBoolean)
    }

    @Test
    fun `credentialed requests never follow redirects`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/other")))
        server.enqueue(MockResponse().setBody("{}"))
        assertNull(client.getAgent("maip:qa:agent"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `export refuses corrupted artifact`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"job-1","status":"COMPLETED","sha256":"wrong"}"""))
        server.enqueue(MockResponse().setBody("[]"))
        var failed = false
        try { client.exportAudit("2026-01-01", "2026-01-31") } catch (e: java.io.IOException) { failed = true }
        assertTrue(failed)
    }

    @Test
    fun `native array agent list is accepted`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"agent_id":"maip:qa:one"}]"""))
        assertEquals("maip:qa:one", client.listAgents().single().get("agent_id").asString)
    }

    @Test
    fun `build event does not claim a timestamp hash is an artifact digest`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        client.createReceipt("build", "build complete", "", "maip:qa:one", mapOf("build_id" to "qa-build"))
        val metadata = gson.fromJson(server.takeRequest().body.readUtf8(), JsonObject::class.java).getAsJsonObject("metadata")
        assertFalse(metadata.has("artifact_hash"))
        assertEquals("qa-build", metadata.get("build_id").asString)
    }

}
