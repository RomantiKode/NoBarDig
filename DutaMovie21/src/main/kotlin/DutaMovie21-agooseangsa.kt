package com.agooseangsa.DutaMovie21

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(87, -8, -62, 73, 75, -126, -69, 8, -42, 52, 83, -72, 105, 114, -89, -26, -9, 72, 119, -89, 85, 10, -128, -100, -108, -1, 53, -20, 86, -8, 84, -104)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
