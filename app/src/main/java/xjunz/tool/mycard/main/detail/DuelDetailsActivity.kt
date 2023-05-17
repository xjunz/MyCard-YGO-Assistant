package xjunz.tool.mycard.main.detail

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.transition.ChangeBounds
import android.transition.Slide
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.launch
import xjunz.tool.mycard.R
import xjunz.tool.mycard.databinding.ActivityDuelDetailsBinding
import xjunz.tool.mycard.game.GameLauncher.spectateCheckLogin
import xjunz.tool.mycard.info.PlayerInfoLoaderClient
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.DuelListAdapter
import xjunz.tool.mycard.main.account.AccountManager
import xjunz.tool.mycard.main.history.HistoryDialog
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.monitor.DuelMonitorEventObserver
import xjunz.tool.mycard.monitor.DuelMonitorService
import xjunz.tool.mycard.monitor.State
import xjunz.tool.mycard.monitor.push.DuelPushManager.cancelPush
import xjunz.tool.mycard.monitor.push.DuelPushManager.cancelSelfPush
import xjunz.tool.mycard.util.Motions

/**
 * @author xjunz 2022/3/7
 */
class DuelDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_NOTIFICATION = "xjunz.extra.FROM_NOTIFICATION"
        const val EXTRA_SHOW_RESULT = "xjunz.extra.SHOW_RESULT"
        const val EXTRA_DIRECTLY_SPECTATE = "xjunz.extra.DIRECT_SPECTATE"
        const val EXTRA_DUEL = "xjunz.extra.DUEL"
    }

    private val viewModel by lazyViewModel<DetailsViewModel>()

    private val Intent.isFromNotification
        get() = getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)

    private val Intent.shouldDirectlySpectate
        get() = getBooleanExtra(EXTRA_DIRECTLY_SPECTATE, false)

    private val Intent.shouldShowResult
        get() = getBooleanExtra(EXTRA_SHOW_RESULT, false)


    private val binding by lazy {
        ActivityDuelDetailsBinding.inflate(layoutInflater)
    }

    private lateinit var playerInfoLoader: PlayerInfoLoaderClient

    private lateinit var duel: Duel

    private var isConnected = false

    private val behavior by lazy {
        (binding.infoContainer.layoutParams as CoordinatorLayout.LayoutParams).behavior
                as BottomSheetBehavior
    }

    @SuppressLint("RestrictedApi", "VisibleForTests")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        duel = intent.requireSerializableExtra(EXTRA_DUEL)
        if (intent.shouldDirectlySpectate) {
            check(intent.isFromNotification)
            duel.spectateCheckLogin(this)
            if (AccountManager.hasLogin()) {
                finish()
                return
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.enterTransition = Slide().apply {
            addTarget(binding.infoContainer)
            interpolator = Motions.EASING_EMPHASIZED
        }
        setContentView(binding.root)
        tryBindMonitorService()
        initViews()
        bindViews()
        behavior.skipCollapsed = true
        binding.infoContainer.doOnPreDraw {
            (it.background as? MaterialShapeDrawable)?.interpolation = 1F
        }
    }

    private fun bindViews() {
        bindHistory()
        bindPlayerNames()
        bindStarsAndTags()
        bindPlayerInfo(Duel.PLAYER_1)
        bindPlayerInfo(Duel.PLAYER_2)
        updateStates()
        mainHandler.removeCallbacks(updateDurationTask)
        mainHandler.post(updateDurationTask)
    }

    private fun initViews() {
        binding.infoContainer.doOnPreDraw {
            behavior.setPeekHeight(it.height)
        }
        binding.btnSpectate.setOnClickListener {
            duel.spectateCheckLogin(this)
        }
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) finish()
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        binding.root.setOnClickListener {
            finishAfterTransition()
        }
        binding.infoContainer.applySystemInsets { v, insets ->
            v.updatePadding(bottom = insets.bottom)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newDuel = intent.requireSerializableExtra<Duel>(EXTRA_DUEL)
        // not the same duel, reset fields
        if (duel != newDuel) {
            mainHandler.removeCallbacksAndMessages(null)
            viewModel.hasPendingRefresh = false
            viewModel.rankDeltas.fill(0)
            viewModel.dpDeltas.fill(0)
            viewModel.prevDuel = null
        }
        duel = newDuel
        // go to spectate directly
        if (intent.shouldDirectlySpectate) duel.spectateCheckLogin(this)
        // update views
        bindViews()
        // if the duel result is not yet loaded
        if (intent.shouldShowResult && !viewModel.hasPendingRefresh) {
            notifyThisDuelEnded()
            // remove the notification
            duel.cancelPush()
        }
    }

    private fun notifyThisDuelEnded() {
        viewModel.prevDuel = duel.copy()
        lifecycleScope.launch {
            // make player info up to date
            if (playerInfoLoader.queryAllPlayerInfo(duel, true)) {
                if (isForeground) {
                    // if is in foreground, notify views to refresh now
                    animateDuelResultIfNeeded()
                } else {
                    // set the flag for pending refresh
                    viewModel.hasPendingRefresh = true
                }
            } else {
                //TODO show retry button
            }
        }
    }

    private fun bindPlayerNames() {
        Duel.PLAYERS.forEach {
            val tvName = getPlayerInfoBinding(it).tvPlayerName
            val name = duel.requirePlayerName(it)
            tvName.text = name

            tvName.isActivated = PlayerInfoManager.isFollowed(name)
        }
    }

    private fun bindHistory() {
        Duel.PLAYERS.forEach { which ->
            val ibHistory = getPlayerInfoBinding(which).ibHistory
            TooltipCompat.setTooltipText(ibHistory, R.string.duel_history.resText)
            ibHistory.setOnClickListener {
                HistoryDialog().setPlayerName(duel.requirePlayerName(which))
                    .show(supportFragmentManager, "history")
            }
        }
    }

    private fun bindStarsAndTags() {
        Duel.PLAYERS.forEach { which ->
            val ibStar = getPlayerInfoBinding(which).ibStar
            val name = duel.requirePlayerName(which)
            ibStar.isActivated = PlayerInfoManager.isFollowed(name)
            if (ibStar.isActivated) ibStar.setTooltipCompat(R.string.unfollow.resText)
            else ibStar.setTooltipCompat(R.string.follow.resText)
            ibStar.setOnClickListener {
                val nextActivated = !it.isActivated
                it.isActivated = nextActivated
                PlayerInfoManager.toggleFollowingState(name)
                if (nextActivated) toast(R.string.format_follow_player.format(name))
                else toast(R.string.format_unfollow_player.format(name))
                if (ibStar.isActivated) ibStar.setTooltipCompat(R.string.unfollow.resText)
                else ibStar.setTooltipCompat(R.string.follow.resText)
                bindPlayerNames()
                DuelListAdapter.broadcastItemChanged(duel, DuelListAdapter.Payload.FOLLOWING_STATE)
            }
            getPlayerInfoBinding(which).rvTags.adapter = TagAdapter(name).doOnTagChanged { _, _ ->
                DuelListAdapter.broadcastItemChanged(duel, DuelListAdapter.Payload.TAGS)
            }
        }
    }

    private val serviceConnection by lazy {
        object : ServiceConnection, DuelMonitorEventObserver {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service != null && service.pingBinder() && service is DuelMonitorService.DuelMonitorBinder) {
                    playerInfoLoader = service.playerInfoLoader
                    service.addEventObserverIfAbsent(this)
                    service.state.observe(this@DuelDetailsActivity) {
                        when (it) {
                            in State.DISCONNECTED -> isConnected = false
                            State.CONNECTED -> isConnected = true
                        }
                        updateStates()
                    }
                    lifecycle.addObserver(LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            service.removeEventObserve(this)
                            unbindService(this)
                        }
                    })
                    Duel.PLAYERS.forEach {
                        loadIfNeededAndBindPlayerInfo(it)
                    }
                    if (intent.shouldShowResult) {
                        notifyThisDuelEnded()
                        duel.cancelSelfPush()
                    }
                    return
                }
                throw IllegalArgumentException("Got a null or dead binder. What's going on?")
            }

            override fun onDuelDeleted(deleted: Duel) {
                if (deleted != duel) return
                toast(R.string.duel_is_ended)
                duel = deleted
                updateStates()
                notifyThisDuelEnded()
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Post this with [mainHandler] to update the duration (if needed) every one sec.
     */
    private val updateDurationTask = object : Runnable {
        override fun run() {
            if (!duel.isEnded && isConnected && !duel.isStartTimeUnknown) {
                updatePrompt()
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    private fun updatePrompt() {
        val ended = duel.isEnded
        val start = if (duel.isStartTimeUnknown) null else duel.startTimestamp.formatToDate()
        val end = if (ended) duel.endTimestamp.formatToDate() else null
        binding.tvStatus.text = when {
            ended -> R.string.duel_is_ended.resStr
            isConnected -> R.string.in_progress.resStr
            else -> R.string.connection_is_lost.resStr
        }
        binding.tvPrompt.text = when {
            ended -> if (duel.isStartTimeUnknown) R.string.format_duel_ended_unknown.format(end)
            else R.string.format_duel_ended.format(
                start, end, duel.duration.formatDurationMinSec()
            )
            isConnected -> if (duel.isStartTimeUnknown) null
            else R.string.format_duel_in_progress.format(
                start, (System.currentTimeMillis() - duel.startTimestamp).formatDurationMinSec()
            )
            else -> if (duel.isStartTimeUnknown) R.string.duel_state_unknown.resText
            else R.string.format_duel_start.format(start)
        }
        val promptVisible = binding.tvPrompt.text.isNotEmpty()
        if (promptVisible != binding.tvPrompt.isVisible) {
            findViewById<ViewGroup>(android.R.id.content).beginDelayedTransition(
                ChangeBounds().addTarget(binding.root)
            )
            binding.tvPrompt.isVisible = promptVisible
        }
    }

    private fun updateStates() {
        val dueling = !duel.isEnded
        binding.tvPrompt.isActivated = dueling && isConnected
        binding.btnSpectate.isEnabled = dueling
        if (!dueling) {
            binding.containerBtnSpectate.clearSpreading()
        } else {
            binding.containerBtnSpectate.startSpreading()
        }
        updatePrompt()
    }

    private fun tryBindMonitorService() {
        val found = bindService(
            Intent(this, DuelMonitorService::class.java), serviceConnection, 0
        )
        if (!found) {
            updateStates()
            Duel.PLAYERS.forEach {
                bindPlayerInfo(it)
            }
        }
    }

    private fun getPlayerInfoBinding(which: Int) = when (which) {
        Duel.PLAYER_1 -> binding.playerInfo1
        Duel.PLAYER_2 -> binding.playerInfo2
        else -> throw IllegalArgumentException()
    }

    private fun loadIfNeededAndBindPlayerInfo(which: Int) {
        if (duel.getPlayer(which) == null) {
            if (!isConnected) return
            lifecycleScope.launch {
                if (!playerInfoLoader.queryPlayerInfo(duel, which)) return@launch
                TransitionManager.beginDelayedTransition(
                    getPlayerInfoBinding(which).root, MaterialSharedAxis(MaterialSharedAxis.X, true)
                )
                bindPlayerInfo(which)
                DuelListAdapter.broadcastItemChanged(duel, DuelListAdapter.Payload.RANK)
            }
        } else {
            bindPlayerInfo(which)
        }
    }

    private fun bindPlayerInfo(which: Int) {
        getPlayerInfoBinding(which).apply {
            val player = duel.getPlayer(which)
            if (player == null) {
                val loading = R.string.loading.resStr
                tvRank.text = loading
                tvDp.text = loading
                tvStats.text = loading
                tvWinRate.text = loading
                tvDpDelta.isVisible = false
                tvRankDelta.isVisible = false
            } else {
                tvRank.text = player.rank.toString()
                tvDp.text = player.pt.toString()
                tvStats.text = R.string.format_stats.format(
                    player.athletic_all, player.athletic_win,
                    player.athletic_lose, player.athletic_draw
                )
                tvWinRate.text = R.string.format_percent.format(player.athletic_wl_ratio)
                val dpDelta = viewModel.dpDeltas[which]
                tvDpDelta.isVisible = dpDelta != 0
                if (dpDelta != Int.MAX_VALUE) {
                    tvDpDelta.isActivated = dpDelta > 0
                    tvDpDelta.text = dpDelta.toString()
                    tvDpDelta.text = if (dpDelta > 0) "+${dpDelta}" else dpDelta.toString()
                }
                val rankDelta = viewModel.rankDeltas[which]
                tvRankDelta.isVisible = rankDelta != 0
                if (rankDelta != Int.MAX_VALUE) {
                    tvRankDelta.isActivated = rankDelta < 0
                    tvRankDelta.text = rankDelta.toString()
                    tvRankDelta.text = if (rankDelta < 0) "+${-rankDelta}" else "-$rankDelta"
                }
            }
        }
    }

    private fun animateDuelResultIfNeeded() {
        Duel.PLAYERS.forEach { which ->
            val binding = getPlayerInfoBinding(which)
            val tvRank = binding.tvRank
            // if prev duel is not loaded, no animation
            if (viewModel.reqPrevDuel.player1 == null || viewModel.reqPrevDuel.player2 == null) {
                bindPlayerInfo(which)
                return@forEach // continue
            }
            val prevRank = viewModel.reqPrevDuel.requirePlayer(which).rank
            val rankDelta = duel.requirePlayer(which).rank - prevRank
            // start rank change animation
            ValueAnimator.ofFloat(0F, 1F).apply {
                addUpdateListener {
                    val cur = prevRank + rankDelta * animatedFraction
                    tvRank.text = cur.toInt().toString()
                }
                duration = 1240
                doOnEnd {
                    binding.root.beginDelayedTransition(MaterialFadeThrough())
                    viewModel.dpDeltas[which] = duel.requirePlayer(which).pt -
                            viewModel.reqPrevDuel.requirePlayer(which).pt
                    viewModel.rankDeltas[which] = rankDelta
                    // bind info when the animator is ended
                    bindPlayerInfo(which)
                }
                interpolator = Motions.EASING_EMPHASIZED
            }.start()
        }
    }

    private var isForeground = false

    override fun onPause() {
        super.onPause()
        isForeground = false
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        if (viewModel.hasPendingRefresh) {
            animateDuelResultIfNeeded()
            viewModel.hasPendingRefresh = false
        }
    }

    /* override fun onBackPressed() {
         super.onBackPressed()
         behavior.setPeekHeight(0, true)
     }*/

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}