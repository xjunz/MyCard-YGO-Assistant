package xjunz.tool.mycard.model

import kotlinx.serialization.Serializable

@Serializable
data class MatchResult(
    val address: String,
    val password: String,
    val port: Int
)