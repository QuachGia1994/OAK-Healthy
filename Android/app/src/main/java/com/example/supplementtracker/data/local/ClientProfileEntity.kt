package com.example.supplementtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "client_profiles")
data class ClientProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarColorArgb: Int,
    val createdAt: Long = System.currentTimeMillis()
)

