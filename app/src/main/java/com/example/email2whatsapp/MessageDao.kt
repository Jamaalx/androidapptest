package com.example.email2whatsapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: ScheduledMessage): Long
    
    @Update
    suspend fun update(message: ScheduledMessage)
    
    @Delete
    suspend fun delete(message: ScheduledMessage)
    
    @Query("SELECT * FROM ScheduledMessage ORDER BY sendTime ASC")
    suspend fun getAllMessages(): List<ScheduledMessage>
    
    @Query("SELECT * FROM ScheduledMessage WHERE id = :messageId")
    suspend fun getMessageById(messageId: Int): ScheduledMessage
    
    @Query("SELECT * FROM ScheduledMessage WHERE sendTime <= :currentTime")
    suspend fun getMessagesToSend(currentTime: Long): List<ScheduledMessage>
}
