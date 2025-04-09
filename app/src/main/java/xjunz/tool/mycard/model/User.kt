package xjunz.tool.mycard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val active: Boolean,
    val admin: Boolean,
    val avatar: String,
    @SerialName("created_at")
    val createdAt: String,
    val email: String,
    val id: Int,
    @SerialName("ip_address")
    val ipAddress: String? = null,
    val locale: String? = null,
    val name: String? = null,
    @SerialName("password_hash")
    val passwordHash: String? = null,
    @SerialName("registration_ip_address")
    val registrationIpAddress: String? = null,
    val salt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val username: String
)