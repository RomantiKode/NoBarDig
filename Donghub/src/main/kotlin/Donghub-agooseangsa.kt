package com.agooseangsa.Donghub

import android.util.Base64

internal object _xD7h {
    private val k = byteArrayOf(117, 66, 92, -8, 56, 48, 46, -5, 58, -3, 122, -50, 120, 13, 24, -117, 78, -53, 13, 57, -86, 19, -59, -82, -57, 56, 89, 2, 99, 30, 33, 37)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _qD9(value: String): String = _xD7h.d(value)
