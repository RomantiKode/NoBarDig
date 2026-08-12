package com.agooseangsa.MidasXXI

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(127, -73, 21, 118, 126, 2, -113, 55, -93, 21, 29, 44, -71, -91, 80, -50, 20, -128, -53, -64, 61, -75, 22, -23, 90, -83, -23, 25, 83, -3, 18, -81)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
