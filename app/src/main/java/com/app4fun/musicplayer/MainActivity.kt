package com.app4fun.musicplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.app4fun.musicplayer.application.infrastructure.util.StorageUtil
import com.app4fun.musicplayer.application.musiclibrary.adapter.MusicLibraryAdapter
import com.app4fun.musicplayer.application.musiclibrary.interfaces.AudioView
import com.app4fun.musicplayer.application.musiclibrary.model.Audio
import com.app4fun.musicplayer.service.MediaPlayerService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), AudioView {

    private var player: MediaPlayerService? = null
    private var serviceBound = false

    private val audioList: MutableList<Audio> = ArrayList()

    private var serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder: MediaPlayerService.LocalBinder = service as MediaPlayerService.LocalBinder
            player = binder.getService()
            serviceBound = true

            Toast.makeText(applicationContext, "Service Bound", Toast.LENGTH_SHORT).show()
        }

    }

    companion object {
        val Broadcast_PLAY_NEW_AUDIO = "com.app4fun.musicplayer.PlayNewAudio"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")

        loadAudio()

        toolbar.title = "Minhas Musicas"

        val adapter = MusicLibraryAdapter(audioList, this)

        recycler_track.layoutManager = LinearLayoutManager(applicationContext)
        recycler_track.setHasFixedSize(true)
        recycler_track.adapter = adapter
        adapter.notifyDataSetChanged()

    }

    //Guarda estado atual do serviço
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(outState)
    }

    //Recupera o estado do serviço
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            player?.stopSelf()
        }
    }

    override fun onClickPlay(index: Int) {
        playAudio(index)
    }

    private fun playAudio(index: Int) {
        //Checa se o serviço está ativo
        if (!serviceBound) {

            val storage = StorageUtil(applicationContext)
            storage.storeAudio(audioList)
            storage.storeAudioIndex(index)

            val playerIntent = Intent(this, MediaPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            //Armazena novo audioIndex no SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(index)

            //O serviço está ativo
            //Envia media com Broadcast Receiver
            val broadCastIntent = Intent(Broadcast_PLAY_NEW_AUDIO)
            sendBroadcast(broadCastIntent)

        }
    }

    private fun loadAudio() {
        val contentResolver = contentResolver
        val uri = EXTERNAL_CONTENT_URI
        val selection = "$IS_MUSIC!= 0"
        val sortOrder = "$TITLE ASC"
        val cursor = contentResolver
            .query(uri, null, selection, null, sortOrder)

        if (cursor != null && cursor.count > 0) {
            while (cursor.moveToNext()) {
                val data = cursor.getString(cursor.getColumnIndex(DATA))
                val title = cursor.getString(cursor.getColumnIndex(TITLE))
                val album = cursor.getString(cursor.getColumnIndex(ALBUM))
                val artist = cursor.getString(cursor.getColumnIndex(ARTIST))
                val time = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION))
                val albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
                //val art = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART))
                //Salva as informações na lista
                audioList.add(Audio(data, title, album, artist, time, albumId))
            }
        }

        cursor?.close()
    }
}
