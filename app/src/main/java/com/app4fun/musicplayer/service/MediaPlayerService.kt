package com.app4fun.musicplayer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.io.IOException
import java.lang.NullPointerException

class MediaPlayerService :
    Service(), OnCompletionListener, OnPreparedListener,
    OnErrorListener, OnSeekCompleteListener, OnInfoListener,
    OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    private val iBinder: IBinder = LocalBinder()
    private lateinit var audioManager: AudioManager

    private var resumePosition: Int = 0
    private var mediaFile: String? = null
    private var mediaPlayer: MediaPlayer? = null

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
        when (what) {
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
        //Chamado quando o audio for modificado
        when (focusChange) {
            AUDIOFOCUS_GAIN -> {
                //O serviço ganhou foco no áudio, por isso precisa começar a tocar.
                mediaPlayer?.let {
                    if (!it.isPlaying) it.start();
                    it.setVolume(1.0f, 1.0f)
                } ?: initMediaPlayer()
            }
            AUDIOFOCUS_LOSS -> {
                //O serviço perdeu o foco do áudio, provavelmente o usuário mudou para reproduzir
                // mídia em outro aplicativo, então libere o media player.
                mediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                    mediaPlayer = null
                }
            }
            AUDIOFOCUS_LOSS_TRANSIENT -> {
                //Fucos perdeu por um curto período de tempo, faça uma pausa no MediaPlayer.
                mediaPlayer?.let {
                    if (it.isPlaying) it.pause()
                }
            }
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                //Perdeu o foco por um curto período de tempo, provavelmente uma notificação
                // chegou ao dispositivo, diminua o volume da reprodução.
                mediaPlayer?.let {
                    if (it.isPlaying) it.setVolume(0.1f, 0.1f)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return iBinder
    }

    /**
     * O sistema chama esse método quando uma atividade solicita que o serviço seja iniciado
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        try {
            // Um ​​arquivo de áudio é passado para o serviço através de putExtra ();
            mediaFile = intent?.extras?.getString("media")
        } catch (e: NullPointerException) {
            stopSelf()
        }

        // Solicita foco de áudio
        if (!requestAudioFocus()) {
            //Não foi possível obter o foco
            stopSelf()
        }

        if (mediaFile != null && mediaFile != "")
            initMediaPlayer()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.let {
            stopMedia()
            it.release()
        }
        removeAudioFocus()
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()

        mediaPlayer?.let {
            it.setOnCompletionListener(this)
            it.setOnErrorListener(this)
            it.setOnPreparedListener(this)
            it.setOnBufferingUpdateListener(this)
            it.setOnSeekCompleteListener(this)
            it.setOnInfoListener(this)
            it.reset()

            it.setAudioStreamType(STREAM_MUSIC)

            try {
                it.setDataSource(mediaFile)
            } catch (e: IOException) {
                e.printStackTrace()
                stopSelf()
            }

            it.prepareAsync()
        }
    }

    private fun playMedia() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.start()
            }
        }
    }

    private fun stopMedia() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
        }
    }

    private fun pauseMedia() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                resumePosition = it.currentPosition
            }
        }
    }

    private fun resumeMedia() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.seekTo(resumePosition)
                it.start()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager.requestAudioFocus(this, STREAM_MUSIC, AUDIOFOCUS_GAIN)
        if (result == AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        //retorna falso quando não for possível obter foco
        return false
    }

    private fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this)
    }
}

class LocalBinder : Binder() {
    fun getService(): MediaPlayerService {
        return MediaPlayerService()
    }
}