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
import androidx.core.content.ContextCompat
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
import xjunz.tool.mycard.ktx.broadcast
import xjunz.tool.mycard.ktx.dp
import xjunz.tool.mycard.ktx.get
import xjunz.tool.mycard.ktx.launchActivity
import xjunz.tool.mycard.ktx.longToast
import xjunz.tool.mycard.ktx.requireActivity
import xjunz.tool.mycard.ktx.resColor
import xjunz.tool.mycard.ktx.resText
import xjunz.tool.mycard.ktx.resolveAttribute
import xjunz.tool.mycard.ktx.setTooltipCompat
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.detail.DuelDetailsActivity
import xjunz.tool.mycard.main.filter.DuelListFilterCriteria
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.monitor.DuelMonitorEventObserver
import xjunz.tool.mycard.monitor.State
import xjunz.tool.mycard.monitor.push.DuelPushManager.checkAllCriteria
import xjunz.tool.mycard.monitor.push.DuelPushManager.checkCriteriaConsideringDelay
import xjunz.tool.mycard.ui.SpreadingRippleDrawable
import xjunz.tool.mycard.util.ViewIdleStateDetector
import xjunz.tool.mycard.util.errorLog
import java.util.Collections

/**
 * Note: this adapter must be attached to the [RecyclerView] after the service
 * is bound.
 */
class DuelListAdapter(private val mvm: MainViewModel) :
    RecyclerView.Adapter<DuelListAdapter.DuelViewHolder>(),
    DuelMonitorEventObserver {

    object Action {
        const val REFRESH_DUEL = 1
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
                putExtra(Extra.ACTION, Action.REFRESH_DUEL)
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
        //viewModel.hasDataShown.value = duelList.isNotEmpty()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
            }
        })
        ContextCompat.registerReceiver(
            context,
            refreshReceiver,
            IntentFilter(ACTION_REFRESH),
            ContextCompat.RECEIVER_NOT_EXPORTED
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

    private fun bindPlayersInfoFromRemote(duel: Duel, fromScroll: Boolean) {
        lifecycleScope.launch {
            if (!viewModel.playerInfoLoader.queryAllPlayerInfo(duel)) return@launch
            val index = duelList.indexOf(duel)
            if (fromScroll) {
                notifyItemChanged(index, Payload.RANK)
            } else {
                notifyPlayersInfoLoaded(index, duel)
            }
        }
    }

    private fun notifyPlayersInfoLoaded(index: Int, duel: Duel) {
        if (index >= 0) {
            duelList.removeAt(index)
            val insertion = -(Collections.binarySearch(duelList, duel, comparator) + 1)
            if (insertion >= 0) {
                duelList.add(insertion, duel)
                notifyItemMoved(index, insertion)
                notifyItemChanged(insertion, Payload.RANK)
                viewModel.hasDataShown.value = true
            }
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
        val isChecked = duel.checkCriteriaConsideringDelay()
        binding.rippleView.isVisible = isChecked
        binding.ibWatch.isActivated = isChecked
    }

    override fun onBindViewHolder(
        holder: DuelViewHolder,
        position: Int,
        payloads: MutableList<Any>
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
            bindPlayersInfoFromRemote(duel, false)
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
                return o === n && o.isFollowed() == n.isFollowed() && o.checkAllCriteria() == n.checkAllCriteria()
            }
        }, detectMoves).dispatchUpdatesTo(this)

    /******************************* Monitor Event Observer **************************************/

    override fun onInitialized(list: List<Duel>) {
        val old = ArrayList(duelList)
        duelList.clear()
        duelList.addAll(list)
        var isEmptyAfterFiltered = duelList.isNotEmpty()
        filterDuelList(duelList)
        isEmptyAfterFiltered = isEmptyAfterFiltered && duelList.isEmpty()
        // sort the list to pin duels with followed players if needed
        duelList.sortWith(comparator)
        if (old.isEmpty()) {
            notifyItemRangeInserted(0, duelList.size)
        } else {
            diffList(old)
        }
        viewModel.hasDataShown.value = duelList.isNotEmpty()
        if (isEmptyAfterFiltered) {
            toast(R.string.tip_no_duel_matches_filter)
        }
    }

    private val comparator = Comparator<Duel> { o1, o2 ->
        var c = 0
        if (Configs.shouldPinFollowedDuels) {
            c = -o1.isFollowed().compareTo(o2.isFollowed())
        }
        if (c == 0) {
            c = -o1.areAllPlayersLoaded.compareTo(o2.areAllPlayersLoaded)
            if (c == 0) {
                if (o1.areAllPlayersLoaded && o2.areAllPlayersLoaded) {
                    val filter = Configs.duelListFilter
                    c = when (filter.sortBy) {
                        DuelListFilterCriteria.SORT_BY_RANK_BEST ->
                            o1.comparableBestRank.compareTo(o2.comparableBestRank)

                        DuelListFilterCriteria.SORT_BY_RANK_SUM ->
                            o1.comparableSumRank.compareTo(o2.comparableSumRank)

                        DuelListFilterCriteria.SORT_BY_RANK_DIFF ->
                            o1.comparableDiffRank.compareTo(o2.comparableDiffRank)

                        DuelListFilterCriteria.SORT_BY_WIN_RATE_BEST ->
                            o1.winRateBest.compareTo(o2.winRateBest)

                        DuelListFilterCriteria.SORT_BY_WIN_RATE_SUM ->
                            o1.winRateSum.compareTo(o2.winRateSum)

                        DuelListFilterCriteria.SORT_BY_WIN_RATE_DIFF ->
                            o1.winRateDiff.compareTo(o2.winRateDiff)

                        else -> o1.ordinal.compareTo(o2.ordinal)
                    }
                    if (!filter.isAscending) c = -c
                } else {
                    c = o1.ordinal.compareTo(o2.ordinal)
                }
            }
        }
        return@Comparator c
    }

    override fun onDuelDeleted(deleted: Duel) {
        ViewIdleStateDetector.cancel(deleted.hashCode())
        val index = duelList.indexOf(deleted)
        if (index != -1) {
            duelList.removeAt(index)
            notifyItemRemoved(index)
        }
        viewModel.hasDataShown.value = duelList.isNotEmpty()
    }

    override fun onDuelCreated(created: Duel) {
        if (Configs.duelListFilter.duelCriteria?.checkConsideringDelay(created) == false) {
            return
        }
        if (Configs.shouldPinFollowedDuels && created.isFollowed()) {
            val insertion = -(Collections.binarySearch(duelList, created, comparator) + 1)
            check(insertion >= 0)
            duelList.add(insertion, created)
            notifyItemInserted(insertion)
        } else {
            duelList.add(created)
            notifyItemInserted(duelList.size - 1)
        }
        viewModel.hasDataShown.value = true
    }

    override fun onPlayersInfoLoadedFromService(duel: Duel) {
        val index = duelList.indexOf(duel)
        notifyPlayersInfoLoaded(index, duel)
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

                        override fun onIdle() = bindPlayersInfoFromRemote(duel, true)

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
            val insertion = -(Collections.binarySearch(duelList, duel, comparator) + 1)
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
                Action.REFRESH_DUEL -> {
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
                        duelList.sortWith(comparator)
                    } else {
                        duelList.sort()
                    }
                    notifyItemRangeChanged(0, duelList.size, Payload.FOLLOWING_STATE)
                }
            }
        }
    }

    private fun filterDuelList(list: MutableList<Duel>) {
        Configs.duelListFilter.duelCriteria?.let {
            list.removeAll { duel ->
                !duel.containsKeyword(Configs.duelListFilter.keyword)
                        || !it.checkConsideringDelay(duel)
            }
        }
    }

    fun notifyFilterChanged() {
        viewModel.binder?.let {
            val old = ArrayList(duelList)
            duelList.clear()
            duelList.addAll(it.duelList)
            var isEmptyAfterFiltered = duelList.isNotEmpty()
            filterDuelList(duelList)
            isEmptyAfterFiltered = isEmptyAfterFiltered && duelList.isEmpty()
            duelList.sortWith(comparator)
            diffList(old)
            viewModel.hasDataShown.value = duelList.isNotEmpty()
            if (isEmptyAfterFiltered) {
                toast(R.string.tip_no_duel_matches_filter)
            }
        }
    }
}