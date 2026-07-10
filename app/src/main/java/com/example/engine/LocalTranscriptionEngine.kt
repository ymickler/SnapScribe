package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class LocalTranscriptionEngine(
    private val context: Context,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {

    private val tag = "LocalTranscriptionEngine"
    private val downloader = ModelDownloader(context)
    private val converter = AudioConverter()
    private val settingsManager = SettingsManager(context)

    interface TranscriptionCallback {
        fun onStart()
        fun onProgress(progress: Float)
        fun onPartialResult(text: String)
        fun onComplete(fullText: String)
        fun onError(error: String)
    }

    fun transcribeAudio(
        audioUri: Uri,
        callback: TranscriptionCallback
    ) {
        CoroutineScope(dispatcher).launch {
            var convertedWavFile: File? = null
            try {
                callback.onStart()

                val engineType = settingsManager.sttEngine
                val langCode = settingsManager.getTargetLanguageCode()

                val modelType = when {
                    engineType == "whisper" -> ModelDownloader.ModelType.WHISPER_TINY
                    langCode == "de" -> ModelDownloader.ModelType.VOSK_DE
                    else -> ModelDownloader.ModelType.VOSK_EN
                }

                if (!downloader.isModelDownloaded(modelType)) {
                    callback.onError("Model not downloaded for $engineType ($langCode). Please download it in settings.")
                    return@launch
                }
                
                val modelPath = downloader.getModelPath(modelType)

                // 1. Copy URI to a temporary file
                val tempInput = File(context.cacheDir, "input_audio_${System.currentTimeMillis()}.tmp")
                context.contentResolver.openInputStream(audioUri)?.use { input ->
                    FileOutputStream(tempInput).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    callback.onError("Failed to read audio file")
                    return@launch
                }

                // 2. Convert to 16kHz WAV
                val wavPath = converter.convertToWav(tempInput.absolutePath, context.cacheDir)
                tempInput.delete()
                
                convertedWavFile = File(wavPath)

                // 3. Transcribe
                val engine: STTEngine = if (engineType == "whisper") WhisperEngineImpl() else VoskEngineImpl()
                
                val fullText = engine.transcribe(
                    context = context,
                    audioFile = convertedWavFile,
                    modelPath = modelPath,
                    onProgress = { progress -> callback.onProgress(progress) },
                    onPartial = { partial -> callback.onPartialResult(partial) }
                )
                
                callback.onComplete(fullText)

            } catch (e: Exception) {
                Log.e(tag, "Error transcribing audio file", e)
                callback.onError(e.message ?: "Unknown offline transcription error")
            } finally {
                convertedWavFile?.delete()
            }
        }
    }
}
