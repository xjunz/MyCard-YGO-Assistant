package xjunz.tool.mycard.main.tools

import android.util.SparseIntArray
import androidx.annotation.IntRange
import androidx.core.util.set
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
class ConditionSet(
    val conditions: MutableList<Condition>,
    var deckCardCount: Int,
    var label: String
) {

    fun copy(): ConditionSet {
        return ConditionSet(
            Array(conditions.size) {
                conditions[it].copy()
            }.toMutableList(), deckCardCount, label
        )
    }

    @Transient
    private lateinit var copy: Array<Condition>

    /**
     * The drawn card counts of collections
     */
    @Transient
    private val consumptions = SparseIntArray(conditions.size)

    /**
     * The cumulative possible occurrence count
     */
    @Transient
    private var occurrence = 0L

    /**
     * The total specified card count. Specified cards cannot be drawn under unspecified conditions.
     */
    @Transient
    private var totalSpecified = 0

    /**
     * The total excluded card count. Excluded cards cannot be drawn under unspecified conditions.
     */
    @Transient
    private var totalExcluded = 0

    /**
     * The current preserved card count, which decrement by 1 on every specified draw.
     */
    @Transient
    private var preserved = 0

    @Transient
    private var result: BigDecimal? = null

    @Transient
    private var shouldStop = false


    private fun cleanUp() {
        consumptions.clear()
        shouldStop = false
        totalExcluded = 0
        totalSpecified = 0
        occurrence = 0L
        preserved = 0
        copy = Array(conditions.size) {
            conditions[it].copy()
        }
    }

    private fun generateIndexes(): IntArray {
        return IntArray(copy.size) { index ->
            copy.indexOfFirst {
                it.id == copy[index].id
            }
        }.sortedArray()
    }

    fun getResult(): BigDecimal? {
        return result
    }

    fun cancelCalculating() {
        shouldStop = true
    }

    /**
     * Calculate the probability of hand cards that matches the [copy]. This operation is O(n!)
     * time complexity, so this might be time consuming as the [copy] count increase.
     */
    fun calculateProbability() {
        cleanUp()

        preprocessConditions()

        val indexes = generateIndexes()

        do {
            if (shouldStop) return
            onNextPermutation(indexes)
        } while (nextPermutation(indexes))

        var total = 1L.toBigDecimal()
        for (i in 0 until conditions.size) {
            total *= (deckCardCount - i).toBigDecimal()
        }
        result = occurrence.toBigDecimal().divide(total, 10, RoundingMode.HALF_EVEN)
    }

    private fun preprocessConditions() {
        val counted = mutableListOf<String?>()
        copy.forEachIndexed { i, c ->
            val bounded =
                c.type == Condition.TYPE_SPEC_IN_DECK || c.type == Condition.TYPE_ANY_IN_COLLECTION
            c.isSpecified = !c.isInverted && bounded
            if (bounded) {
                if (c.type == Condition.TYPE_SPEC_IN_DECK) {
                    c.collectionCount = 1
                    if (!c.isInverted) {
                        c.id = i
                    }
                } else if (!c.isInverted) {
                    c.id = c.collectionName.hashCode()
                }
                if (!counted.contains(c.collectionName)) {
                    if (c.isInverted) {
                        totalExcluded += c.collectionCount
                    } else {
                        totalSpecified += c.collectionCount
                    }
                    counted.add(c.collectionName)
                }
            }
        }
    }

    /**
     * Get the occurrence count of a conditioned hand when it is the [N]th draw.
     * N is started from 0.
     */
    private fun Condition.getOccurrenceCount(@IntRange(from = 0) N: Int): Int {
        val count: Int
        if (isSpecified) {
            val consumed = consumptions[id]
            consumptions[id] = consumed + 1
            count = collectionCount - consumed
            preserved--
            check(count >= 0) { "over consumed" }
        } else {
            count = deckCardCount - N - preserved - totalExcluded
            check(count >= 0) { "no card to draw" }
        }
        return count
    }

    private fun onNextPermutation(permutation: IntArray) {
        preserved = totalSpecified
        consumptions.clear()
        var count = 1L
        for (i in permutation.indices) {
            count *= copy[permutation[i]].getOccurrenceCount(i)
        }
        occurrence += count
    }

    private fun nextPermutation(array: IntArray): Boolean {
        while (true) {
            var j = -1
            for (i in array.size - 2 downTo 0) {
                if (array[i] < array[i + 1]) {
                    j = i
                    break
                }
            }
            if (j == -1) return false
            var k = -1
            var min = Int.MAX_VALUE
            for (i in j until array.size) {
                if (array[i] > array[j] && array[i] <= min) {
                    min = array[i]
                    k = i
                }
            }
            val tmp = array[j]
            array[j] = array[k]
            array[k] = tmp
            var left = j + 1
            var right = array.size - 1
            while (left < right) {
                val t = array[left]
                array[left] = array[right]
                array[right] = t
                left++
                right--
            }
            return true
        }
    }
}