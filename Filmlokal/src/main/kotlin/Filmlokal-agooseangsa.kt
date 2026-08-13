package com.agooseangsa.Filmlokal

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(-40, -94, 41, 72, 14, -26, -22, 13, 97, -10, 35, -95, -48, -103, 117, -27, -104, -55, 49, 83, -84, -121, -122, 98, 50, -4, 61, -14, -15, -109, 16, -3)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
