package com.anipulse.app.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.launch

/** Короткий системный звук нового сообщения, пока экран чата/ЛС открыт (отдельно от фоновых пушей NotifyWorker). */
object SoundPlayer {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    fun playMessageSound(context: Context) {
        scope.launch {
            runCatching {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                val descriptor = context.resources.openRawResourceFd(com.anipulse.app.R.raw.anipulse_pulse)
                player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                descriptor.close()
                player.setOnCompletionListener { it.release() }
                player.setOnErrorListener { mp, _, _ -> mp.release(); true }
                player.prepare()
                player.start()
            }
        }
    }
}
