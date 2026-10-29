package com.ovehbe.junkboy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Summary data for grouped conversations
 */
data class ConversationSummary(
    val appName: String,
    val packageName: String,
    val senderName: String,
    val lastMessageDate: Date,
    val messageCount: Int,
    val unreadCount: Int,
    val category: MessageCategory
)

@Dao
interface ChatMessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(chatMessage: ChatMessage): Long
    
    @Update
    suspend fun updateChatMessage(chatMessage: ChatMessage)
    
    @Query("SELECT * FROM chat_messages ORDER BY receivedAt DESC")
    fun getAllChatMessages(): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages ORDER BY receivedAt DESC LIMIT :limit")
    fun getAllChatMessagesLimited(limit: Int): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE category = :category ORDER BY receivedAt DESC")
    fun getChatMessagesByCategory(category: MessageCategory): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE category = :category ORDER BY receivedAt DESC LIMIT :limit")
    fun getChatMessagesByCategoryLimited(category: MessageCategory, limit: Int): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE appName = :appName ORDER BY receivedAt DESC")
    fun getChatMessagesByApp(appName: String): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE appName = :appName ORDER BY receivedAt DESC LIMIT :limit")
    fun getChatMessagesByAppLimited(appName: String, limit: Int): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE messageContent LIKE '%' || :searchQuery || '%' OR senderName LIKE '%' || :searchQuery || '%' ORDER BY receivedAt DESC")
    fun searchChatMessages(searchQuery: String): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE messageContent LIKE '%' || :searchQuery || '%' OR senderName LIKE '%' || :searchQuery || '%' ORDER BY receivedAt DESC LIMIT :limit")
    fun searchChatMessagesLimited(searchQuery: String, limit: Int): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE isRead = 0 ORDER BY receivedAt DESC")
    fun getUnreadChatMessages(): Flow<List<ChatMessage>>
    
    @Query("UPDATE chat_messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: Long)
    
    @Query("UPDATE chat_messages SET isRead = 1 WHERE appName = :appName")
    suspend fun markAllAsReadByApp(appName: String)
    
    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteChatMessage(messageId: Long)
    
    @Query("DELETE FROM chat_messages WHERE appName = :appName")
    suspend fun deleteChatMessagesByApp(appName: String)
    
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllChatMessages()

    @Query("DELETE FROM chat_messages WHERE receivedAt < :before")
    suspend fun deleteChatMessagesOlderThan(before: Date): Int
    
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getChatMessageCount(): Int
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE category = :category")
    suspend fun getChatMessageCountByCategory(category: MessageCategory): Int
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE appName = :appName")
    suspend fun getChatMessageCountByApp(appName: String): Int
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE isRead = 0")
    suspend fun getUnreadChatMessageCount(): Int
    
    /**
     * Get grouped conversations for Hub display
     * Groups by app and sender, showing latest message and counts
     */
    @Query("""
        SELECT 
            appName,
            packageName,
            senderName,
            MAX(receivedAt) as lastMessageDate,
            COUNT(*) as messageCount,
            SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END) as unreadCount,
            category
        FROM chat_messages 
        GROUP BY appName, senderName 
        ORDER BY lastMessageDate DESC
    """)
    suspend fun getGroupedConversations(): List<ConversationSummary>
    
    /**
     * Get the latest message content for a specific conversation
     */
    @Query("""
        SELECT messageContent 
        FROM chat_messages 
        WHERE appName = :appName AND senderName = :senderName 
        ORDER BY receivedAt DESC 
        LIMIT 1
    """)
    suspend fun getLatestMessageContent(appName: String, senderName: String): String?
    
    /**
     * Get all messages for a specific conversation
     */
    @Query("""
        SELECT * FROM chat_messages 
        WHERE appName = :appName AND senderName = :senderName 
        ORDER BY receivedAt DESC
    """)
    suspend fun getConversationMessages(appName: String, senderName: String): List<ChatMessage>
} 