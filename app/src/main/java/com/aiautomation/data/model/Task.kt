package com.aiautomation.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: TaskStatus = TaskStatus.PENDING
)

enum class TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
