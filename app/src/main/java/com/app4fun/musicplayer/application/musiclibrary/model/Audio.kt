package com.app4fun.musicplayer.application.musiclibrary.model

import java.io.Serializable
import java.math.BigDecimal

data class Audio(
    val data: String,
    val title: String,
    val album: String,
    val artist: String,
    val time: Long,
    val albumId: Long
) : Serializable