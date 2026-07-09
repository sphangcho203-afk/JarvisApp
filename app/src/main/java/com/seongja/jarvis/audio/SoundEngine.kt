package com.seongja.jarvis.audio

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class SoundEngine(private val vibrator: Vibrator?) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    fun pulse(strong: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(if (strong) 55 else 25, if (strong) 90 else 45))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(if (strong) 55 else 25)
        }
    }

    fun release() {
        soundPool.release()
    }
}
