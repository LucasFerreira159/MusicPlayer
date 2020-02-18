package com.app4fun.musicplayer.application.infrastructure.util

import android.content.Context
import android.content.SharedPreferences
import com.app4fun.musicplayer.application.musiclibrary.model.Audio
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StorageUtil (val context: Context) {

    private val STORAGE = "com.app4fun.musicplayer.STORAGE"

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
    }

    fun storeAudio(list: List<Audio>) {
        val editor: SharedPreferences.Editor = preferences.edit()
        val gson = Gson()
        val json = gson.toJson(list)
        editor.putString("audioArrayList", json)
        editor.apply()
    }

    fun loadAudio(): List<Audio> {
        val gson = Gson()
        val json = preferences.getString("audioArrayList", null)
        val type = object : TypeToken<ArrayList<Audio>>() {}.type
        return gson.fromJson(json, type)
    }

    fun storeAudioIndex(index: Int) {
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.putInt("audioIndex", index)
        editor.apply()
    }

    fun loadAudioIndex(): Int {
        return preferences.getInt("audioIndex", -1) //retorna -1 se não for encontrado
    }

    fun clearCachedAudioPlaylist() {
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.clear()
        editor.commit()
    }
}