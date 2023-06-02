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
    val ip_address: String? = null,
    val locale: String? = null,
    val name: String? = null,
    val password_hash: String? = null,
    val registration_ip_address: String? = null,
    val salt: String? = null,
    val updated_at: String? = null,
    val username: String
)