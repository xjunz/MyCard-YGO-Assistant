package xjunz.tool.mycard.monitor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import xjunz.tool.mycard.R
import xjunz.tool.mycard.info.PlayerInfoLoaderClient
import xjunz.tool.mycard.ktx.resStr
import xjunz.tool.mycard.ktx.resText
import xjunz.tool.mycard.main.MainActivity
import xjunz.tool.mycard.main.account.AccountManager
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.monitor.push.DuelPushManager
import xjunz.tool.mycard.monitor.push.DuelPushManager.cancelPush
import xjunz.tool.mycard.monitor.push.DuelPushManager.checkAllCriteriaAndPush
import xjunz.tool.mycard.monitor.push.DuelPushManager.notifyResult
import xjunz.tool.mycard.monitor.push.DuelPushManager.pushSelfInvolved
import xjunz.tool.mycard.util.CompositeCloseable

/**
 * @author xjunz 2022/2/23
 */
class DuelMonitorService : Service(), DuelMonitorEventObserver {

    private companion object {
        const val CHANNEL_ID_FOREGROUND = "foreground"

        const val ACTION_STOP_MONITOR = "xjunz.action.STOP_MONITOR"

        const val ACTION_RESTART_MONITOR = "xjunz.action.RESTART_MONITOR"

        val FOREGROUND_SERVICE_ID = "DuelMonitorService".sumOf { it.code }
    }

    private val closeables = CompositeCloseable()

    private val playerInfoLoader by closeables.lazilyCompose {
        PlayerInfoLoaderClient()
    }

    private val wssClient by closeables.lazilyCompose {
        DuelMonitorClient()
    }

    private val mainScope by lazy {
        closeables.composeAny(MainScope() + SupervisorJob()) { cancel() }
    }

    private val notificationManager by lazy {
        NotificationManagerCompat.from(this)
    }

    /**
     * The binder for the communication between components.
     */
    private val binder by lazy { DuelMonitorBinder() }

    /**
     * A [Binder] holding the states and data of the host service.
     */
    inner class DuelMonitorBinder : Binder(), DuelMonitorDelegate by wssClient {

        val playerInfoLoader = this@DuelMonitorService.playerInfoLoader

        fun observeEventSticky(observer: DuelMonitorEventObserver) {
            if (wssClient.isInitialized) observer.onInitialized(duelList)
            addEventObserverIfAbsent(observer)
        }

        /**
         * Only stop the monitor without stopping the foreground service. You can restart it later.
         * If not and no activity is bounded to it, it will be recycled by the system.
         */
        fun stopMonitor() {
            wssClient.state.removeObserver(stateObserver)
            ServiceCompat.stopForeground(
                this@DuelMonitorService, ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            wssClient.cancelIfNeeded()
        }

        @SuppressLint("MissingPermission")
        private val stateObserver = Observer<Int> {
            notificationManager.notify(FOREGROUND_SERVICE_ID, buildForegroundNotification(it))
        }

        fun startMonitor() {
            startForeground(
                FOREGROUND_SERVICE_ID, buildForegroundNotification(State.DISCONNECTED_IDLE)
            )
            wssClient.activateIfNeeded()
            wssClient.addEventObserverIfAbsent(this@DuelMonitorService)
            wssClient.state.removeObserver(stateObserver)
            wssClient.state.observeForever(stateObserver)
        }
    }

    override fun onBind(intent: Intent?) = binder

    override fun onCreate() {
        super.onCreate()
        val foregroundChannel = NotificationChannelCompat.Builder(
            CHANNEL_ID_FOREGROUND, NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).setName(applicationInfo.labelRes.resText).setShowBadge(false).build()
        notificationManager.createNotificationChannel(foregroundChannel)
        AccountManager.initIfNeeded()
        mainScope.launch {
            DuelPushManager.initIfNeeded()
        }
    }

    private fun buildForegroundNotification(state: Int): Notification {
        val launcherIntent = PendingIntent.getActivity(
            this, -1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopMonitorIntent = PendingIntent.getService(
            this, -1,
            Intent(this, DuelMonitorService::class.java).setAction(ACTION_STOP_MONITOR),
            PendingIntent.FLAG_IMMUTABLE
        )
        val startIntent = PendingIntent.getService(
            this, -1,
            Intent(this, DuelMonitorService::class.java).setAction(ACTION_RESTART_MONITOR),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setSmallIcon(R.drawable.ic_twotone_eye_24)
            .setContentTitle(R.string.app_name.resText)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFAULT)
            .setContentIntent(launcherIntent)
            .setOnlyAlertOnce(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
        when (state) {
            in State.DISCONNECTED -> {
                val contentRes = if (state in State.DISCONNECTED_ERROR) R.string.connection_is_lost
                else R.string.service_is_idle
                val startActionRes = if (state in State.DISCONNECTED_ERROR) R.string.restart_service
                else R.string.start_service
                builder.setContentText(contentRes.resStr).addAction(
                    NotificationCompat.Action(
                        IconCompat.createWithResource(this, R.drawable.ic_twotone_replay_24),
                        startActionRes.resStr, startIntent
                    )
                ).addAction(
                    NotificationCompat.Action(
                        IconCompat.createWithResource(this, R.drawable.ic_baseline_close_24),
                        R.string.remove_notification.resText, stopMonitorIntent
                    )
                )
            }

            State.CONNECTING -> {
                builder.setContentText(R.string.starting_service.resStr)
            }

            State.CONNECTED -> {
                builder.setContentText(R.string.service_is_running.resStr).addAction(
                    NotificationCompat.Action(
                        IconCompat.createWithResource(this, R.drawable.ic_baseline_close_24),
                        R.string.stop_monitor_service.resText, stopMonitorIntent
                    )
                )
            }

            else -> error("unexpected state: $state")
        }
        return builder.build()
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //TODO update notification when language changes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITOR) {
            binder.stopMonitor()
        } else if (intent?.action == ACTION_RESTART_MONITOR) {
            binder.startMonitor()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        closeables.close()
        DuelPushManager.cancelAll()
    }

    override fun onDuelCreated(created: Duel) {
        mainScope.launch {
            val userInvolved = AccountManager.hasLogin()
                    && created.containsPlayer(AccountManager.reqUsername())
            if (userInvolved && playerInfoLoader.queryAllPlayerInfo(created)) {
                wssClient.notifyPlayersInfoLoadedFromService(created)
                created.pushSelfInvolved()
                return@launch
            }
            val requireRank = DuelPushManager.ALL_CRITERIA.any { it.isEnabled }
                    && !Configs.isNotificationDisabled
            if (requireRank && playerInfoLoader.queryAllPlayerInfo(created)) {
                wssClient.notifyPlayersInfoLoadedFromService(created)
                created.checkAllCriteriaAndPush()
            }
        }
    }

    override fun onDuelDeleted(deleted: Duel) {
        super.onDuelDeleted(deleted)
        deleted.cancelPush()
        val userInvolved =
            AccountManager.hasLogin() && deleted.containsPlayer(AccountManager.reqUsername())
        if (userInvolved) deleted.notifyResult()
    }
}