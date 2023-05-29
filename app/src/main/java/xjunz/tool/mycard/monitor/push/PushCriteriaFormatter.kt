package xjunz.tool.mycard.monitor.push

import xjunz.tool.mycard.R
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.resArray
import xjunz.tool.mycard.ktx.resStr

object PushCriteriaFormatter {

    private const val NL = DuelFilterCriteria.PlayerCriteria.NO_LIMIT

    private val oneTheOther = R.array.one_the_other.resArray

    private val SEPARATOR = R.string.criteria_separator.resStr

    fun format(criteria: DuelFilterCriteria): String {
        val all = listOf(criteria.onePlayerCriteria, criteria.theOtherPlayerCriteria)
        val sb = StringBuilder()
        val nonNullCount = all.count { it != null }
        var formattedCount = 0
        all.forEach {
            if (it == null) return@forEach
            if (formattedCount == 1) {
                sb.append(R.string.criteria_condition_and_prefix.resStr)
            }
            sb.append(
                R.string.format_criteria_prefix.format(
                    oneTheOther[if (nonNullCount == 2) formattedCount else oneTheOther.lastIndex]
                )
            )
            var anyBefore = false
            if (it.isRankLimited) {
                anyBefore = true
                // rank start is at least 1, so convert NO_LIMIT to 1
                val rankStart = if (it.rankStart == NL) 1 else it.rankStart
                val rankEnd = if (it.rankEnd == NL) "+∞" else it.rankEnd.toString()
                val endBracket = if (it.rankEnd == NL) ")" else "]"
                sb.append(
                    R.string.format_criteria_rank_range.format(rankStart, rankEnd, endBracket)
                )
            }
            if (it.requireFollowed) {
                if (anyBefore) sb.append(SEPARATOR)
                anyBefore = true
                sb.append(R.string.criteria_followed.resStr)
            }
            val tag = it.requiredTag
            if (!tag.isNullOrEmpty()) {
                if (anyBefore) sb.append(SEPARATOR)
                anyBefore = true
                sb.append(R.string.criteria_tags.resStr).append(" [")
                sb.append(tag)
                sb.append("]")
            }
            check(anyBefore) { "Found an empty but non-null criteria?" }
            sb.append(R.string.criteria_end.resStr)
            formattedCount++
        }
        if (criteria.pushDelayInMinute != 0) {
            sb.append(" ").append(
                R.string.format_push_delay.format(criteria.pushDelayInMinute)
            ).append(R.string.criteria_end.resStr)
        }
        return sb.toString()
    }
}