package xjunz.tool.mycard.model

import androidx.annotation.IntDef
import java.io.Serializable

/**
 * @author xjunz 2022/2/24
 */
data class Duel(
    val id: String,
    val ordinal: Int,
    val player1Name: String,
    val player2Name: String,
    var startTimestamp: Long = -1,
    var player1: Player? = null,
    var player2: Player? = null
) : Comparable<Duel>, Serializable {

    companion object {
        const val PLAYER_1 = 0
        const val PLAYER_2 = 1
        val PLAYERS = PLAYER_1..PLAYER_2
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(PLAYER_1, PLAYER_2)
    annotation class PlayerNumber

    fun requirePlayerName(@PlayerNumber number: Int) = when (number) {
        PLAYER_1 -> player1Name
        PLAYER_2 -> player2Name
        else -> throw IllegalArgumentException("illegal player number $number")
    }

    fun containsPlayer(name: String): Boolean {
        return player1Name == name || player2Name == name
    }

    /**
     * Set a player info to the duel. If all players' info are set, we will check the push
     * criteria.
     */
    fun setPlayer(@PlayerNumber number: Int, player: Player) {
        when (number) {
            PLAYER_1 -> player1 = player
            PLAYER_2 -> player2 = player
            else -> throw IllegalArgumentException("illegal player number $number")
        }
    }

    fun getPlayer(@PlayerNumber number: Int) = when (number) {
        PLAYER_1 -> player1
        PLAYER_2 -> player2
        else -> throw IllegalArgumentException("illegal player number $number")
    }

    fun requirePlayer(@PlayerNumber number: Int) = when (number) {
        PLAYER_1 -> player1!!
        PLAYER_2 -> player2!!
        else -> throw IllegalArgumentException("illegal player number $number")
    }

    var endTimestamp: Long = -1

    inline val areAllPlayersLoaded get() = player1 != null && player2 != null

    inline val duration: Long
        get() {
            check(startTimestamp != -1L)
            check(endTimestamp != -1L)
            return endTimestamp - startTimestamp
        }

    inline val isStartTimeUnknown get() = startTimestamp == -1L

    inline val isEnded get() = endTimestamp != -1L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Duel
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "$id: [$player1Name] vs [$player2Name]"
    }

    override fun compareTo(other: Duel): Int {
        return ordinal.compareTo(other.ordinal)
    }
}