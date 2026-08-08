package com.agooseangsa.Donghub

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(9, 53, 116, -50, 46, 89, -105, 37, 47, 25, 55, 21, -83, 12, -26, 31, -48, -32, -51, -24, -114, 21, -8, 17, 46, -113, 116, -29, 123, -38, -74, 116)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
