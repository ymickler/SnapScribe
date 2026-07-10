package com.example.engine

import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperEngineImpl : STTEngine {
    override suspend fun transcribe(
        context: android.content.Context,
        audioFile: File,
        modelPath: String,
        onProgress: (Float) -> Unit,
        onPartial: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        
        onProgress(0.1f)
        onPartial("...") 
        
        var fullText = ""
        var whisperModel: dev.ffmpegkit.whisper.WhisperModel? = null
        try {
            // Whisper.loadModel(context, modelPath, ...) is suspend
            whisperModel = Whisper.loadModel(context, modelPath)
            
            // Assuming default config if we pass null or empty config
            val config = WhisperConfig()
            
            // Whisper.transcribe(model, audioPath, config) is suspend
            val result = Whisper.transcribe(whisperModel, audioFile.absolutePath, config)
            
            fullText = result.text ?: ""
            
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Whisper transcription failed: ${e.message}")
        } finally {
            if (whisperModel != null) {
                Whisper.releaseModel(whisperModel)
            }
        }
        
        onProgress(1.0f)
        return@withContext fullText.trim()
    }
}
