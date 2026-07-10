package com.example.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class ModelDownloader(private val context: Context) {

    enum class ModelType(val engine: String, val language: String, val url: String, val isZip: Boolean, val folderName: String, val sizeLabel: String) {
        VOSK_DE("vosk", "de", "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip", true, "vosk-model-small-de-0.15", "~40 MB"),
        VOSK_EN("vosk", "en", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", true, "vosk-model-small-en-us-0.15", "~40 MB"),
        WHISPER_TINY("whisper", "tiny", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin", false, "ggml-tiny.bin", "~75 MB"),
        WHISPER_BASE("whisper", "base", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin", false, "ggml-base.bin", "~140 MB"),
        WHISPER_SMALL("whisper", "small", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin", false, "ggml-small.bin", "~460 MB")
    }

    private val client = OkHttpClient()
    private val modelsDir = File(context.filesDir, "models")

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    fun isModelDownloaded(modelType: ModelType): Boolean {
        val modelFileOrDir = File(modelsDir, modelType.folderName)
        return modelFileOrDir.exists() && (modelFileOrDir.isFile || (modelFileOrDir.isDirectory && modelFileOrDir.list()?.isNotEmpty() == true))
    }

    fun getModelPath(modelType: ModelType): String {
        return File(modelsDir, modelType.folderName).absolutePath
    }

    fun deleteModel(modelType: ModelType) {
        val modelFileOrDir = File(modelsDir, modelType.folderName)
        if (modelFileOrDir.exists()) {
            modelFileOrDir.deleteRecursively()
        }
    }

    suspend fun downloadModel(modelType: ModelType, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(modelType.url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Failed to download model: ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()

            if (modelType.isZip) {
                extractZip(inputStream, totalBytes, onProgress, modelType.folderName)
            } else {
                saveFile(inputStream, totalBytes, onProgress, modelType.folderName)
            }
        }
    }

    private fun saveFile(inputStream: InputStream, totalBytes: Long, onProgress: (Float) -> Unit, fileName: String) {
        val destFile = File(modelsDir, fileName)
        val tmpFile = File(modelsDir, "$fileName.tmp")
        
        var bytesCopied: Long = 0
        val buffer = ByteArray(8 * 1024)
        var bytes = inputStream.read(buffer)

        FileOutputStream(tmpFile).use { fos ->
            while (bytes >= 0) {
                fos.write(buffer, 0, bytes)
                bytesCopied += bytes
                if (totalBytes > 0) {
                    onProgress(bytesCopied.toFloat() / totalBytes)
                }
                bytes = inputStream.read(buffer)
            }
        }
        
        if (destFile.exists()) destFile.delete()
        tmpFile.renameTo(destFile)
    }

    private fun extractZip(inputStream: InputStream, totalBytes: Long, onProgress: (Float) -> Unit, expectedFolderName: String) {
        val tmpExtractDir = File(modelsDir, "tmp_extract")
        if (tmpExtractDir.exists()) tmpExtractDir.deleteRecursively()
        tmpExtractDir.mkdirs()

        // We can't perfectly track zip download progress if we are unpacking on the fly,
        // because totalBytes is compressed size. We'll simulate fake progress or ignore.
        
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(tmpExtractDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        val buffer = ByteArray(8 * 1024)
                        var len = zis.read(buffer)
                        while (len > 0) {
                            fos.write(buffer, 0, len)
                            len = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Move to final folder
        val extractedFolders = tmpExtractDir.listFiles() ?: emptyArray()
        if (extractedFolders.size == 1 && extractedFolders[0].isDirectory) {
            // Usually the zip contains a single root folder with the same name.
            val targetDir = File(modelsDir, expectedFolderName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            extractedFolders[0].renameTo(targetDir)
        } else {
            // Multiple files at root
            val targetDir = File(modelsDir, expectedFolderName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            tmpExtractDir.renameTo(targetDir)
        }
        
        if (tmpExtractDir.exists()) tmpExtractDir.deleteRecursively()
        onProgress(1f)
    }
}
