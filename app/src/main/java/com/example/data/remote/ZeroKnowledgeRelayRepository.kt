package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class UserNode(
    @DocumentId val id: String = "",
    val userId: String = "",
    val handle: String = "",
    val phoneHash: String = "",
    val publicKeyHex: String = "",
    val activeCipherId: String = "AEGIS-52-CASCADE",
    val customServerUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedEnvelope(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val senderHandle: String = "",
    val cipherSuite: String = "AEGIS-52-CASCADE",
    val ephemeralPubKey: String = "",
    val nonceHex: String = "",
    val ciphertextHex: String = "",
    val macTagHex: String = "",
    val transportRoute: String = "CLOUD_RELAY",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class ZeroKnowledgeRelayRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Zero-Knowledge Relay.")
    }

    suspend fun registerOrUpdateUserNode(
        handle: String,
        phoneHash: String,
        publicKeyHex: String,
        activeCipherId: String,
        customServerUrl: String?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val existingSnap = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }

        if (existingSnap != null && existingSnap.exists()) {
            val updatePayload = mutableMapOf<String, Any>(
                "handle" to handle,
                "phoneHash" to phoneHash,
                "publicKeyHex" to publicKeyHex,
                "activeCipherId" to activeCipherId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!customServerUrl.isNullOrBlank()) {
                updatePayload["customServerUrl"] = customServerUrl
            }
            try {
                docRef.update(updatePayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        } else {
            val createPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "handle" to handle,
                "phoneHash" to phoneHash,
                "publicKeyHex" to publicKeyHex,
                "activeCipherId" to activeCipherId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!customServerUrl.isNullOrBlank()) {
                createPayload["customServerUrl"] = customServerUrl
            }
            try {
                docRef.set(createPayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        }
        uid
    }

    suspend fun getUserNodeById(targetUserId: String): Result<UserNode?> = runCatching {
        val docRef = db.collection("users").document(targetUserId)
        try {
            val snapshot = docRef.get().await()
            snapshot.toObject(UserNode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun publishEncryptedEnvelope(
        conversationId: String,
        senderHandle: String,
        cipherSuite: String,
        ephemeralPubKey: String,
        nonceHex: String,
        ciphertextHex: String,
        macTagHex: String,
        transportRoute: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("envelopes")
        val payload = mapOf(
            "userId" to uid,
            "conversationId" to conversationId,
            "senderHandle" to senderHandle,
            "cipherSuite" to cipherSuite,
            "ephemeralPubKey" to ephemeralPubKey,
            "nonceHex" to nonceHex,
            "ciphertextHex" to ciphertextHex,
            "macTagHex" to macTagHex,
            "transportRoute" to transportRoute,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val docRef = colRef.add(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun getUserEnvelopes(): Result<List<EncryptedEnvelope>> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("envelopes")
        try {
            val snapshot = colRef.whereEqualTo("userId", uid).get().await()
            snapshot.toObjects(EncryptedEnvelope::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun getEnvelopeById(ownerUserId: String, envelopeId: String): Result<EncryptedEnvelope?> = runCatching {
        val docRef = db.collection("users").document(ownerUserId).collection("envelopes").document(envelopeId)
        try {
            val snapshot = docRef.get().await()
            snapshot.toObject(EncryptedEnvelope::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    fun observeEnvelopes(userId: String? = auth.currentUser?.uid): Flow<List<EncryptedEnvelope>> =
        callbackFlow {
            val uid = userId ?: auth.currentUser?.uid ?: "unauthenticated_probe"
            val path = "users/$uid/envelopes"
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            val registration = db.collection("users").document(uid).collection("envelopes")
                .whereEqualTo("userId", uid)
                .addSnapshotListener(executor) { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.LIST, path)
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val items = snapshot.toObjects(
                            EncryptedEnvelope::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        trySend(items)
                    }
                }
            awaitClose {
                registration.remove()
                executor.shutdown()
            }
        }
}
