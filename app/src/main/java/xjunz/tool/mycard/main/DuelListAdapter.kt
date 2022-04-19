package xjunz.tool.mycard.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.databinding.ItemDuelBinding
import xjunz.tool.mycard.game.GameLauncher
import xjunz.tool.mycard.game.GameLauncher.spectateCheckLogin
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.info.PlayerInfoManager.isFollowed
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.detail.DuelDetailsActivity
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.monitor.DuelMonitorEventObserver
import xjunz.tool.mycard.monitor.State
import xjunz.tool.mycard.monitor.push.DuelPushManager.checkCriteria
import xjunz.tool.mycard.ui.SpreadingRippleDrawable
import xjunz.tool.mycard.util.ViewIdleStateDetector
import xjunz.tool.mycard.util.errorLog
import java.util.*

/**
 * Note: this adapter must be attached to the [RecyclerView] after the service
 * is bound.
 */
class DuelListAdapter : RecyclerView.Adapter<DuelListAdapter.DuelViewHolder>(),
    DuelMonitorEventObserver {

    object Action {
        const val REFRESH_ITEM = 1
        const val REFRESH_ALL = 2
        const val REFRESH_ORDER = 3
    }

    object Extra {
        const val ACTION = "xjunz.extra.ACTION"
        const val PAYLOAD = "xjunz.extra.PAYLOAD"
        const val DUEL_ID = "xjunz.extra.DUEL_ID"
    }

    object Payload {
        const val CHECKED_STATE = 0x1
        const val RANK = 0x2
        const val TAGS = 0x3
        const val FOLLOWING_STATE = 0x4
        const val ALL = -1
    }

    companion object {

        private const val ACTION_REFRESH = "xjunz.action.REFRESH"

        fun broadcastItemChanged(duel: Duel, payload: Int) {
            app.broadcast(ACTION_REFRESH) {
                putExtra(Extra.ACTION, Action.REFRESH_ITEM)
                putExtra(Extra.DUEL_ID, duel.id)
                putExtra(Extra.PAYLOAD, payload)
            }
        }

        fun broadcastOrderChanged() {
            app.broadcast(ACTION_REFRESH) {
                putExtra(Extra.ACTION, Action.REFRESH_ORDER)
            }
        }

        fun broadcastAllChanged(payload: Int) {
            app.broadcast(ACTION_REFRESH) {
                putExtra(Extra.ACTION, Action.REFRESH_ALL)
                putExtra(Extra.PAYLOAD, payload)
            }
        }
    }

    private val duelList = mutableListOf<Duel>()

    private lateinit var host: RecyclerView

    private val lifecycleOwner by lazy {
        host.findViewTreeLifecycleOwner()!!
    }

    private val lifecycleScope by lazy {
        lifecycleOwner.lifecycleScope
    }

    private val context by lazy { host.context.requireActivity() }

    private val viewModel by lazy {
        context[MainViewModel::class.java]
    }

    private val layoutInflater by lazy { LayoutInflater.from(context) }

    private var isScrolling = false

    private val isDisconnected get() = viewModel.monitorState.value in State.DISCONNECTED

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        host = recyclerView
        viewModel.monitorService.observeEventSticky(this)
        viewModel.hasDataShown.value = duelList.isNotEmpty()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
            }
        })
        context.registerReceiver(
            refreshReceiver, IntentFilter(ACTION_REFRESH)
        )
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) cleanUp()
        })
    }

    private fun cleanUp() {
        viewModel.monitorService.removeEventObserve(this)
        viewModel.monitorState.removeObservers(lifecycleOwner)
        context.unregisterReceiver(refreshReceiver)
        ViewIdleStateDetector.clearAll()
    }

    inner class DuelViewHolder(val binding: ItemDuelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val runningRipple by lazy {
            SpreadingRippleDrawable().apply {
                rippleCenterRadius = 12.dp
                alpha = (.78 * 0xFF).toInt()
                rippleColor = context.resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary
                ).resColor
            }
        }

        init {
            binding.rippleView.background = runningRipple
            runningRipple.start()
            binding.ibWatch.setOnClickListener {
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                if (!GameLauncher.exists()) {
                    longToast(R.string.no_game_launcher_found)
                    return@setOnClickListener
                }
                duelList[adapterPosition].spectateCheckLogin(context)
            }
            binding.ibWatch.setTooltipCompat(R.string.spectate.resText)
            binding.root.setOnClickListener {
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                context.launchActivity(DuelDetailsActivity::class.java) {
                    putExtra(DuelDetailsActivity.EXTRA_DUEL, duelList[adapterPosition])
                }
            }
            binding.pinContainer.translationY = (-binding.root.paddingTop).toFloat()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        DuelViewHolder(ItemDuelBinding.inflate(layoutInflater, parent, false))

    /******************************* Bind View Holder **************************************/

    private fun bindPlayerRanksFromRemote(duel: Duel) {
        lifecycleScope.launch {
            if (!viewModel.playerInfoLoader.queryAllPlayerInfo(duel)) return@launch
            val index = duelList.indexOf(duel)
            if (index != -1) notifyItemChanged(index, Payload.RANK)
        }
    }

    private fun bindPlayerRanks(binding: ItemDuelBinding, duel: Duel) {
        for (i in Duel.PLAYERS) {
            val tv = if (i == Duel.PLAYER_1) binding.tvRank1 else binding.tvRank2
            tv.isActivated = isFollowed(duel.requirePlayerName(i))
            val player = duel.getPlayer(i)
            if (player != null) when {
                player.isNewcomer -> tv.text = R.string.newcomer.resText
                player.rank == 1 -> tv.text = "🥇"
                player.rank == 2 -> tv.text = "🥈"
                player.rank == 3 -> tv.text = "🥉"
                else -> tv.text = player.rank.toString()
            } else {
                tv.text = R.string.loading.resText
            }
        }
    }

    private fun bindTags(binding: ItemDuelBinding, duel: Duel) {
        Duel.PLAYERS.forEach {
            val container =
                if (it == Duel.PLAYER_1) binding.tagContainer1 else binding.tagContainer2
            val tags = PlayerInfoManager.getTags(duel.requirePlayerName(it))
            container.isVisible = tags.isNotEmpty()
            // continue
            if (tags.isEmpty()) return@forEach
            // simply reuse the existing views
            tags.forEachIndexed { index, tag ->
                val child = container.getChildAt(index) as? TextView ?: container.addText(index)
                child.isVisible = true
                child.text = tag
            }
            if (tags.size < container.childCount) {
                for (i in tags.size until container.childCount) {
                    container.getChildAt(i).isVisible = false
                }
            }
        }
    }

    private fun bindPlayerNames(binding: ItemDuelBinding, duel: Duel) {
        binding.pinContainer.isVisible = duel.isFollowed()
        Duel.PLAYERS.forEach {
            val tv = if (it == Duel.PLAYER_1) binding.tvPlayer1 else binding.tvPlayer2
            tv.text = duel.requirePlayerName(it)
            tv.isActivated = isFollowed(duel.requirePlayerName(it))
        }
    }

    private fun bindCheckedStates(binding: ItemDuelBinding, duel: Duel) {
        val isChecked = duel.checkCriteria()
        binding.rippleView.isVisible = isChecked
        binding.ibWatch.isActivated = isChecked
    }

    override fun onBindViewHolder(
        holder: DuelViewHolder, position: Int, payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val binding = holder.binding
            val duel = duelList[position]
            payloads.forEach {
                when (it) {
                    Payload.RANK -> bindPlayerRanks(binding, duel)
                    Payload.FOLLOWING_STATE -> {
                        bindPlayerNames(binding, duel)
                        bindPlayerRanks(binding, duel)
                    }
                    Payload.TAGS -> bindTags(binding, duel)
                }
            }
            // recheck on every payload change
            bindCheckedStates(binding, duel)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private var firstStage = true
    private val staggerAnimOffsetMills = 30L

    override fun onBindViewHolder(holder: DuelViewHolder, position: Int) {
        val binding = holder.binding
        val duel = duelList[position]
        bindPlayerNames(binding, duel)
        bindTags(binding, duel)
        bindCheckedStates(binding, duel)
        bindPlayerRanks(binding, duel)
        if (!isScrolling && !isDisconnected && !duel.areAllPlayersLoaded) {
            bindPlayerRanksFromRemote(duel)
        }
        if (firstStage) {
            val easeIn = AnimationUtils.loadAnimation(context, R.anim.mtrl_item_ease_enter)
            easeIn.startOffset = (staggerAnimOffsetMills + position) * position
            holder.itemView.startAnimation(easeIn)
            if (position == 0) {
                holder.itemView.postDelayed({ firstStage = false }, staggerAnimOffsetMills)
            }
        }
    }

    override fun getItemCount() = duelList.size

    private fun diffList(old: List<Duel>, detectMoves: Boolean = false) =
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size

            override fun getNewListSize() = duelList.size

            override fun areItemsTheSame(op: Int, np: Int) = old[op] === duelList[np]

            override fun areContentsTheSame(op: Int, np: Int): Boolean {
                val o = old[op]
                val n = duelList[np]
                return o === n && o.isFollowed() == n.isFollowed() && o.checkCriteria() == n.checkCriteria()
            }
        }, detectMoves).dispatchUpdatesTo(this)

    /******************************* Monitor Event Observer **************************************/

    override fun onInitialized(list: List<Duel>) {
        val old = ArrayList(duelList)
        duelList.clear()
        duelList.addAll(list)
        // sort the list to pin duels with followed players if needed
        if (Configs.shouldPinFollowedDuels) duelList.sortWith(pinComparator)
        if (old.isEmpty()) {
            notifyItemRangeInserted(0, duelList.size)
        } else {
            diffList(old)
        }
        viewModel.hasDataShown.value = duelList.isNotEmpty()
    }

    private val pinComparator = Comparator<Duel> { o1, o2 ->
        val ret = -o1.isFollowed().compareTo(o2.isFollowed())
        if (ret == 0) o1.compareTo(o2) else ret
    }

    override fun onDuelDeleted(deleted: Duel) {
        ViewIdleStateDetector.cancel(deleted.hashCode())
        val index = duelList.indexOf(deleted)
        if (index != -1) {
            duelList.removeAt(index)
            notifyItemRemoved(index)
            viewModel.hasDataShown.value = duelList.isNotEmpty()
        }
    }

    override fun onDuelCreated(created: Duel) {
        if (Configs.shouldPinFollowedDuels && created.isFollowed()) {
            val insertion = -(Collections.binarySearch(duelList, created, pinComparator) + 1)
            check(insertion >= 0)
            duelList.add(insertion, created)
            notifyItemInserted(insertion)
        } else {
            duelList.add(created)
            notifyItemInserted(duelList.size - 1)
        }
        viewModel.hasDataShown.value = true
    }

    override fun onAllDuelCleared() {
        val prevCount = duelList.size
        duelList.clear()
        notifyItemRangeRemoved(0, prevCount)
        viewModel.hasDataShown.value = false
    }

    override fun onViewAttachedToWindow(holder: DuelViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (isDisconnected) return
        val pos = holder.adapterPosition
        if (pos !in duelList.indices) return
        val duel = duelList[pos]
        if (duel.player1 != null && duel.player2 != null || !isScrolling) return
        val itemView = holder.itemView
        itemView.post {
            // When we scroll the list, the view might be detached and attached multiple times.
            // Hence, on every attach event we check whether the view, in the next frame, is
            // eventually attached. If true, we would launch a lazy loader.
            if (itemView.isAttachedToWindow) {
                ViewIdleStateDetector.detect(
                    duel.hashCode(), object : ViewIdleStateDetector.Callback {

                        override val target: View = itemView

                        override fun onIdle() = bindPlayerRanksFromRemote(duel)

                        override fun shouldStop() = !target.isAttachedToWindow
                    })
            }
        }
    }

    private fun notifyFollowingStateChanged(position: Int) {
        notifyItemChanged(position, Payload.FOLLOWING_STATE)
        val duel = duelList[position]
        // try to pin or unpin the duel
        if (Configs.shouldPinFollowedDuels) {
            val toPin = duel.isFollowed()
            duelList.removeAt(position)
            val insertion = -(Collections.binarySearch(duelList, duel, pinComparator) + 1)
            // check contract
            check(if (toPin) insertion <= position else insertion >= position)
            duelList.add(insertion, duel)
            if (insertion == position) return
            notifyItemMoved(position, insertion)
            if (!toPin) return
            host.scrollToPosition(insertion)
            host.post {
                host.findViewHolderForAdapterPosition(insertion)?.itemView?.let {
                    it.isPressed = true
                    it.isPressed = false
                }
            }
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(Extra.ACTION, -1)) {
                Action.REFRESH_ITEM -> {
                    val id = intent.getStringExtra(Extra.DUEL_ID)
                    checkNotNull(id)
                    val index = duelList.indexOfFirst { it.id == id }
                    if (index != -1) {
                        when (val payload = intent.getIntExtra(Extra.PAYLOAD, Payload.ALL)) {
                            Payload.ALL -> notifyItemChanged(index)
                            Payload.FOLLOWING_STATE -> notifyFollowingStateChanged(index)
                            else -> notifyItemChanged(index, payload)
                        }
                    } else {
                        errorLog("Cannot find duel: $id in duel list. Is it removed just now?")
                    }
                }
                Action.REFRESH_ALL -> notifyItemRangeChanged(
                    0, duelList.size,
                    intent.getIntExtra(Extra.PAYLOAD, Payload.ALL)
                )
                Action.REFRESH_ORDER -> {
                    if (Configs.shouldPinFollowedDuels) {
                        duelList.sortWith(pinComparator)
                    } else {
                        duelList.sort()
                    }
                    notifyItemRangeChanged(0, duelList.size, Payload.FOLLOWING_STATE)
                }
            }
        }
    }
}