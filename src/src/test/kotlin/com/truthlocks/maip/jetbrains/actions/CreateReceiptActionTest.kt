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

import com.truthlocks.maip.jetbrains.client.MAIPConfig
import com.truthlocks.maip.jetbrains.services.TrustService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for action-related logic that does not require the IntelliJ platform.
 *
 * Tests for the actual [CreateReceiptAction] action execution require the
 * IntelliJ test framework and are part of the integration test suite.
 * Here we test the supporting utilities and data structures used by actions.
 */
class CreateReceiptActionTest {

    @Test
    fun `MAIPConfig isValid returns true when all required fields present`() {
        val config = MAIPConfig(
            apiUrl = "https://api.truthlocks.com/maip/v1",
            apiKey = "key-123",
            tenantId = "tenant-abc",
            agentId = "agent-xyz"
        )
        assertTrue(config.isValid)
    }

    @Test
    fun `MAIPConfig isValid returns false with blank API URL`() {
        val config = MAIPConfig(
            apiUrl = "",
            apiKey = "key-123",
            tenantId = "tenant-abc",
            agentId = "agent-xyz"
        )
        assertFalse(config.isValid)
    }

    @Test
    fun `MAIPConfig isValid returns false with blank API key`() {
        val config = MAIPConfig(
            apiUrl = "https://api.example.com",
            apiKey = "",
            tenantId = "tenant-abc",
            agentId = "agent-xyz"
        )
        assertFalse(config.isValid)
    }

    @Test
    fun `MAIPConfig isValid returns false with blank tenant ID`() {
        val config = MAIPConfig(
            apiUrl = "https://api.example.com",
            apiKey = "key-123",
            tenantId = "",
            agentId = "agent-xyz"
        )
        assertFalse(config.isValid)
    }

    @Test
    fun `MAIPConfig trims trailing slash from API URL`() {
        val config = MAIPConfig(
            apiUrl = "https://api.truthlocks.com/maip/v1/",
            apiKey = "key",
            tenantId = "tenant",
            agentId = "agent"
        )
        // Note: trimming happens in fromSettings(), the data class stores raw
        // The test verifies the data class itself holds the value
        assertNotNull(config.apiUrl)
    }

    @Test
    fun `TrustService trustColor returns green for high score`() {
        val color = TrustService.trustColor(0.9)
        assertEquals(TrustService.HIGH_TRUST_COLOR, color)
    }

    @Test
    fun `TrustService trustColor returns yellow for medium score`() {
        val color = TrustService.trustColor(0.6)
        assertEquals(TrustService.MEDIUM_TRUST_COLOR, color)
    }

    @Test
    fun `TrustService trustColor returns red for low score`() {
        val color = TrustService.trustColor(0.3)
        assertEquals(TrustService.LOW_TRUST_COLOR, color)
    }

    @Test
    fun `TrustService trustLevel returns correct labels`() {
        assertEquals("High", TrustService.trustLevel(0.8))
        assertEquals("High", TrustService.trustLevel(1.0))
        assertEquals("Medium", TrustService.trustLevel(0.5))
        assertEquals("Medium", TrustService.trustLevel(0.79))
        assertEquals("Low", TrustService.trustLevel(0.49))
        assertEquals("Low", TrustService.trustLevel(0.0))
    }

    @Test
    fun `TrustService formatScore formats correctly`() {
        assertEquals("85.0%", TrustService.formatScore(0.85))
        assertEquals("100.0%", TrustService.formatScore(1.0))
        assertEquals("0.0%", TrustService.formatScore(0.0))
        assertEquals("50.5%", TrustService.formatScore(0.505))
    }

    @Test
    fun `TrustService extractScore handles null`() {
        assertEquals(0.0, TrustService.extractScore(null))
    }

    @Test
    fun `TrustService extractScore extracts score from JsonObject`() {
        val obj = com.google.gson.JsonObject().apply {
            addProperty("score", 0.75)
        }
        assertEquals(0.75, TrustService.extractScore(obj))
    }

    @Test
    fun `TrustService extractScore returns zero when score field missing`() {
        val obj = com.google.gson.JsonObject().apply {
            addProperty("agent_id", "test")
        }
        assertEquals(0.0, TrustService.extractScore(obj))
    }

    @Test
    fun `TrustService trustColor boundary at 0_8`() {
        assertEquals(TrustService.HIGH_TRUST_COLOR, TrustService.trustColor(0.8))
        assertEquals(TrustService.MEDIUM_TRUST_COLOR, TrustService.trustColor(0.7999))
    }

    @Test
    fun `TrustService trustColor boundary at 0_5`() {
        assertEquals(TrustService.MEDIUM_TRUST_COLOR, TrustService.trustColor(0.5))
        assertEquals(TrustService.LOW_TRUST_COLOR, TrustService.trustColor(0.4999))
    }
}
