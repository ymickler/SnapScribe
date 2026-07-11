package com.example.engine

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

class VoskEngineImpl : STTEngine {
    override suspend fun transcribe(
        context: android.content.Context,
        audioFile: File,
        modelPath: String,
        onProgress: (Float) -> Unit,
        onPartial: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val model = Model(modelPath)
        // Ensure 16000Hz as the audio was converted to 16kHz
        val recognizer = Recognizer(model, 16000f)
        
        var fullText = ""
        FileInputStream(audioFile).use { fis ->
            // Skip WAV header (44 bytes standard)
            fis.skip(44)
            val totalBytes = audioFile.length() - 44
            var bytesReadTotal = 0L
            val buffer = ByteArray(4096)
            var bytesRead = 0

            while (fis.read(buffer).also { bytesRead = it } >= 0) {
                if (!this@withContext.isActive) {
                    break
                }
                bytesReadTotal += bytesRead
                if (totalBytes > 0) {
                    onProgress(bytesReadTotal.toFloat() / totalBytes.toFloat())
                }

                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    val res = recognizer.result
                    val text = JSONObject(res).optString("text", "")
                    if (text.isNotBlank()) {
                        fullText += "$text "
                        onPartial(fullText.trim())
                    }
                } else {
                    val partial = recognizer.partialResult
                    val pText = JSONObject(partial).optString("partial", "")
                    if (pText.isNotBlank()) {
                        onPartial("$fullText $pText".trim())
                    }
                }
            }
        }
        
        val finalRes = recognizer.finalResult
        val text = JSONObject(finalRes).optString("text", "")
        if (text.isNotBlank()) {
            fullText += "$text "
        }
        
        recognizer.close()
        model.close()
        
        return@withContext fullText.trim()
    }
}
