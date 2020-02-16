package com.app4fun.musicplayer

import android.content.ComponentName
import android.content.Context
import android.content.Context.*
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import com.app4fun.musicplayer.service.MediaPlayerService

class MainActivity : AppCompatActivity() {

    private var player: MediaPlayerService? = null
    private var serviceBound = false

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

        playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")
    }

    private fun playAudio(media: String) {
        //Checa se o serviço está ativo
        if(!serviceBound) {
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            playerIntent.putExtra("media", media)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            //O serviço está ativo
            //Envia media com Broadcast Receiver
        }
    }
}
