package xjunz.tool.mycard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DuelRecord(
    val end_time: String,
    @SerialName("isfirstwin")
    val isFirstWin: Boolean,
    val pta: Double,
    val pta_ex: Double,
    val ptb: Double,
    val ptb_ex: Double,
    val start_time: String,
    val type: String,
    @SerialName("usernamea")
    val playerName1: String,
    @SerialName("usernameb")
    val playerName2: String,
    val winner: String
)