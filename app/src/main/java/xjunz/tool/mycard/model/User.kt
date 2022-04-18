package xjunz.tool.mycard.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val active: Boolean,
    val admin: Boolean,
    val avatar: String,
    val created_at: String,
    val email: String,
    val id: Int,
    val ip_address: String,
    val locale: String,
    val name: String,
    val password_hash: String,
    val registration_ip_address: String,
    val salt: String,
    val updated_at: String,
    val username: String
) {

    inline val memberSince: String
        get() {
            val newSequence = CharArray(created_at.length)
            created_at.forEachIndexed { index, c ->
                if (c.isLetter()) {
                    newSequence[index] = ' '
                } else {
                    newSequence[index] = c
                }
            }
            return newSequence.joinToString("")
        }
}