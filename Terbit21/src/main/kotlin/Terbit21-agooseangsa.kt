package com.agooseangsa.Terbit21

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(103, 58, -83, 118, 90, 105, 92, -74, -64, 60, -103, -41, -97, -81, -84, -5, 84, -104, 50, -24, -128, 104, -25, -85, 39, 9, -25, 36, 73, 18, 102, -57)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
