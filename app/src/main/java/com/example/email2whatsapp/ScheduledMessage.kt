package com.example.email2whatsapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipient: String,
    val message: String,
    val sendTime: Long,
    val askBeforeSending: Boolean
)
