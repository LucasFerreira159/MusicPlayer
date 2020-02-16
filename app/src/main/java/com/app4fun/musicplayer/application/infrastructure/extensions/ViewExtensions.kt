package com.app4fun.musicplayer.application.infrastructure.extensions

import java.util.concurrent.TimeUnit

fun String.millisToMinutes(): String {
    val millis = this.toLong()
    return this.format(
        "%d min, %d sec",
        TimeUnit.MILLISECONDS.toMinutes(millis),
        TimeUnit.MILLISECONDS.toSeconds(millis) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
    )
}