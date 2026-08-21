package com.agooseangsa.AnimeXin

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(-94, -74, 124, 46, 38, 63, 123, 21, 35, -74, -6, 19, 19, -8, 64, -126, -92, -70, -28, -54, 5, 51, -86, 75, 30, 28, -93, -57, 25, 125, -109, -74)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
