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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [ReceiptService] utility methods.
 *
 * Note: Full integration tests of receipt CRUD require the IntelliJ test
 * framework and are run as part of the plugin's integration test suite.
 * These tests cover the pure utility functions that do not depend on
 * the IntelliJ platform.
 */
class ReceiptServiceTest {

    @Test
    fun `sha256Hex computes correct hash for known input`() {
        val input = "hello world".toByteArray(Charsets.UTF_8)
        val hash = ReceiptService.sha256Hex(input)

        assertEquals(
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            hash
        )
    }

    @Test
    fun `sha256Hex produces 64-character hex string`() {
        val input = "test data for hashing".toByteArray(Charsets.UTF_8)
        val hash = ReceiptService.sha256Hex(input)

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `sha256Hex returns different hashes for different inputs`() {
        val hash1 = ReceiptService.sha256Hex("file1.kt".toByteArray())
        val hash2 = ReceiptService.sha256Hex("file2.kt".toByteArray())

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `sha256Hex returns same hash for same input`() {
        val input = "deterministic input".toByteArray()
        val hash1 = ReceiptService.sha256Hex(input)
        val hash2 = ReceiptService.sha256Hex(input)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `sha256Hex handles empty input`() {
        val hash = ReceiptService.sha256Hex(ByteArray(0))

        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hash
        )
    }

    @Test
    fun `sha256Hex handles binary data`() {
        val binaryData = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())
        val hash = ReceiptService.sha256Hex(binaryData)

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `sha256Hex handles large input`() {
        val largeInput = ByteArray(1_000_000) { (it % 256).toByte() }
        val hash = ReceiptService.sha256Hex(largeInput)

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `sha256Hex handles UTF-8 multibyte characters`() {
        val unicodeInput = "Hello \u4e16\u754c \uD83D\uDE00".toByteArray(Charsets.UTF_8)
        val hash = ReceiptService.sha256Hex(unicodeInput)

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }
}
