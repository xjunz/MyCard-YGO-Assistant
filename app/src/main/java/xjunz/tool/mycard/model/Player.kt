package xjunz.tool.mycard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Player(
    @SerialName("arena_rank") val rank: Int,
    val athletic_all: Int,
    val athletic_draw: Int,
    val athletic_lose: Int,
    val athletic_win: Int,
    val athletic_wl_ratio: Float,
    val entertain_all: Int,
    val entertain_draw: Int,
    val entertain_lose: Int,
    val entertain_win: Int,
    val entertain_wl_ratio: Float,
    val exp: Int,
    val exp_rank: Int,
    val pt: Int
) : java.io.Serializable {

    @Transient
    lateinit var name: String

    val isNewcomer get() = rank == 0

    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Player

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}
