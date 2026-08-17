package com.agooseangsa.Layarkaca21

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(40, -49, 1, 58, -11, 116, 38, 44, -8, -64, -36, -15, 23, -110, -106, -7, -117, 10, -83, -72, -86, -12, 53, -59, -63, -128, 91, -14, -101, -38, 88, -18)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
