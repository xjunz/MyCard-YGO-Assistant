package xjunz.tool.mycard.info

import androidx.collection.ArrayMap
import xjunz.tool.mycard.model.Duel

object DuelRepository {

    private val fullCache by lazy {
        ArrayMap<String, Duel>()
    }

    fun clearAllCache() {
        fullCache.clear()
    }

    /**
     * Check whether a duel is worthy to be cached.
     */
    private fun Duel.isWorthy(): Boolean {
        return player1 != null || player2 != null || !isStartTimeUnknown
    }

    /**
     * Cache all duels worthy to be cached in a list.
     */
    fun List<Duel>.cacheAll() {
        forEach {
            if (it.isWorthy()) fullCache[it.id] = it
        }
    }

    /**
     * Try to fulfill a duel from cache if exists.
     */
    fun Duel.tryReadFromCache() {
        fullCache[id]?.let {
            if (id != it.id) return
            player1 = it.player1
            player2 = it.player2
            startTimestamp = it.startTimestamp
        }
    }

    fun Duel.removeFromCache() {
        fullCache.remove(id)
    }
}