package com.agooseangsa.AnimeXin

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(19, -86, 3, -94, 11, -77, 114, 27, 66, 4, 86, -52, -19, 4, -107, -67, 57, -91, -68, 49, 78, -57, 46, 89, 5, -127, -47, -53, 56, 18, 6, 92)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
