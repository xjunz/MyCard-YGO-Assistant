package xjunz.tool.mycard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LeaderboardPlayer(
    val athletic_all: Int,
    val athletic_draw: Int,
    val athletic_lose: Int,
    val athletic_win: Int,
    val entertain_all: Int,
    val entertain_draw: Int,
    val entertain_lose: Int,
    val entertain_win: Int,
    val exp: Double,
    val id: Int? = null,
    val pt: Double,
    @SerialName("username") val name: String
) {
    @Transient
    var rank: Int = -1
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LeaderboardPlayer

        if (athletic_all != other.athletic_all) return false
        if (athletic_draw != other.athletic_draw) return false
        if (athletic_lose != other.athletic_lose) return false
        if (athletic_win != other.athletic_win) return false
        if (pt != other.pt) return false
        if (name != other.name) return false
        if (rank != other.rank) return false

        return true
    }

    override fun hashCode(): Int {
        var result = athletic_all
        result = 31 * result + athletic_draw
        result = 31 * result + athletic_lose
        result = 31 * result + athletic_win
        result = 31 * result + pt.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + rank
        return result
    }


}