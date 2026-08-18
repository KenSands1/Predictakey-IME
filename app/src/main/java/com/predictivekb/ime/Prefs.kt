package com.predictivekb.ime

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "predictive_keyboard_prefs"
    private const val KEY_DIRECTION_RTL = "prediction_direction_rtl"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** false = left-to-right (most likely letter on the left, the default). */
    fun isPredictionRowRtl(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DIRECTION_RTL, false)

    fun setPredictionRowRtl(context: Context, rtl: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIRECTION_RTL, rtl).apply()
    }
}
