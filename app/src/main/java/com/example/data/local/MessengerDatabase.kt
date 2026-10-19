package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val handle: String,
    val kind: String, // "DM", "GROUP", "CHANNEL", "SECRET"
    val folderTag: String, // "PERSONAL", "GROUPS", "CHANNELS", "LAN", "SECRET"
    val lastMessagePreview: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0,
    val pinnedMessageText: String? = null,
    val peerIpAddress: String? = null,
    val peerPort: Int = 45889,
    val peerPublicKeyHex: String,
    val selfDestructSeconds: Int = 0, // 0 = off, >0 = auto-delete timer
    val memberCount: Int = 2,
    val isVerifiedNode: Boolean = true
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderName: String,
    val senderHandle: String,
    val isOutgoing: Boolean,
    val plaintext: String,
    val ciphertextHex: String,
    val nonceHex: String,
    val macTagHex: String,
    val ephemeralPubKey: String,
    val cipherSuite: String,
    val transportRoute: String, // "LAN_P2P", "CUSTOM_SERVER", "CLOUD_RELAY"
    val latencyMs: Long = 1L,
    val replyToId: String? = null,
    val replyPreview: String? = null,
    val reactionsCsv: String = "",
    val attachmentType: String? = null, // "VOICE", "FILE", null
    val attachmentMeta: String? = null,
    val expiresAt: Long? = null,
    val viewsCount: Int = 1,
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "lan_peers")
data class LanPeerEntity(
    @PrimaryKey val peerId: String,
    val nodeName: String,
    val handle: String,
    val ipAddress: String,
    val port: Int,
    val publicKeyFingerprint: String,
    val cipherSuite: String,
    val lastSeen: Long,
    val rttMs: Long,
    val isDirectReachable: Boolean = true
)

@Entity(tableName = "decoy_notes")
data class DecoyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val category: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface MessengerDao {
    @Query("SELECT * FROM conversations ORDER BY lastMessageTime DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purgeExpiredMessages(now: Long): Int

    @Query("SELECT * FROM lan_peers ORDER BY lastSeen DESC")
    fun observeLanPeers(): Flow<List<LanPeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLanPeer(peer: LanPeerEntity)

    @Query("SELECT * FROM decoy_notes ORDER BY updatedAt DESC")
    fun observeDecoyNotes(): Flow<List<DecoyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDecoyNote(note: DecoyNoteEntity)

    @Query("DELETE FROM decoy_notes WHERE id = :noteId")
    suspend fun deleteDecoyNote(noteId: Int)
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        LanPeerEntity::class,
        DecoyNoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MessengerDatabase : RoomDatabase() {
    abstract fun dao(): MessengerDao

    companion object {
        @Volatile
        private var INSTANCE: MessengerDatabase? = null

        fun getInstance(context: Context): MessengerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessengerDatabase::class.java,
                    "aegis52_stealth_vault.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
