package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.DependencyProvider
import com.example.data.TranscriptionEntity
import com.example.engine.LocalTranscriptionEngine
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SleekBackground
import com.example.ui.theme.SleekInnerSurface
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.SleekSurface
import com.example.ui.theme.SleekText
import com.example.ui.theme.SleekButtonText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TranscriptionOverlayService : Service() {

    companion object {
        const val EXTRA_AUDIO_URI = "extra_audio_uri"
        private const val NOTIFICATION_ID = 8888
        private const val CHANNEL_ID = "snapscripe_transcription_channel"
    }

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null

    // Dedicated Compose lifecycle owner
    private val lifecycleOwner = MyLifecycleOwner()

    // Mutable states observed by Compose
    private var transcriptionStatus by mutableStateOf("Initializing...")
    private var transcriptionProgress by mutableStateOf(0.0f)
    private var transcribedText by mutableStateOf("")
    private var isCompleted by mutableStateOf(false)
    private var isError by mutableStateOf(false)
    private var errorMsg by mutableStateOf("")
    private var showCancelConfirmation by mutableStateOf(false)
    private var currentAudioUriString: String = ""

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val audioUriString = intent?.getStringExtra(EXTRA_AUDIO_URI)
        if (audioUriString != null) {
            currentAudioUriString = audioUriString
            startForeground(NOTIFICATION_ID, createNotification())
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            val settings = DependencyProvider.getSettingsManager(this)
            if (settings.showAsNotification) {
                val toastMsg = com.example.data.Localization.getString("toast_service_started", settings.uiLanguage)
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            } else {
                showOverlay()
            }
            startTranscription(audioUriString)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startTranscription(uriString: String) {
        val context = this
        val uri = Uri.parse(uriString)
        val engine = LocalTranscriptionEngine(context)
 
        // Fetch settings for pre-selected language target
        val settings = DependencyProvider.getSettingsManager(context)
        val targetLanguage = settings.getTargetLanguageCode()
 
        engine.transcribeAudio(uri, object : LocalTranscriptionEngine.TranscriptionCallback {
            override fun onStart() {
                transcriptionStatus = "Loading Offline Engine..."
                transcriptionProgress = 0.05f
                updateForegroundNotification(transcriptionStatus, transcriptionProgress)
            }
 
            override fun onProgress(progress: Float) {
                val engineType = settings.sttEngine
                val isWhisper = engineType == "whisper"
                transcriptionStatus = when {
                    isWhisper -> {
                        when {
                            progress < 0.15f -> "Loading Whisper model..."
                            progress < 0.4f -> "Analyzing audio waves..."
                            progress < 0.7f -> "Decoding speech patterns..."
                            progress < 0.9f -> "Synthesizing text..."
                            else -> "Finishing up..."
                        }
                    }
                    else -> "Transcribing... (${(progress * 100).toInt()}%)"
                }
                transcriptionProgress = progress
                updateForegroundNotification(transcriptionStatus, transcriptionProgress, transcribedText)
            }
 
            override fun onPartialResult(text: String) {
                transcribedText = text
                updateForegroundNotification(transcriptionStatus, transcriptionProgress, text)
            }
 
            override fun onComplete(fullText: String) {
                transcribedText = fullText
                transcriptionProgress = 1.0f
                transcriptionStatus = "Completed"
                isCompleted = true
 
                // Save to local database transparently (will be encrypted inside the repo)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = DependencyProvider.getRepository(context)
                        val entity = TranscriptionEntity(
                            audioUri = uriString,
                            transcribedText = fullText,
                            language = targetLanguage,
                            timestamp = System.currentTimeMillis()
                        )
                        val id = repository.insert(entity)
 
                        if (settings.showAsNotification) {
                            showCompletedNotification(context, id.toInt(), fullText)
                            stopSelf()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
 
            override fun onError(error: String) {
                isError = true
                errorMsg = error
                transcriptionStatus = "Error occurred"
 
                if (settings.showAsNotification) {
                    showErrorNotification(context, error)
                    stopSelf()
                }
            }
        })
    }

    private fun showOverlay() {
        if (composeView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120 // Positioned nicely below status bar
        }

        composeView = ComposeView(this).apply {
            // Set up Jetpack Compose owners
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MyApplicationTheme {
                    OverlayContent()
                }
            }
        }

        windowManager?.addView(composeView, params)
    }

    @Composable
    private fun OverlayContent() {
        val context = LocalContext.current
        val settings = remember { DependencyProvider.getSettingsManager(context) }
        val displayLang = remember {
            if (settings.getTargetLanguageCode() == "de") "Deutsch (DE)" else "English (EN)"
        }
        var isExpanded by remember { mutableStateOf(false) }
        var canExpand by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            if (showCancelConfirmation) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = com.example.data.Localization.getString("cancel_dialog_title", settings.uiLanguage),
                        color = SleekPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = com.example.data.Localization.getString("cancel_dialog_desc", settings.uiLanguage),
                        color = SleekText,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                showCancelConfirmation = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekInnerSurface, contentColor = SleekText),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(com.example.data.Localization.getString("cancel_dialog_btn_keep", settings.uiLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                dismissOverlayAndStop()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary, contentColor = SleekButtonText),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(com.example.data.Localization.getString("cancel_dialog_btn_cancel", settings.uiLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = getString(R.string.app_name) + " Transcriber",
                                color = SleekPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isError) "Failed to process voice" else transcriptionStatus,
                                color = SleekText.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        // Display targeted language badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(SleekBackground, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = displayLang,
                                    color = SleekPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(
                                onClick = { 
                                    if (!isCompleted && !isError) {
                                        showCancelConfirmation = true
                                    } else {
                                        dismissOverlayAndStop()
                                    }
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SleekBackground)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = SleekText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Indicator
                    if (!isCompleted && !isError) {
                        LinearProgressIndicator(
                            progress = { transcriptionProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SleekPrimary,
                            trackColor = SleekInnerSurface,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Error message
                    if (isError) {
                        Text(
                            text = "Error: $errorMsg",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Transcribed text display
                        val displayText = transcribedText.ifEmpty { "Waiting for offline voice segments..." }
                        Column {
                            Text(
                                text = displayText,
                                color = SleekText.copy(alpha = if (transcribedText.isEmpty()) 0.4f else 0.95f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { textLayoutResult ->
                                    if (!isExpanded) {
                                        canExpand = textLayoutResult.hasVisualOverflow
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isExpanded) {
                                            Modifier
                                                .heightIn(max = 240.dp)
                                                .verticalScroll(rememberScrollState())
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                            if (canExpand || isExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isExpanded) {
                                        com.example.data.Localization.getString("btn_show_less", settings.uiLanguage)
                                    } else {
                                        com.example.data.Localization.getString("btn_show_more", settings.uiLanguage)
                                    },
                                    color = SleekPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { isExpanded = !isExpanded }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Action panel once completed
                    AnimatedVisibility(visible = isCompleted) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Transcription", transcribedText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SleekPrimary,
                                        contentColor = SleekButtonText
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, transcribedText)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        val chooser = Intent.createChooser(intent, "Share Text").apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(chooser)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SleekInnerSurface,
                                        contentColor = SleekText
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = SleekText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dismissOverlayAndStop() {
        if (composeView != null) {
            try {
                windowManager?.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            composeView = null
        }
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = CHANNEL_ID
        val settings = DependencyProvider.getSettingsManager(this)
        val uiLanguage = settings.uiLanguage
        val title = com.example.data.Localization.getString("notification_title_started", uiLanguage)
        val text = com.example.data.Localization.getString("notification_desc_started", uiLanguage)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "${getString(R.string.app_name)} Transcriber Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun updateForegroundNotification(status: String, progress: Float, partialText: String = "") {
        val settings = DependencyProvider.getSettingsManager(this)
        if (!settings.showAsNotification) return
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = com.example.data.Localization.getString("notification_title_started", settings.uiLanguage)
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setProgress(100, (progress * 100).toInt(), false)
            
        if (partialText.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$status\n\n$partialText"))
        }
        
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showCompletedNotification(context: Context, id: Int, fullText: String) {
        val channelId = CHANNEL_ID
        val settings = DependencyProvider.getSettingsManager(context)
        val uiLanguage = settings.uiLanguage
        
        val title = com.example.data.Localization.getString("notification_title_completed", uiLanguage)
        val btnCopyText = com.example.data.Localization.getString("notification_action_copy", uiLanguage)
        val btnShareText = com.example.data.Localization.getString("notification_action_share", uiLanguage)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "${context.getString(R.string.app_name)} Transcriber Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("scroll_to_id", id.toLong())
        }
        val pendingMainIntent = android.app.PendingIntent.getActivity(
            context,
            id,
            mainIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = Intent(context, CopyReceiver::class.java).apply {
            putExtra("text_to_copy", fullText)
        }
        val pendingCopyIntent = android.app.PendingIntent.getBroadcast(
            context,
            id + 10000,
            copyIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, fullText)
            type = "text/plain"
        }
        val pendingShareIntent = android.app.PendingIntent.getActivity(
            context,
            id + 20000,
            Intent.createChooser(shareIntent, btnShareText),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(fullText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingMainIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .addAction(android.R.drawable.ic_menu_save, btnCopyText, pendingCopyIntent)
            .addAction(android.R.drawable.ic_menu_share, btnShareText, pendingShareIntent)

        notificationManager.notify(8889, builder.build())
    }

    private fun showErrorNotification(context: Context, error: String) {
        val channelId = CHANNEL_ID
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Transcription Failed")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(8890, builder.build())
    }

    override fun onDestroy() {
        if (composeView != null) {
            try {
                windowManager?.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            composeView = null
        }
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
