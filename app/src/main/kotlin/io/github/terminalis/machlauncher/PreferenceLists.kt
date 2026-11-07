package io.github.terminalis.machlauncher

import android.content.SharedPreferences

fun SharedPreferences.getStringList(key: String): List<String>? =
    getString(key, null)?.let { if (it.isEmpty()) emptyList() else it.split('\n') }

fun SharedPreferences.Editor.putStringList(key: String, values: List<String>): SharedPreferences.Editor =
    putString(key, values.joinToString("\n"))
