package xjunz.tool.mycard.monitor.push

import androidx.annotation.IntRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.model.Player
import xjunz.tool.mycard.monitor.push.DuelFilterCriteria.PlayerCriteria

/**
 * Contract: If any [PlayerCriteria] is not [limited][PlayerCriteria.isLimited], it should be
 * set to `null`.
 *
 * @author xjunz 2022/2/24
 */
@Serializable
data class DuelFilterCriteria(
    @IntRange(from = 0)
    var pushDelayInMinute: Int = 0,
    var onePlayerCriteria: PlayerCriteria? = null,// one player criteria should not equal to..
    var theOtherPlayerCriteria: PlayerCriteria? = null,//..the other player criteria.
    var isEnabled: Boolean = true
) {

    var id: String? = null

    /**
     * Call this to indicate that this instance is ready for persist storage. Hence, we will assign an id
     * to it.
     */
    fun ready(): DuelFilterCriteria {
        check(id == null)
        id = hashCode().toString()
        return this
    }

    fun deepClone(): DuelFilterCriteria {
        return copy(
            onePlayerCriteria = onePlayerCriteria?.copy(),
            theOtherPlayerCriteria = theOtherPlayerCriteria?.copy()
        )
    }

    @Transient
    private var updated = false

    fun markAsUpdated() {
        updated = true
    }

    fun getPlayerCriteria(@Duel.PlayerNumber which: Int) =
        if (which == 0) onePlayerCriteria else theOtherPlayerCriteria

    @Transient
    var formattedString: String? = null
        get() {
            if (field == null || updated) {
                field = PushCriteriaFormatter.format(this)
                updated = false
            }
            return field
        }

    @Serializable
    data class PlayerCriteria(
        var rankStart: Int = NO_LIMIT,
        var rankEnd: Int = NO_LIMIT,
        var requireFollowed: Boolean = false,
        var requiredTag: String? = null
    ) {

        companion object {
            const val NO_LIMIT = -1
            private val EMPTY = PlayerCriteria()
        }

        inline val isLimited get() = hashCode() != 0

        inline val isRankLimited get() = isStartRankLimited || isEndRankLimited

        inline val isStartRankLimited get() = rankStart != NO_LIMIT

        inline val isEndRankLimited get() = rankEnd != NO_LIMIT

        fun check(name: String, player: Player?): Boolean {
            // check followed
            if (requireFollowed && !PlayerInfoManager.isFollowed(name)) return false
            // check rank range
            if (isRankLimited) {
                if (player == null) return false
                val minRank = if (rankStart == NO_LIMIT) 1 else rankStart
                val maxRank = if (rankEnd == NO_LIMIT) Int.MAX_VALUE else rankEnd
                if (player.rank !in minRank..maxRank) return false
            }
            // check tag
            if (requiredTag != null && !PlayerInfoManager.getTags(name).contains(requiredTag!!)
            ) return false
            return true
        }

        override fun equals(other: Any?): Boolean {
            val theOther = other ?: EMPTY
            if (this === theOther) return true
            if (javaClass != theOther.javaClass) return false

            theOther as PlayerCriteria

            if (rankStart != theOther.rankStart) return false
            if (rankEnd != theOther.rankEnd) return false
            if (requireFollowed != theOther.requireFollowed) return false
            if (requiredTag != theOther.requiredTag) return false
            return true
        }

        override fun hashCode(): Int {
            var result = if (isStartRankLimited) rankStart else 0
            result = 31 * result + if (isEndRankLimited) rankEnd else 0
            result = 31 * result + if (requireFollowed) 1 else 0
            result = 31 * result + requiredTag.hashCode()
            return result
        }
    }

    fun checkConsideringDelay(duel: Duel): Boolean {
        if (!check(duel)) return false
        if (pushDelayInMinute != 0) {
            val timestamp =
                if (duel.startTimestamp == -1L) duel.localCreateTimestamp else duel.startTimestamp
            if (System.currentTimeMillis() - timestamp < pushDelayInMinute * 60_000) return false
        }
        return true
    }

    /**
     * Check whether a [duel] is expected to be notified.
     */
    fun check(duel: Duel): Boolean {
        if (!isEnabled) return false
        val p1c1 = onePlayerCriteria?.check(duel.player1Name, duel.player1) ?: true
        val p2c2 = theOtherPlayerCriteria?.check(duel.player2Name, duel.player2) ?: true
        // player 1 in the position 1 and player 2 in the position 2
        if (p1c1 && p2c2) return true
        val p1c2 = theOtherPlayerCriteria?.check(duel.player1Name, duel.player1) ?: true
        val p2c1 = onePlayerCriteria?.check(duel.player2Name, duel.player2) ?: true
        // player 1 in the position 2 and player 2 in the position 1
        if (p1c2 && p2c1) return true
        return false
    }

    fun encodeToJson(): String {
        return Json.encodeToString(this)
    }

    override fun equals(other: Any?): Boolean {
        val theOther = other ?: EMPTY
        if (this === theOther) return true
        if (theOther !is DuelFilterCriteria) return false

        if (pushDelayInMinute != theOther.pushDelayInMinute) return false
        if (onePlayerCriteria != theOther.onePlayerCriteria) return false
        if (theOtherPlayerCriteria != theOther.theOtherPlayerCriteria) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pushDelayInMinute
        result = 31 * result + (onePlayerCriteria?.hashCode() ?: 0)
        result = 31 * result + (theOtherPlayerCriteria?.hashCode() ?: 0)
        return result
    }


    companion object {

        fun parseFromJson(json: String): DuelFilterCriteria {
            return Configs.LenientJson.decodeFromString(json)
        }

        private val EMPTY = DuelFilterCriteria()

        val PRESET_CRITERIA_ARRAY by lazy {
            arrayOf(
                DuelFilterCriteria(
                    onePlayerCriteria = PlayerCriteria(requireFollowed = true),
                    theOtherPlayerCriteria = null
                ),
                DuelFilterCriteria(
                    onePlayerCriteria = PlayerCriteria(1, 100),
                    theOtherPlayerCriteria = PlayerCriteria(1, 100)
                ),
                DuelFilterCriteria(
                    onePlayerCriteria = PlayerCriteria(1, 2000),
                    theOtherPlayerCriteria = PlayerCriteria(1, 2000),
                    pushDelayInMinute = 30
                )
            )
        }
    }
}