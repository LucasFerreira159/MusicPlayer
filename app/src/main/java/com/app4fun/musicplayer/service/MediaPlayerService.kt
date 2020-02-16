package com.app4fun.musicplayer.service

import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.io.IOException

class MediaPlayerService:
    Service(), OnCompletionListener, OnPreparedListener,
    OnErrorListener, OnSeekCompleteListener, OnInfoListener,
    OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener
{

    private val iBinder: IBinder = LocalBinder()

    private var resumePosition: Int = 0

    private val mediaPlayer: MediaPlayer by lazy {
        MediaPlayer()
    }
    private var mediaFile: String? = null

    override fun onCompletion(mp: MediaPlayer?) {
        //Invoked when playback of a media source has completed.
        stopMedia()
        //stop this service
        stopSelf()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        //Invoked when the media source is ready for playback.
        playMedia()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        //Invoked when there has been an error during an asynchronous operation.
        when(what) {
            MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> {
                Log.d("MediaPlayer Error", "MÍDIA NÃO É VÁLIDA PARA REPRODUÇÃO  $extra");
            }
            MEDIA_ERROR_SERVER_DIED -> {
                Log.d("MediaPlayer Error", "SERVIDOR DE ERRO DE MÍDIA MORREU $extra");
            }
            MEDIA_ERROR_UNKNOWN -> {
                Log.d("MediaPlayer Error", "ERRO DE MÍDIA DESCONHECIDO $extra");
            }
        }
        return false
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        //Invoked indicating the completion of a seek operation.
    }

    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        //Invoked to communicate some info.
        return false
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    override fun onAudioFocusChange(focusChange: Int) {
        //Invoked when the audio focus of the system is updated.
    }

    override fun onBind(intent: Intent?): IBinder? {
        return iBinder
    }

    private fun initMidiaPlayer() {
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnErrorListener(this)
        mediaPlayer.setOnPreparedListener(this)
        mediaPlayer.setOnBufferingUpdateListener(this)
        mediaPlayer.setOnSeekCompleteListener(this)
        mediaPlayer.setOnInfoListener(this)
        mediaPlayer.reset()

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

        try {
            mediaPlayer.setDataSource(mediaFile)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }

        mediaPlayer.prepareAsync()
    }

    private fun playMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer == null) return
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            resumePosition = mediaPlayer.currentPosition
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.seekTo(resumePosition)
            mediaPlayer.start()
        }
    }

    class LocalBinder : Binder() {
        fun getService(): MediaPlayerService {
            return MediaPlayerService()
        }
    }
}