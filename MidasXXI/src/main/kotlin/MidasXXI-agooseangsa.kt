package com.agooseangsa.MidasXXI

import android.util.Base64

internal object _x7P {
    private val k = byteArrayOf(102, -33, -47, 87, -43, 44, 124, 121, -111, -74, -113, -107, 13, -26, -95, 101, 97, -126, 40, -11, -5, 87, 72, 90, -107, -117, -91, 91, -41, 108, -3, -65)

    fun d(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        for (index in bytes.indices) {
            bytes[index] = (bytes[index].toInt() xor k[index % k.size].toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}

internal fun _q9(value: String): String = _x7P.d(value)
