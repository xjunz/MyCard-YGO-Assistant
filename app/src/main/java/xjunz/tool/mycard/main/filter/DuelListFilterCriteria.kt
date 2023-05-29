package xjunz.tool.mycard.main.filter

import kotlinx.serialization.Serializable
import xjunz.tool.mycard.monitor.push.DuelFilterCriteria

/**
 * @author xjunz 2023/05/25
 */
@Serializable
data class DuelListFilterCriteria(
    var sortBy: Int = SORT_BY_DEFAULT,
    var keyword: String? = null,
    var isAscending: Boolean = true,
    var minimumDurationMinute: Int = 0,
    var duelCriteria: DuelFilterCriteria? = null
) {

    companion object {
        private val EMPTY = DuelFilterCriteria()
        const val SORT_BY_DEFAULT = 0
        const val SORT_BY_RANK_BEST = 1
        const val SORT_BY_RANK_SUM = 2
        const val SORT_BY_RANK_DIFF = 3
        const val SORT_BY_WIN_RATE_BEST = 4
        const val SORT_BY_WIN_RATE_SUM = 5
        const val SORT_BY_WIN_RATE_DIFF = 6
    }

    override fun equals(other: Any?): Boolean {
        val theOther = other ?: EMPTY
        if (this === theOther) return true
        if (theOther !is DuelListFilterCriteria) return false

        if (sortBy != theOther.sortBy) return false
        if (keyword != theOther.keyword) return false
        if (isAscending != theOther.isAscending) return false
        if (minimumDurationMinute != theOther.minimumDurationMinute) return false
        if (duelCriteria != theOther.duelCriteria) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sortBy
        result = 31 * result + isAscending.hashCode()
        result = 31 * result + keyword.hashCode()
        result = 31 * result + minimumDurationMinute
        result = 31 * result + (duelCriteria?.hashCode() ?: 0)

        return result
    }

    fun deepClone(): DuelListFilterCriteria {
        return copy(duelCriteria = duelCriteria?.deepClone())
    }
}