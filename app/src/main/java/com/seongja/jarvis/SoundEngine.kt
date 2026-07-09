package com.seongja.jarvis

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build

class SoundEngine {
    private var soundPool: SoundPool? = null

    fun initialize() {
        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(3)
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(3, android.media.AudioManager.STREAM_MUSIC, 0)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
