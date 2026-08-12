package com.agooseangsa.MidasXXI

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(30, 39, -67, 94, -128, 126, -102, -71, -112, -96, 103, -70, -34, 29, -17, -45, -111, -34, 71, -44, 1, 76, -25, -35, 107, 107, 115, 65, -84, 52, 29, 16)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
