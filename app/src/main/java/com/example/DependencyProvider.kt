package com.example

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.SettingsManager
import com.example.data.TranscriptionRepository

object DependencyProvider {
    private var database: AppDatabase? = null
    private var repository: TranscriptionRepository? = null
    private var settingsManager: SettingsManager? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val db = AppDatabase.getDatabase(context)
            database = db
            db
        }
    }

    fun getRepository(context: Context): TranscriptionRepository {
        return repository ?: synchronized(this) {
            val repo = TranscriptionRepository(getDatabase(context).transcriptionDao())
            repository = repo
            repo
        }
    }

    fun getSettingsManager(context: Context): SettingsManager {
        return settingsManager ?: synchronized(this) {
            val sm = SettingsManager(context.applicationContext)
            settingsManager = sm
            sm
        }
    }

    fun setTestInstances(db: AppDatabase?, repo: TranscriptionRepository?, sm: SettingsManager?) {
        database = db
        repository = repo
        settingsManager = sm
    }
}
