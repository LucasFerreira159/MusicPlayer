package com.app4fun.musicplayer.application.musiclibrary.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app4fun.musicplayer.R
import com.app4fun.musicplayer.application.musiclibrary.interfaces.AudioView
import com.app4fun.musicplayer.application.musiclibrary.model.Audio
import com.app4fun.musicplayer.application.utils.MusicPlayerUtils
import com.app4fun.musicplayer.service.MediaPlayerService
import com.squareup.picasso.Picasso

class MusicLibraryAdapter(val audioList: List<Audio>, val view: AudioView) :
    RecyclerView.Adapter<MusicLibraryAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_library, parent, false)
        return MyViewHolder(view)
    }

    override fun getItemCount(): Int {
        return audioList.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val audio = audioList[position]
        holder.title.text = audio.title
        holder.artist.text = audio.artist
        holder.time.text = MusicPlayerUtils.makeShortTimeString(holder.itemView.context, audio.time / 1000)

        Picasso.get()
            .load(MediaPlayerService.getAlbumArtUri(audio.albumId).toString())
            .error(R.mipmap.ic_launcher)
            .into(holder.cover);

        holder.itemView.setOnClickListener {
            view.onClickPlay(position)
        }
    }

    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title = view.findViewById<TextView>(R.id.text_title)
        val artist = view.findViewById<TextView>(R.id.text_artist)
        val time = view.findViewById<TextView>(R.id.text_time)
        val cover = view.findViewById<ImageView>(R.id.img_cover)
    }
}