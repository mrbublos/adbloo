package bloo.ad.addbloo

import android.util.Log
import kotlin.math.pow

fun String?.log() {
    this?.takeIf { isNotEmpty() }?.let {
        Log.v("adBloo", this)
    }
}

fun String?.logE(e: Throwable? = null) {
    this?.takeIf { isNotEmpty() }?.let {
        Log.e("adBloo", this, e)
    }
}

fun Byte?.bits(offset: Int, len: Int): Byte {
    if (this == null || offset > 8) { return 0 }
    val me = this.toInt()
    return (me shr (8 - len - offset)).rem((2.0.pow(len)).toInt()).toByte()
}

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

fun ByteArray.toPrettyHex() : String{
    val result = StringBuffer()
    var counter = 1
    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
        result.append(" ")
        if (counter++ % 8 == 0) { result.append("\n") }
    }

    return result.toString()
}

fun ByteArray?.toInt(): Int {
    var result = 0
    this?.forEach {
        result = (result shl 8) + (it.toInt() and 0xFF)
    }
    return result
}