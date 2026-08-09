package com.agooseangsa.Sokuja

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(0, -68, 103, 68, 87, -7, -51, 46, -10, 52, 36, -48, -35, -76, -125, -75, 100, 119, 66, 88, 81, -86, 75, 115, 75, -35, -16, 66, 85, -110, -85, 44)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
