package com.app4fun.musicplayer.service

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.app4fun.musicplayer.MainActivity
import com.app4fun.musicplayer.R
import com.app4fun.musicplayer.application.PlaybackStatus
import com.app4fun.musicplayer.application.infrastructure.util.StorageUtil
import com.app4fun.musicplayer.application.musiclibrary.model.Audio
import java.io.IOException

class MediaPlayerService :
    Service(), OnCompletionListener, OnPreparedListener,
    OnErrorListener, OnSeekCompleteListener, OnInfoListener,
    OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    private val iBinder: IBinder = LocalBinder()
    private lateinit var telephonyManager: TelephonyManager
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var ongoingCall = false
    private var resumePosition: Int = 0
    private var mediaFile: String? = null

    private var mediaPlayer: MediaPlayer? = null
    private var phoneStateListener: PhoneStateListener? = null

    //lista de músicas disponiveis
    private lateinit var audioList: List<Audio>
    private var audioIndex: Int = -1
    private lateinit var activeAudio: Audio

    //MediaSession
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var transportControls: MediaControllerCompat.TransportControls

    companion object {
        val ACTION_PLAY = "com.app4fun.musicplayer.ACTION_PLAY"
        val ACTION_PAUSE = "com.app4fun.musicplayer.ACTION_PAUSE"
        val ACTION_PREVIOUS = "com.app4fun.musicplayer.ACTION_PREVIOUS"
        val ACTION_NEXT = "com.app4fun.musicplayer.ACTION_NEXT"
        val ACTION_STOP = "com.app4fun.musicplayer.ACTION_STOP"

        //AudioPlayer ID Notificação
        val NOTIFICATION_ID = 101

        fun getAlbumArtUri(albumId: Long): Uri? {
            return ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        }
    }

    /**
     * Broadcast receiver responsável por receber informações se o fone de ouvido foi removido
     */
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            //Pausa audio na ação ACTION_AUDIO_BECOMING_NOISE
            pauseMedia()
            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    private val playNewAudio = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            //Pega a posição da midia no SharedPreferences
            audioIndex = StorageUtil(applicationContext).loadAudioIndex()
            if (audioIndex != -1 && audioIndex < audioList.size) {
                activeAudio = audioList[audioIndex]
            } else {
                stopSelf()
            }

            //Recebeu PLAY_NEW_AUDIO action
            //Reseta o mediaPlayer para tocar a nova música
            stopMedia()
            mediaPlayer?.reset()
            initMediaPlayer()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

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
            // carrega arquivo do shared preference
            val storage = StorageUtil(applicationContext)
            audioList = storage.loadAudio()
            audioIndex = storage.loadAudioIndex()

            if (audioIndex != -1 && audioIndex < audioList.size) {
                activeAudio = audioList[audioIndex]
            } else {
                stopSelf()
            }

        } catch (e: NullPointerException) {
            stopSelf()
        }

        // Solicita foco de áudio
        if (!requestAudioFocus()) {
            //Não foi possível obter o foco
            stopSelf()
        }

        if (!::mediaSessionManager.isInitialized) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }

        //Handle Intent action from MediaSession.TransportControls
        //handleIncomingActions(intent)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        // Executa procedimentos de configuração

        // Gerencia chamadas telefônicas recebidas durante a reprodução
        // Pausa o MediaPlayer na chamada recebida,
        callStateListener()
        //ACTION_AUDIO_BECOMING_NOISY -- chamado se houver mudança na saida de audio -- BroadcastReceiver
        registerBecomingNoisyReceiver()
        //registra o novo audio que irá reproduzir -- BroadcastReceiver
        register_playNewAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.let {
            stopMedia()
            it.release()
        }
        removeAudioFocus()
        //Desvincula o PhoneStateListener
        phoneStateListener?.let {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }

        removeNotification()

        //Desvincula os BrodcastReceivers
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(playNewAudio)

        //Limpa o cache da PlayList
        StorageUtil(applicationContext).clearCachedAudioPlaylist()
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnBufferingUpdateListener(this)
        mediaPlayer?.setOnSeekCompleteListener(this)
        mediaPlayer?.setOnInfoListener(this)
        mediaPlayer?.reset()

        mediaPlayer?.setAudioStreamType(STREAM_MUSIC)

        try {
            mediaPlayer?.setDataSource(activeAudio.data)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }

        mediaPlayer?.prepareAsync()
    }

    private fun playMedia() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
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

    private fun skipToNext() {
        if (audioIndex == audioList.size - 1) {
            //se for a ultima da playlist
            audioIndex = 0
            activeAudio = audioList[audioIndex]
        } else {
            //chama a próxima da playlist
            activeAudio = audioList[audioIndex]
        }

        StorageUtil(applicationContext).storeAudioIndex(audioIndex)

        stopMedia()
        //reseta o media Player
        mediaPlayer?.reset()
        initMediaPlayer()
    }

    private fun skipToPrevious() {
        if (audioIndex == 0) {
            //se for o primeiro da playlist
            //atribui o index para a última música da playlist
            activeAudio = audioList[--audioIndex]
        }

        StorageUtil(applicationContext).storeAudioIndex(audioIndex)

        stopMedia()
        //reseta o media Player
        mediaPlayer?.reset()
        initMediaPlayer()
    }

    private fun requestAudioFocus(): Boolean {
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

    private fun registerBecomingNoisyReceiver() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    /**
     * Método responsável por tratar chamadas recebidas
     */
    private fun callStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                when (state) {
                    // caso haja pelo menos uma chamada ou o telefone estiver tocando
                    // pausa o MediaPlayer
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        mediaPlayer?.let {
                            pauseMedia()
                            ongoingCall = true
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        mediaPlayer?.let {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                    }
                }
            }
        }
        // Registra o ouvinte no gerenciador de chamadas
        // Observa as alterações no estado da chamada do dispositivo.
        telephonyManager.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    private fun register_playNewAudio() {
        //Registra playNewMedia receiver
        val filter = IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudio, filter)
    }

    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (!::mediaSessionManager.isInitialized) return

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        //Cria um novo MediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //Recupera MediaSession transport controls
        transportControls = mediaSession.controller.transportControls
        //atribui Media Session -> pronto para receber comandos
        mediaSession.isActive = true
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        updateMetaData()

        //Atribui callbalck para receber atualizações do MediaSession
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSED)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                skipToPrevious()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onStop() {
                super.onStop()
                removeNotification();
                //Stop the service
                stopSelf()
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
            }
        })
    }

    private fun updateMetaData() {
        val albumArt: Bitmap = BitmapFactory.decodeResource(
            resources,
            R.drawable.nocover
        )

        if (!::mediaSession.isInitialized) {
            //Cria um novo MediaSession
            mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        }

        //atualiza a metadata
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, activeAudio.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "")
                .putString(MediaMetadata.METADATA_KEY_TITLE, activeAudio.title)
                .build()
        )
    }

    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackAction = Intent(this, MediaPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                // Play
                playbackAction.action = ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }

            1 -> {
                // Pause
                playbackAction.action = ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }

            2 -> {
                // Next track
                playbackAction.action = ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }

            3 -> {
                // Previous track
                playbackAction.action = ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
        }

        return null
    }

    private fun buildNotification(playbackStatus: PlaybackStatus) {

        if (!::mediaSession.isInitialized) {
            //Cria um novo MediaSession
            mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        }


        var notificationIcon = R.drawable.ic_play_circle_outline
        var play_pauseAction: PendingIntent? = null

        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationIcon = R.drawable.ic_pause_circle_outline_black_24dp
            play_pauseAction = playbackAction(1)
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationIcon = R.drawable.ic_play_circle_outline
            play_pauseAction = playbackAction(0)
        }

        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.mipmap.ic_launcher_round
        )

        val notificationManager = NotificationManagerCompat.from(this)

        val channelId = "MUSIC_PLAYER_CHANNEL_ID"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Music Channel"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val notificationChannel = NotificationChannel(channelId, channelName, importance)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(this)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setColor(resources.getColor(R.color.colorPrimary))
            .setLargeIcon(largeIcon)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentText(activeAudio.artist)
            .setContentTitle(activeAudio.album)
            .setChannelId(channelId)
            .setContentInfo(activeAudio.title)
            .addAction(R.drawable.ic_skip_previous, "previous", playbackAction(3))
            .addAction(R.drawable.ic_pause, "pause", play_pauseAction)
            .addAction(R.drawable.ic_skip_next_black_24dp, "next", playbackAction(2))

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        val manager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun removeNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    class LocalBinder : Binder() {
        fun getService(): MediaPlayerService {
            return MediaPlayerService()
        }
    }
}