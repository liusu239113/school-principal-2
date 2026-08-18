package com.principal.school2

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build

/**
 * 音频管理器
 * - BGM:MediaPlayer 循环播放校园主题曲
 * - 音效:SoundPool 播放 UI/建造/金币等短音效
 */
class SoundManager(context: Context) {

    private val bgm: MediaPlayer
    private val soundPool: SoundPool
    private val soundIds = HashMap<String, Int>()
    private var enabled = true

    init {
        bgm = MediaPlayer.create(context, R.raw.music)
        bgm.isLooping = true
        bgm.setVolume(0.5f, 0.5f)

        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(6, android.media.AudioManager.STREAM_MUSIC, 0)
        }

        soundIds["click"] = soundPool.load(context, R.raw.sfx_click, 1)
        soundIds["build"] = soundPool.load(context, R.raw.sfx_build, 1)
        soundIds["upgrade"] = soundPool.load(context, R.raw.sfx_upgrade, 1)
        soundIds["coin"] = soundPool.load(context, R.raw.sfx_coin, 1)
        soundIds["event"] = soundPool.load(context, R.raw.sfx_event, 1)
        soundIds["achievement"] = soundPool.load(context, R.raw.sfx_achievement, 1)
    }

    fun setEnabled(v: Boolean) {
        enabled = v
        if (!v) bgm.pause() else bgm.start()
    }

    fun startBgm() {
        if (enabled && !bgm.isPlaying) {
            try {
                bgm.start()
            } catch (_: Exception) {
            }
        }
    }

    fun stopBgm() {
        try {
            if (bgm.isPlaying) bgm.pause()
        } catch (_: Exception) {
        }
    }

    fun play(name: String) {
        if (!enabled) return
        val id = soundIds[name] ?: return
        try {
            soundPool.play(id, 0.9f, 0.9f, 1, 0, 1f)
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            bgm.release()
            soundPool.release()
        } catch (_: Exception) {
        }
    }
}
