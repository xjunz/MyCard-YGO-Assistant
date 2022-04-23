package xjunz.tool.mycard.main.account

import android.util.Base64

object Generator {

    fun generateToken(id: Int): String {
        val b = arrayOf(0xD0, 0x30, 0, 0, 0, 0)
        val r = id % 0xFFFF + 1
        for (t in b.indices step 2) {
            val k = b[t + 1] shl 8 or b[t] xor r
            b[t] = k and 0xFF
            b[t + 1] = k ushr 8 and 0xFF
        }
        return Base64.encodeToString(
            ByteArray(b.size) { i -> (b[i] and 0xFF).toByte() }, Base64.NO_WRAP
        )
    }

    fun generateMatchAuth(): String {
        val user = AccountManager.peekUser()
        return "Basic " + Base64.encodeToString(
            "${user.username}:${user.id}".toByteArray(), Base64.NO_WRAP
        )
    }
}