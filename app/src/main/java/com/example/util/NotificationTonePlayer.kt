package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TonePreset(
    val id: String,
    val name: String,
    val uri: String?
)

object NotificationTonePlayer {
    private const val TAG = "NotificationTonePlayer"

    const val URI_SILENT = "omni://tone/silent"
    const val URI_CHIME = "omni://tone/chime"
    const val URI_PING = "omni://tone/ping"
    const val URI_BELL = "omni://tone/bell"
    const val URI_URGENT = "omni://tone/urgent"
    const val URI_WORK = "omni://tone/work"

    val presets = listOf(
        TonePreset("default", "System Default", null),
        TonePreset("chime", "Crystal Chime", URI_CHIME),
        TonePreset("ping", "Quick Ping", URI_PING),
        TonePreset("bell", "Classic Bell", URI_BELL),
        TonePreset("urgent", "Urgent Alert", URI_URGENT),
        TonePreset("work", "Work Chime", URI_WORK),
        TonePreset("silent", "Silent (No Sound)", URI_SILENT)
    )

    private var activeRingtone: Ringtone? = null

    fun createRingtonePickerIntent(currentUri: String?): Intent {
        val parsedCurrent = currentUri
            ?.takeIf { !it.startsWith("omni://") }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, parsedCurrent)
        }
    }

    fun resolveToneTitle(context: Context, uri: Uri?): String {
        if (uri == null) return "Silent (No Sound)"
        val uriStr = uri.toString()
        presets.firstOrNull { it.uri == uriStr }?.let { return it.name }
        return try {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)?.takeIf { it.isNotBlank() }
                ?: "Custom Tone"
        } catch (e: Exception) {
            "Custom Tone"
        }
    }

    fun playTone(context: Context, toneUri: String?) {
        try {
            stopPreview()

            if (toneUri == URI_SILENT) return

            if (toneUri != null && toneUri.startsWith("omni://tone/")) {
                playSynthesizedPreset(toneUri)
                return
            }

            val resolvedUri = if (!toneUri.isNullOrBlank()) {
                Uri.parse(toneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } ?: return

            val ringtone = RingtoneManager.getRingtone(context.applicationContext, resolvedUri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            activeRingtone = ringtone
            ringtone.play()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to play tone uri=$toneUri", e)
        }
    }

    fun stopPreview() {
        try {
            activeRingtone?.let {
                if (it.isPlaying) it.stop()
            }
            activeRingtone = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringtone preview", e)
        }
    }

    private fun playSynthesizedPreset(toneUri: String) {
        CoroutineScope(Dispatchers.Default).launch {
            var generator: ToneGenerator? = null
            try {
                generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                when (toneUri) {
                    URI_CHIME -> {
                        generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                        delay(260)
                    }
                    URI_PING -> {
                        generator.startTone(ToneGenerator.TONE_PROP_ACK, 160)
                        delay(200)
                    }
                    URI_BELL -> {
                        generator.startTone(ToneGenerator.TONE_SUP_PIP, 180)
                        delay(220)
                        generator.startTone(ToneGenerator.TONE_SUP_PIP, 180)
                        delay(220)
                    }
                    URI_URGENT -> {
                        generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 320)
                        delay(360)
                    }
                    URI_WORK -> {
                        generator.startTone(ToneGenerator.TONE_PROP_PROMPT, 240)
                        delay(280)
                    }
                    else -> {
                        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                        delay(220)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ToneGenerator playback error", e)
            } finally {
                try {
                    generator?.release()
                } catch (_: Exception) {
                }
            }
        }
    }
}
