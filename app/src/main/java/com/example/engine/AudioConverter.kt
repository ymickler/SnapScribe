package com.example.engine

import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioConverter {

    /**
     * Converts the given audio file to 16kHz, mono, 16-bit PCM WAV.
     * @return The absolute path to the converted WAV file.
     */
    suspend fun convertToWav(inputPath: String, outputDir: File): String {
        return withContext(Dispatchers.IO) {
            val outputFile = File(outputDir, "converted_audio_${System.currentTimeMillis()}.wav")
            
            // Standard ffmpeg arguments to convert audio to 16kHz mono pcm_s16le
            val command = "-y -i \"$inputPath\" -ar 16000 -ac 1 -c:a pcm_s16le \"${outputFile.absolutePath}\""
            
            val session = FFmpegKit.execute(command)
            if (ReturnCode.isSuccess(session.returnCode)) {
                outputFile.absolutePath
            } else {
                val logs = session.allLogsAsString
                throw Exception("Audio conversion failed with state ${session.state} and rc ${session.returnCode}. Logs: $logs")
            }
        }
    }
}
