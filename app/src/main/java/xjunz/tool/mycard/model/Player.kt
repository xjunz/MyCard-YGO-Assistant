package xjunz.tool.mycard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Player(
    @SerialName("arena_rank")
    override var rank: Int,
    override val athletic_all: Int,
    override val athletic_draw: Int,
    override val athletic_lose: Int,
    override val athletic_win: Int,
    override val pt: Float,
    override val exp: Float,
    val athletic_wl_ratio: Float,
) : BasePlayer(), java.io.Serializable {

    @Transient
    override lateinit var name: String

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
