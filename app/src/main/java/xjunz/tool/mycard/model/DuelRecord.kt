package xjunz.tool.mycard.model

import kotlinx.serialization.Serializable

@Serializable
data class DuelRecord(
    val end_time: String,
    val expa: Float,
    val expa_ex: Float,
    val expb: Float,
    val expb_ex: Float,
    val isfirstwin: Boolean,
    val pta: Float,
    val pta_ex: Float,
    val ptb: Float,
    val ptb_ex: Float,
    val start_time: String,
    val type: String,
    val usernamea: String,
    val usernameb: String,
    val userscorea: Float,
    val userscoreb: Float,
    val winner: String
)