package com.example.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.DependencyProvider
import com.example.data.Localization

class CopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val text = intent?.getStringExtra("text_to_copy") ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcription", text)
        clipboard.setPrimaryClip(clip)

        val settings = DependencyProvider.getSettingsManager(context)
        val uiLanguage = settings.uiLanguage
        val toastMsg = Localization.getString("toast_copied", uiLanguage)
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
    }
}
