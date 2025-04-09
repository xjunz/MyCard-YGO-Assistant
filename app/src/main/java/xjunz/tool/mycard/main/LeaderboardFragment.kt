package xjunz.tool.mycard.main

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.transition.Fade
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AnimationUtils
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xjunz.tool.mycard.Apis
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.InputDialog
import xjunz.tool.mycard.databinding.FragmentLeaderboardBinding
import xjunz.tool.mycard.databinding.ItemLeaderboardBinding
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.applySystemInsets
import xjunz.tool.mycard.ktx.asStateList
import xjunz.tool.mycard.ktx.beginDelayedTransition
import xjunz.tool.mycard.ktx.dp
import xjunz.tool.mycard.ktx.dpFloat
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.formatToDate
import xjunz.tool.mycard.ktx.lazyActivityViewModel
import xjunz.tool.mycard.ktx.resColor
import xjunz.tool.mycard.ktx.resText
import xjunz.tool.mycard.ktx.resolveAttribute
import xjunz.tool.mycard.ktx.setTooltipCompat
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.LeaderboardPlayer
import java.util.Collections

class LeaderboardFragment : Fragment() {

    private val staggerAnimOffsetMills = 40L

    private lateinit var binding: FragmentLeaderboardBinding

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("players", Context.MODE_PRIVATE)
    }

    private val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        expectSuccess = false
        install(ContentNegotiation) {
            json(Configs.LenientJson)
        }
    }

    private suspend fun loadFromRemote(): Result<List<LeaderboardPlayer>> = runCatching {
        withContext(Dispatchers.IO) {
            client.get(Apis.BASE_API + Apis.API_TOP_PLAYER).body()
        }
    }

    private val leaderboardPlayers = mutableListOf<LeaderboardPlayer>()

    private val adapter by lazy {
        LeaderboardAdapter()
    }

    private var firstStage = true

    private val viewModel by lazyActivityViewModel<MainViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun updateLeaderboard(newList: List<LeaderboardPlayer>) {
        val old = ArrayList(leaderboardPlayers)
        leaderboardPlayers.clear()
        leaderboardPlayers.addAll(newList)
        if (binding.rvLeaderboard.adapter == null) {
            binding.rvLeaderboard.adapter = adapter
        } else {
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int {
                    return old.size
                }

                override fun getNewListSize(): Int {
                    return newList.size
                }

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return old[oldItemPosition].name == newList[newItemPosition].name
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int, newItemPosition: Int
                ): Boolean {
                    return old[oldItemPosition] == newList[newItemPosition]
                }
            }, true).dispatchUpdatesTo(adapter)
            toast(R.string.loaded_successfully)
        }
    }

    private fun loadLeaderboard() {
        lifecycleScope.launch {
            binding.tvTimelinessTip.text = R.string.loading_leaderboard.resText
            binding.mask.isVisible = true
            ObjectAnimator.ofFloat(binding.mask, View.ALPHA, 0F, 1F).start()
            loadFromRemote().onFailure {
                it.printStackTrace()
                binding.tvTimelinessTip.text = R.string.prompt_load_failed.resText
            }.onSuccess {
                updateLeaderboard(it)
                binding.tvTimelinessTip.text =
                    R.string.format_timeliness.format(System.currentTimeMillis().formatToDate())
            }
            binding.root.beginDelayedTransition(Fade())
            binding.mask.isVisible = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mask.background = ColorUtils.setAlphaComponent(
            requireContext().resolveAttribute(com.google.android.material.R.attr.colorSurface).resColor,
            (.8 * 0XFF).toInt()
        ).toDrawable()
        binding.topBar.applySystemInsets { v, insets ->
            v.updatePadding(top = insets.top)
        }
        viewModel.bottomBarHeight.observe(viewLifecycleOwner) {
            binding.fabSearch.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = it + 16.dp
            }
        }
        binding.btnRefresh.setOnClickListener {
            loadLeaderboard()
        }
        loadLeaderboard()
        binding.rvLeaderboard.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) viewModel.shouldShowBottomBar.value = false
                else if (dy < 0) viewModel.shouldShowBottomBar.value = true
            }
        })
        binding.topBar.addOnOffsetChangedListener { _, verticalOffset ->
            val alpha = 1F - (-verticalOffset.toFloat() / binding.cvTitle.height)
            binding.cvTitle.alpha = alpha
        }
        binding.fabSearch.setOnClickListener {
            val spKey = "search_history"
            val history = sharedPrefs.getStringSet(spKey, Collections.emptySet())!!.toList()
            InputDialog().apply {
                setTitle(R.string.search_for_player.resText)
                setHint(R.string.input_player_name.resText)
                setPositiveButton {
                    PlayerInfoDialog().setPlayerName(it)
                        .doOnInfoLoaded { name ->
                            val newList = (history + Collections.singleton(name)).toMutableList()
                            if (history.size > 10) {
                                newList.removeAt(0)
                            }
                            sharedPrefs.edit { putStringSet(spKey, newList.toSet()) }
                        }.show(parentFragmentManager, "player-info")
                    return@setPositiveButton null
                }
                setDropDownData(history)
            }.show(parentFragmentManager, "search-player")
        }
        viewModel.onPlayerInfoChanged.observe(viewLifecycleOwner) { name ->
            if (name == null) return@observe
            val index = leaderboardPlayers.indexOfFirst {
                it.name == name
            }
            if (index >= 0) {
                adapter.notifyItemChanged(index, true)
            }
        }
    }

    inner class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemLeaderboardBinding) :
            RecyclerView.ViewHolder(binding.root) {

            private val shapeDrawable by lazy {
                MaterialShapeDrawable(ShapeAppearanceModel.builder().build()).apply {
                    strokeColor = requireActivity().getColorStateList(
                        com.google.android.material.R.color.material_on_surface_stroke
                    )
                    fillColor = requireActivity().resolveAttribute(
                        com.google.android.material.R.attr.colorSurface
                    ).resColor.asStateList
                    strokeWidth = 1.dpFloat
                }
            }

            private fun createBackground(): Drawable {
                return RippleDrawable(
                    requireContext().resolveAttribute(
                        com.google.android.material.R.attr.colorSurfaceVariant
                    ).resColor.asStateList, shapeDrawable, null
                )
            }

            fun setCorner(
                leftTop: Float = 0F, rightTop: Float = 0F,
                leftBottom: Float = 0F, rightBottom: Float = 0F
            ) {
                shapeDrawable.shapeAppearanceModel =
                    shapeDrawable.shapeAppearanceModel.toBuilder().setTopLeftCornerSize(leftTop)
                        .setTopRightCornerSize(rightTop).setBottomLeftCornerSize(leftBottom)
                        .setBottomRightCornerSize(rightBottom).build()
            }

            init {
                binding.root.background = createBackground()
                binding.root.setOnClickListener {
                    PlayerInfoDialog()
                        .setPlayer(leaderboardPlayers[adapterPosition])
                        .show(parentFragmentManager, "LeaderboardPlayerInfoDialog")
                }
                binding.ibStar.setOnClickListener {
                    val name = leaderboardPlayers[adapterPosition].name
                    it.isActivated = !it.isActivated
                    PlayerInfoManager.toggleFollowingState(name)
                    setIbStarTooltip(it)
                    DuelListAdapter.broadcastAllChanged(DuelListAdapter.Payload.FOLLOWING_STATE)
                    if (it.isActivated) {
                        toast(R.string.format_follow_player.format(name))
                    } else {
                        toast(R.string.format_unfollow_player.format(name))
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemLeaderboardBinding.inflate(layoutInflater, parent, false))
        }

        override fun getItemCount(): Int {
            return leaderboardPlayers.size
        }

        private fun setIbStarTooltip(ibStar: View) {
            check(ibStar.id == R.id.ib_star)
            if (ibStar.isActivated) {
                ibStar.setTooltipCompat(R.string.unfollow.resText)
            } else {
                ibStar.setTooltipCompat(R.string.follow.resText)
            }
        }

        private fun bindTags(binding: ItemLeaderboardBinding, name: String) {
            val tags = PlayerInfoManager.getTags(name)
            if (tags.isNotEmpty()) {
                binding.tvTag.isVisible = true
                binding.tvTag.text = tags[0]
            } else {
                binding.tvTag.isVisible = false
            }
        }

        private fun bindMilestone(binding: ItemLeaderboardBinding, index: Int) {
            val next = leaderboardPlayers.getOrNull(index + 1)?.pt ?: Float.MAX_VALUE
            val current = leaderboardPlayers[index].pt
            val milestone = (current / 100).toInt() * 100
            if (next < milestone) {
                binding.tvDpMilestone.isVisible = true
                binding.dividerMilestone.isVisible = true
                binding.maskBott.isVisible = true
                binding.tvDpMilestone.text = R.string.format_dp.format(milestone)
            } else {
                binding.tvDpMilestone.isVisible = false
                binding.dividerMilestone.isVisible = false
            }
        }

        override fun onBindViewHolder(
            holder: ViewHolder, position: Int, payloads: MutableList<Any>
        ) {
            if (payloads.isNotEmpty()) {
                bindTags(holder.binding, leaderboardPlayers[position].name)
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        private val primaryTextColor by lazy {
            requireContext().getColorStateList(requireContext().resolveAttribute(android.R.attr.textColorPrimary))
        }

        private val tertiaryTextColor by lazy {
            requireContext().getColorStateList(requireContext().resolveAttribute(android.R.attr.textColorTertiary))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val player = leaderboardPlayers[position]
            player.rank = position + 1
            holder.binding.apply {
                tvPt.text = String.format("%.2f", player.pt)
                tvRank.text = String.format("%d", position + 1)
                tvName.text = player.name
                tvRank.setTextColor(
                    when (position) {
                        0 -> R.color.color_champion.resColor
                        1 -> R.color.color_second.resColor
                        2 -> R.color.color_third.resColor
                        else -> tvRank.currentTextColor
                        //   else -> requireContext().resolveAttribute(android.R.attr.textColorPrimary).resColor
                    }
                )
                when (position) {
                    in 0..2 -> {
                        tvRank.setTypeface(null, Typeface.BOLD)
                    }

                    in 3..9 -> {
                        tvRank.setTypeface(null, Typeface.BOLD)
                        tvRank.setTextColor(primaryTextColor)
                    }

                    else -> {
                        tvRank.setTypeface(null, Typeface.BOLD)
                        tvRank.setTextColor(tertiaryTextColor)
                    }
                }
                ibStar.isActivated = PlayerInfoManager.isFollowed(player.name)
                setIbStarTooltip(ibStar)
            }
            holder.binding.maskTop.isVisible = position != 0
            holder.binding.maskBott.isVisible = position != itemCount - 1
            when (position) {
                0 -> holder.setCorner(leftTop = 16.dpFloat, rightTop = 16.dpFloat)

                itemCount - 1 -> holder.setCorner(leftBottom = 16.dpFloat, rightBottom = 16.dpFloat)

                else -> holder.setCorner()
            }
            bindTags(holder.binding, player.name)
            bindMilestone(holder.binding, position)
            if (firstStage) {
                val easeIn =
                    AnimationUtils.loadAnimation(requireContext(), R.anim.mtrl_item_ease_enter)
                easeIn.startOffset = (staggerAnimOffsetMills + position) * position
                holder.itemView.startAnimation(easeIn)
                if (position == 0) {
                    holder.itemView.postDelayed({ firstStage = false }, staggerAnimOffsetMills)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (client.isActive) {
            client.cancel()
        }
    }
}