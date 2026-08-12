package com.agooseangsa.DrakorKita

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(89, -113, -75, 45, -4, 53, 88, 49, -6, 78, -128, -18, 50, -84, -104, -16, 59, -89, -74, 55, -46, -43, -42, 18, -115, 55, -67, -14, 76, -100, -50, -38)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
