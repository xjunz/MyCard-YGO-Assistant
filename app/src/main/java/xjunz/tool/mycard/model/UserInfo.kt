package xjunz.tool.mycard.model

import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val token: String,
    val user: User
)