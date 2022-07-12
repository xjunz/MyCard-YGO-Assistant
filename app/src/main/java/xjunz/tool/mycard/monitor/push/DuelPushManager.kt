package xjunz.tool.mycard.monitor.push

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.service.notification.StatusBarNotification
import androidx.collection.ArrayMap
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.postDelayed
import androidx.core.text.parseAsHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.resText
import xjunz.tool.mycard.main.detail.DuelDetailsActivity
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.util.PendingIntentCompat
import xjunz.tool.mycard.util.errorLog
import xjunz.tool.mycard.util.printLog

object DuelPushManager {

    const val CHECK_PERIOD = 60_000L

    private const val REMOVE_RESULT_NOTIFICATION_DELAY = 30_000L

    private const val CHANNEL_ID_DUEL_PUSH = "duel_push"

    private const val CHECKED_DUEL_PUSH_ID = 0x8E4
    private const val SELF_DUEL_PUSH_ID = 0x8E5

    private val notificationManagerCompat by lazy {
        NotificationManagerCompat.from(app)
    }

    /**
     * Ids of duels which have already been pushed and currently shown as [StatusBarNotification].
     */
    val shownPushes: List<StatusBarNotification>
        get() = app.getSystemService(NotificationManager::class.java).activeNotifications.filter {
            it.id == CHECKED_DUEL_PUSH_ID
        }

    private val pendingPushes by lazy {
        ArrayMap<String, PendingPush>()
    }

    private data class PendingPush(
        var duel: Duel,
        val criteria: DuelPushCriteria,
        var isSuffocated: Boolean = false
    ) {
        inline val remainingMills
            get() = criteria.pushDelayInMinute * 60_000 - (System.currentTimeMillis() - duel.startTimestamp)
    }

    private val pushHandler by lazy {
        object : Handler(Looper.getMainLooper()) {

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                val push = pendingPushes[msg.obj as String] ?: return
                val duel = push.duel
                if (duel.isEnded) {
                    pendingPushes.remove(duel.id)
                    return
                }
                if (push.remainingMills <= 0) {
                    if (!push.isSuffocated) {
                        val notification = buildCheckedPushNotification(duel, push.criteria)
                        if (shownPushes.size == Configs.MAX_SHOWN_PUSHES_COUNT) {
                            notificationManagerCompat.cancel(
                                shownPushes[0].tag, CHECKED_DUEL_PUSH_ID
                            )
                        }
                        notificationManagerCompat.notify(
                            duel.id, CHECKED_DUEL_PUSH_ID, notification
                        )
                        removeCallbacks(deadPushesClearTask)
                        post(deadPushesClearTask)
                    }
                    pendingPushes.remove(duel.id)
                    return
                }
                sendMessageDelayed(Message.obtain(msg), CHECK_PERIOD)
            }
        }
    }

    val ALL_CRITERIA get() = allCriteria as List<DuelPushCriteria> // exposed as immutable

    private var allCriteria: MutableList<DuelPushCriteria> = mutableListOf()

    private val sharedPrefs by lazy {
        app.sharedPrefsOf("push")
    }

    var isInitialized = false

    suspend fun initIfNeeded() {
        if (isInitialized) return
        val pushChannel = NotificationChannelCompat.Builder(
            CHANNEL_ID_DUEL_PUSH, NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName(R.string.duel_push.resText).build()
        notificationManagerCompat.createNotificationChannel(pushChannel)
        withContext(Dispatchers.Default) {
            sharedPrefs.all.asSequence().filter { it.value is CharSequence }.forEach {
                try {
                    allCriteria.add(DuelPushCriteria.parseFromJson(it.value as String))
                } catch (t: Throwable) {
                    errorLog("Remove an invalid criteria: ${it.value}.")
                    sharedPrefs.edit().remove(it.key).apply()
                }
            }
            isInitialized = true
        }
    }

    private val deadPushesClearTask: Runnable = object : Runnable {
        override fun run() {
            shownPushes.forEach {
                if (System.currentTimeMillis() - it.postTime >= Configs.MAX_SHOWN_PUSH_ALIVE_MILLS) {
                    notificationManagerCompat.cancel(it.tag, it.id)
                }
            }
            pushHandler.postDelayed(this, 10_000)
        }
    }

    /**
     * "Suffocate" all pending pushes. When a pending push is due, a suffocated push would not trigger
     * a notification but simply be removed.
     */
    fun suffocateAll() {
        pendingPushes.forEach {
            it.value.isSuffocated = true
        }
    }

    /**
     * Cancel a pending push and remove the notification if already pushed.
     */
    fun Duel.cancelPush() {
        pendingPushes.remove(id)
        pushHandler.removeCallbacksAndMessages(id)
        notificationManagerCompat.cancel(id, CHECKED_DUEL_PUSH_ID)
    }

    fun Duel.cancelSelfPush() {
        notificationManagerCompat.cancel(id, SELF_DUEL_PUSH_ID)
    }

    /**
     * Awake all suffocated pending pushes that exist in the [survivors] and remove those not.
     */
    fun awakeSurvivors(survivors: List<Duel>) {
        val iterator = pendingPushes.iterator()
        while (iterator.hasNext()) {
            val push = iterator.next().value
            val index = survivors.indexOf(push.duel)
            if (index != -1) {
                push.isSuffocated = false
                push.duel = survivors[index]
            } else {
                iterator.remove()
            }
        }
    }

    /**
     * Cancel all pending pushes and remove all notifications if already pushed.
     */
    fun cancelAll() {
        pendingPushes.clear()
        pushHandler.removeCallbacksAndMessages(null)
        notificationManagerCompat.cancelAll()
    }

    private fun createPushMessage(duel: Duel): Message {
        val msg = Message.obtain()
        msg.what = duel.hashCode()
        msg.obj = duel.id
        msg.target = pushHandler
        return msg
    }

    /**
     * Check whether a duel fits any push criteria.
     */
    fun Duel.checkCriteria(): Boolean {
        check(isInitialized)
        return allCriteria.any { it.check(this) }
    }

    fun Duel.checkCriteriaAndPush() {
        check(isInitialized)
        allCriteria.forEach {
            if (it.check(this)) {
                pendingPushes[id] = PendingPush(this, it)
                createPushMessage(this).sendToTarget()
                return
            }
        }
    }

    fun Duel.pushSelfInvolved() {
        val notification = buildPushNotification(
            this, R.string.match_successfully.resText,
        )
        notificationManagerCompat.notify(id, SELF_DUEL_PUSH_ID, notification)
    }

    fun Duel.notifyResult() {
        val notification = buildResultNotification(this)
        notificationManagerCompat.notify(id, SELF_DUEL_PUSH_ID, notification)
        // auto remove the result notification, because it is time-sensitive
        pushHandler.postDelayed(REMOVE_RESULT_NOTIFICATION_DELAY) {
            printLog("Remove result notification")
            notificationManagerCompat.cancel(id, SELF_DUEL_PUSH_ID)
        }
    }

    private fun DuelPushCriteria.persist() {
        sharedPrefs.edit().putString(id, encodeToJson()).apply()
    }

    fun DuelPushCriteria.update() {
        markAsUpdated()
        val iterator = pendingPushes.iterator()
        // remove pushes that no longer match the criteria
        while (iterator.hasNext()) {
            val push = iterator.next().value
            if (push.criteria === this) {
                if (push.remainingMills < 0 || !push.criteria.check(push.duel)) {
                    iterator.remove()
                    push.duel.cancelPush()
                }
            }
        }
        persist()
    }

    /**
     * Remove pending pushes that no longer match any criteria. Call this when a player's tags or
     * following state is changed.
     */
    fun removeUnmatchedPendingPushes() {
        val iterator = pendingPushes.iterator()
        while (iterator.hasNext()) {
            val push = iterator.next().value
            if (!push.duel.checkCriteria()) {
                iterator.remove()
                push.duel.cancelPush()
            }
        }
    }

    fun DuelPushCriteria.addToAll() {
        allCriteria.add(ready())
        persist()
    }

    fun DuelPushCriteria.removeFromAll() {
        allCriteria.remove(this)
        sharedPrefs.edit().remove(id).apply()
        val iterator = pendingPushes.iterator()
        // remove pending pushes that match this criteria
        while (iterator.hasNext()) {
            val push = iterator.next().value
            if (push.criteria === this) {
                iterator.remove()
                push.duel.cancelPush()
            }
        }
    }

    private fun formatTags(tags: List<String>): String {
        check(tags.isNotEmpty())
        val sb = StringBuilder()
        for (i in 0..tags.lastIndex) {
            sb.append(tags[i]).append("•")
        }
        sb.deleteAt(sb.lastIndex)
        return sb.toString()
    }

    private fun buildResultNotification(duel: Duel): Notification {
        val detailIntent = PendingIntent.getActivity(
            app, -duel.hashCode() + 2,
            Intent(app, DuelDetailsActivity::class.java)
                .putExtra(DuelDetailsActivity.EXTRA_DUEL, duel)
                .putExtra(DuelDetailsActivity.EXTRA_FROM_NOTIFICATION, true)
                .putExtra(DuelDetailsActivity.EXTRA_SHOW_RESULT, true),
            PendingIntentCompat.makeFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
        return NotificationCompat.Builder(app, CHANNEL_ID_DUEL_PUSH)
            .setSmallIcon(R.drawable.ic_sword_cross)
            .setContentIntent(detailIntent)
            .setContentTitle(R.string.duel_is_ended.resText)
            .setContentText(R.string.tap_to_check_details.resText)
            .setOngoing(false)
            .setDefaults(Notification.DEFAULT_ALL)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis()).build()
    }

    private fun buildPushNotification(
        duel: Duel, content: CharSequence
    ): Notification {
        val detailIntent = PendingIntent.getActivity(
            app, duel.hashCode(),
            Intent(app, DuelDetailsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(DuelDetailsActivity.EXTRA_DUEL, duel)
                .putExtra(DuelDetailsActivity.EXTRA_FROM_NOTIFICATION, true),
            PendingIntentCompat.makeFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
        val spectateIntent = PendingIntent.getActivity(
            app, -duel.hashCode() + 1 /* make different */,
            Intent(app, DuelDetailsActivity::class.java)
                .putExtra(DuelDetailsActivity.EXTRA_DUEL, duel)
                .putExtra(DuelDetailsActivity.EXTRA_FROM_NOTIFICATION, true)
                .putExtra(DuelDetailsActivity.EXTRA_DIRECTLY_SPECTATE, true),
            PendingIntentCompat.makeFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
        val title = R.string.html_push_title.format(
            duel.player1Name, duel.requirePlayer(Duel.PLAYER_1).rank,
            duel.player2Name, duel.requirePlayer(Duel.PLAYER_2).rank
        ).parseAsHtml()
        return NotificationCompat.Builder(app, CHANNEL_ID_DUEL_PUSH).addAction(
            NotificationCompat.Action(
                IconCompat.createWithResource(app, R.drawable.ic_twotone_eye_24),
                R.string.spectate.resText, spectateIntent
            )
        ).setSmallIcon(R.drawable.ic_sword_cross)
            .setContentIntent(detailIntent)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(false)
            .setDefaults(Notification.DEFAULT_ALL)
            .setShowWhen(true)
            .setWhen(duel.startTimestamp).build()
    }

    private fun buildCheckedPushNotification(duel: Duel, criteria: DuelPushCriteria): Notification {
        val destTags = mutableSetOf<String>()
        criteria.onePlayerCriteria?.requiredTag?.let { destTags.add(it) }
        criteria.theOtherPlayerCriteria?.requiredTag?.let { destTags.add(it) }
        val sourceTags = mutableListOf<String>()
        sourceTags.addAll(PlayerInfoManager.getTags(duel.player1Name))
        sourceTags.addAll(PlayerInfoManager.getTags(duel.player2Name))
        val hitTags = destTags.filter {
            sourceTags.contains(it)
        }

        val duration = if (criteria.pushDelayInMinute != 0)
            R.string.html_push_delayed.format(criteria.pushDelayInMinute) else ""
        val content =
            if (hitTags.isEmpty()) R.string.format_push_content.format(duration).parseAsHtml()
            else R.string.html_push_content_tagged.format(formatTags(hitTags), duration)
                .parseAsHtml()
        return buildPushNotification(duel, content)
    }

    fun toggleWhiteHotPush(shouldPush: Boolean) {

    }
}