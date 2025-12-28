package com.aiautomation.data.repository

import com.aiautomation.data.local.TaskDao
import com.aiautomation.data.model.Task
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()
    
    suspend fun getTaskById(taskId: Long): Task? = taskDao.getTaskById(taskId)
    
    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)
    
    suspend fun updateTask(task: Task) = taskDao.updateTask(task)
    
    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
}
