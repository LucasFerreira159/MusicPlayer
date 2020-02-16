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
import com.app4fun.musicplayer.application.musiclibrary.adapter.MusicLibraryAdapter
import com.app4fun.musicplayer.application.musiclibrary.model.Audio
import com.app4fun.musicplayer.service.MediaPlayerService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")

        loadAudio()
        //play the first audio in the ArrayList
        playAudio(audioList[0].data)

        toolbar.title = "Minhas Musicas"

        val adapter = MusicLibraryAdapter(audioList)

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

    private fun playAudio(media: String) {
        //Checa se o serviço está ativo
        if (!serviceBound) {
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            playerIntent.putExtra("media", media)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(playerIntent)
            } else {
                startService(playerIntent)
            }
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            //O serviço está ativo
            //Envia media com Broadcast Receiver
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
                val time = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION))

                //Salva as informações na lista
                audioList.add(Audio(data, title, album, artist, time))
            }
        }

        cursor?.close()
    }
}
