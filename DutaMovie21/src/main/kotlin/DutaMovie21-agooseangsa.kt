package com.agooseangsa.DutaMovie21

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(37, 102, 52, 40, -78, -42, -117, -111, -56, 66, 84, -120, -64, -116, 118, -55, 35, 51, 35, -83, -30, -53, -124, -84, 38, -22, -70, 120, -125, 63, 111, 31)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
