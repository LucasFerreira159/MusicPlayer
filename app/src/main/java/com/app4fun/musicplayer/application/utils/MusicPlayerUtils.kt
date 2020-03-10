package com.app4fun.musicplayer.application.utils

import android.content.Context
import com.app4fun.musicplayer.R

object MusicPlayerUtils {

    fun makeShortTimeString(context: Context, secs: Long): String {
        var secs = secs
        val hours: Long
        val mins: Long
        hours = secs / 3600
        secs %= 3600
        mins = secs / 60
        secs %= 60
        val durationFormat = context.resources.getString(
            if (hours == 0L) R.string.durationformatshort else R.string.durationformatlong
        )
        return String.format(durationFormat, hours, mins, secs)
    }

}