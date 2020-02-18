package com.app4fun.musicplayer.application.musiclibrary.model

import java.io.Serializable

data class Audio(
    val data: String,
    val title: String,
    val album: String,
    val artist: String,
    val time: String,
    val albumArt: String
) : Serializable