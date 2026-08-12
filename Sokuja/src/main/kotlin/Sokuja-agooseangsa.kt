package com.agooseangsa.Sokuja

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(-62, 110, -65, 108, 4, 80, 96, 71, 44, 58, -57, -33, -36, -24, -76, -61, -62, 64, 116, -13, 18, -83, 109, -121, -126, -124, 40, -110, 118, 1, 10, -102)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
