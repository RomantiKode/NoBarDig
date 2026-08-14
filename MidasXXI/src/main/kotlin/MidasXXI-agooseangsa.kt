package com.agooseangsa.MidasXXI

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(8, -113, 80, -52, 58, 127, 13, 64, 88, 54, 82, 109, 83, 3, 14, 92, 94, -22, -33, 120, -18, -15, 67, -53, 121, 32, -28, 65, 76, -40, -17, 77)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
