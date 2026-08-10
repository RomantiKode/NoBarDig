package com.agooseangsa.MidasXXI

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(126, -91, 79, -60, 104, -6, 122, -83, 116, 61, 115, -82, 80, 5, -119, 72, -9, -31, 7, 86, -79, 7, -81, 91, 9, 45, -76, -82, -74, 82, 67, 46)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
