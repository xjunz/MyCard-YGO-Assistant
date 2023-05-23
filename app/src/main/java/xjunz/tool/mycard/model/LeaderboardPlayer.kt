package xjunz.tool.mycard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class LeaderboardPlayer(
    override val athletic_all: Int,
    override val athletic_draw: Int,
    override val athletic_lose: Int,
    override val athletic_win: Int,
    override val exp: Float,
    override val pt: Float,
    @SerialName("username")
    override var name: String
) : BasePlayer() {

    @Transient
    override var rank: Int = -1

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