package xjunz.tool.mycard.info

import android.util.LruCache
import androidx.collection.ArraySet
import androidx.core.util.lruCache
import xjunz.tool.mycard.Constants
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.ktx.resStr
import xjunz.tool.mycard.main.account.AccountManager
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.monitor.push.DuelPushManager

/**
 * @author xjunz 2022/2/25
 */
object PlayerInfoManager {

    private val tagSp by lazy {
        app.sharedPrefsOf("tags")
    }

    private val followedSp by lazy {
        app.sharedPrefsOf("followed")
    }

    private val tagCache by lazy { lruCache<String, List<String>>(50, { _, v -> v.size }) }

    private inline val String.tagSpKey get() = this + "_tags"

    fun getTags(name: String?): List<String> {
        if (name == null) return emptyList()
        var tags = tagCache[name]
        if (tags == null && tagSp.contains(name.tagSpKey)) {
            val tagSequence = tagSp.getString(name.tagSpKey, null)!!
            tags = tagSequence.split('|').filter { it.isNotBlank() }
            if (tags.isEmpty()) tagSp.edit().remove(name.tagSpKey).apply()
            else tagCache[name] = tags
        }
        if (tags == null) tags = emptyList()
        // attach a special tag to the developer :)
        if (name == Constants.DEVELOPER_NAME) {
            val myTag = R.string.developer.resStr
            if (tags.isEmpty()) tags = mutableListOf(myTag)
            else if (!tags.contains(myTag)) (tags as MutableList).add(0, myTag)
        }
        return tags
    }

    fun getTaggedPlayers(): List<String> {
        val players = mutableListOf<String>()
        tagSp.all.forEach {
            if (it.key.endsWith("_tags")) players.add(it.key.removeSuffix("_tags"))
        }
        return players
    }

    fun getAllDistinctTags(): List<String> {
        val set = ArraySet<String>()
        tagSp.all.forEach { entry ->
            val tags = (entry.value as String).split('|').filter { it.isNotBlank() }
            if (tags.isNotEmpty()) {
                set.addAll(tags)
            }
        }
        return set.toList()
    }

    fun getFollowedPlayers(): List<String> {
        return followedSp.getStringSet(SP_KEY_FOLLOWED_PLAYERS, emptySet())!!.toList()
    }

    fun renameTag(name: String, from: String, to: String): Int {
        val cache = tagCache[name]
        if (cache.isNullOrEmpty()) return -1
        val index = cache.indexOf(from)
        if (index < 0) return -1
        (cache as MutableList)[index] = to
        val key = name.tagSpKey
        val tagSequence = tagSp.getString(key, null) ?: return -1
        val newSequence = tagSequence.replace("$from|", "$to|")
        tagSp.edit().putString(key, newSequence).apply()
        DuelPushManager.removeUnmatchedPendingPushes()
        return index
    }

    fun insertTag(name: String, tag: String) {
        val key = name.tagSpKey
        val cache = tagCache[name]
        if (cache.isNullOrEmpty()) {
            tagCache[name] = mutableListOf(tag)
            tagSp.edit().putString(key, "$tag|").apply()
        } else {
            (cache as MutableList).add(0, tag)
            val tagSequence = tagSp.getString(key, null)
            tagSp.edit().putString(key, "$tag|$tagSequence").apply()
        }
    }

    fun removeTag(tag: String): List<String> {
        val victims = mutableListOf<String>()
        getTaggedPlayers().forEach {
            if (removeTag(it, tag)) {
                victims.add(it)
            }
        }
        return victims.takeIf { it.isNotEmpty() } ?: emptyList()
    }

    fun removeTag(name: String, tag: String): Boolean {
        val cache = tagCache[name]
        if (!cache.isNullOrEmpty()) {
            (cache as MutableList).remove(tag)
            if (cache.isEmpty()) tagCache.remove(name)
        }
        val key = name.tagSpKey
        val tagSequence = tagSp.getString(key, null) ?: return false
        val index = tagSequence.indexOf("$tag|")
        if (index < 0) return false
        val newSequence = tagSequence.removeRange(index, index + tag.length + 1)
        if (newSequence.isEmpty()) {
            tagSp.edit().remove(key).apply()
        } else {
            tagSp.edit().putString(key, newSequence).apply()
        }
        DuelPushManager.removeUnmatchedPendingPushes()
        return true
    }

    private val followed: MutableSet<String> by lazy {
        ArraySet(followedSp.getStringSet(SP_KEY_FOLLOWED_PLAYERS, null))
    }

    private const val SP_KEY_FOLLOWED_PLAYERS = "followed"

    fun toggleFollowingState(name: String) {
        if (isFollowed(name)) followed.remove(name) else followed.add(name)
        followedSp.edit().putStringSet(SP_KEY_FOLLOWED_PLAYERS, followed).apply()
        DuelPushManager.removeUnmatchedPendingPushes()
    }

    fun unfollowAll() {
        followedSp.edit().remove(SP_KEY_FOLLOWED_PLAYERS).apply()
        followed.clear()
        DuelPushManager.removeUnmatchedPendingPushes()
    }

    fun clearAllTags() {
        tagSp.edit().clear().apply()
        tagCache.evictAll()
    }

    fun compatFollowAll(set: Set<String>) {
        followedSp.edit().putStringSet(SP_KEY_FOLLOWED_PLAYERS, set).apply()
    }

    fun Duel.isFollowed(): Boolean {
        return isFollowed(player1Name) || isFollowed(player2Name)
    }

    fun isFollowed(name: String): Boolean {
        return (AccountManager.hasLogin()
                && AccountManager.reqUsername() == name) || followed.contains(name)
    }

    private operator fun <K, V> LruCache<K, V>.set(k: K, value: V) = put(k, value)

}


