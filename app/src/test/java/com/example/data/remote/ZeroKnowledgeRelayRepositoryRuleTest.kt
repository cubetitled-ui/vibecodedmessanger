package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ZeroKnowledgeRelayRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun registerUserNode_andPublishEnvelope_authenticatedOwnerSucceeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = ZeroKnowledgeRelayRepository(firestore, auth)

        val regResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerOrUpdateUserNode(
                handle = "@alice_stealth",
                phoneHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                publicKeyHex = "8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f",
                activeCipherId = "AEGIS-52-CASCADE",
                customServerUrl = "wss://relay.aegis52.local:8443"
            )
        }
        assertTrue(regResult.isSuccess)
        assertEquals(aliceUid, regResult.getOrThrow())

        val convId = "conv_${UUID.randomUUID()}"
        val envResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.publishEncryptedEnvelope(
                conversationId = convId,
                senderHandle = "@alice_stealth",
                cipherSuite = "AEGIS-52-CASCADE",
                ephemeralPubKey = "a1b2c3d4e5f60718293a4b5c6d7e8f90",
                nonceHex = "00112233445566778899aabb",
                ciphertextHex = "9f8e7d6c5b4a39281706f5e4d3c2b1a0",
                macTagHex = "11223344556677889900aabbccddeeff",
                transportRoute = "LAN_P2P"
            )
        }
        assertTrue(envResult.isSuccess)
        val envId = envResult.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeEnvelopes(aliceUid).first { list -> list.any { it.id == envId } }
        }
        assertTrue(observed.any { it.id == envId && it.conversationId == convId })
    }

    @Test
    fun getEnvelopeById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ZeroKnowledgeRelayRepository(firestore, auth)
        val envId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.publishEncryptedEnvelope(
                conversationId = "conv_cross",
                senderHandle = "@alice_stealth",
                cipherSuite = "AEGIS-52-CASCADE",
                ephemeralPubKey = "a1b2c3d4e5f60718293a4b5c6d7e8f90",
                nonceHex = "00112233445566778899aabb",
                ciphertextHex = "9f8e7d6c5b4a39281706f5e4d3c2b1a0",
                macTagHex = "11223344556677889900aabbccddeeff",
                transportRoute = "CLOUD_RELAY"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ZeroKnowledgeRelayRepository(firestore, auth)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getEnvelopeById(aliceUid, envId)
        }
        assertTrue(crossResult.isFailure)
        val ex = crossResult.exceptionOrNull() as? FirebaseFirestoreException
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeEnvelopes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ZeroKnowledgeRelayRepository(firestore, auth)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeEnvelopes("unauthenticated_probe").first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@aegis52.test"
        const val BOB_EMAIL = "bob@aegis52.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
