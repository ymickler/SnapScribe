package com.example.engine

import java.io.File

interface STTEngine {
    suspend fun transcribe(
        context: android.content.Context,
        audioFile: File,
        modelPath: String,
        onProgress: (Float) -> Unit,
        onPartial: (String) -> Unit
    ): String
}
