package com.example

import com.example.crypto.AegisCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun aegis52_encryptAndDecrypt_roundtripSucceedsAndDetectsTampering() {
        val secret = "8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f"
        val original = "Секретное сообщение по LAN P2P через каскад Aegis-52!"

        val sealed = AegisCryptoEngine.sealAegis52(original, secret)
        assertEquals(256, sealed.paddedBytesSize)

        val opened = AegisCryptoEngine.openAegis52(sealed, secret)
        assertTrue(opened.isSuccess)
        assertEquals(original, opened.getOrThrow())

        // Verify tampering with HMAC-SHA512 tag is rejected
        val tampered = sealed.copy(macTagHex = "00".repeat(32))
        val tamperedResult = AegisCryptoEngine.openAegis52(tampered, secret)
        assertTrue(tamperedResult.isFailure)
    }

    @Test
    fun cryptoLab_evaluatesAll52Ideas_andSelectsAegis52AsWinner() {
        val results = AegisCryptoEngine.runLiveHardwareBenchmarkOnAll52()
        assertEquals(52, results.size)
        val winner = results.first()
        assertTrue(winner.isWinner)
        assertEquals("AEGIS-52-CASCADE", winner.id)
    }
}
