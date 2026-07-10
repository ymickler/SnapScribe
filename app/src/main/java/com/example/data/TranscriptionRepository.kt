package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TranscriptionRepository(private val dao: TranscriptionDao) {

    val allTranscriptions: Flow<List<TranscriptionEntity>> = dao.getAllTranscriptions().map { list ->
        list.map { entity ->
            entity.copy(transcribedText = CryptoHelper.decrypt(entity.transcribedText))
        }
    }

    fun search(query: String): Flow<List<TranscriptionEntity>> {
        return allTranscriptions.map { list ->
            list.filter { entity ->
                entity.transcribedText.contains(query, ignoreCase = true)
            }
        }
    }

    suspend fun insert(transcription: TranscriptionEntity): Long {
        val encryptedEntity = transcription.copy(
            transcribedText = CryptoHelper.encrypt(transcription.transcribedText)
        )
        return dao.insertTranscription(encryptedEntity)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteTranscriptionById(id)
    }

    suspend fun clear() {
        dao.clearAll()
    }
}
