package com.agooseangsa.DutaMovie21

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(-83, 94, 18, -39, -114, 95, 47, -30, 35, -108, 3, -98, -35, 25, -1, -66, -13, -66, -76, 106, -64, -6, 126, 106, 107, -99, 46, 46, 64, -59, 125, -17)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
