package xjunz.tool.mycard.main

import android.app.Dialog
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withCreated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogLeaderboardPlayerInfoBinding
import xjunz.tool.mycard.info.PlayerInfoLoaderClient
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.beginDelayedTransition
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.lazyActivityViewModel
import xjunz.tool.mycard.ktx.lazyViewModel
import xjunz.tool.mycard.ktx.resText
import xjunz.tool.mycard.ktx.setTooltipCompat
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.detail.TagAdapter
import xjunz.tool.mycard.main.history.HistoryDialog
import xjunz.tool.mycard.model.BasePlayer
import java.text.NumberFormat

class PlayerInfoDialog : BaseBottomSheetDialog<DialogLeaderboardPlayerInfoBinding>() {

    class PlayerInfoViewModel : ViewModel() {

        val client = PlayerInfoLoaderClient()

        val player = MutableLiveData<BasePlayer?>()

        lateinit var playerName: String

        var onInfoLoaded: ((name: String) -> Unit)? = null

        fun loadPlayerInfo(name: String) {
            viewModelScope.launch {
                player.value = withContext(Dispatchers.IO) {
                    client.queryPlayerInfo(name)
                }
                player.value?.let {
                    if (it.athletic_all > 0) {
                        onInfoLoaded?.invoke(playerName)
                    }
                }
            }
        }
    }

    private val viewModel by lazyViewModel<PlayerInfoViewModel>()

    private val mainViewModel by lazyActivityViewModel<MainViewModel>()

    fun setPlayer(player: BasePlayer): PlayerInfoDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                viewModel.playerName = player.name
                viewModel.player.value = player
            }
        }
        return this
    }

    fun setPlayerName(name: String): PlayerInfoDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                viewModel.playerName = name
                viewModel.loadPlayerInfo(name)
            }
        }
        return this
    }

    fun doOnInfoLoaded(block: (String) -> Unit): PlayerInfoDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                viewModel.onInfoLoaded = block
            }
        }
        return this
    }

    private val tagAdapter by lazy {
        TagAdapter(viewModel.playerName)
    }

    private fun updateStarTooltip(ibStar: View) {
        if (ibStar.isActivated) {
            ibStar.setTooltipCompat(getString(R.string.unfollow))
        } else {
            ibStar.setTooltipCompat(getString(R.string.follow))
        }
    }

    override fun onDialogCreated(dialog: Dialog) {
        binding.infoContainer.tvPlayerName.text = viewModel.playerName
        viewModel.player.observe(this) {
            binding.root.rootView.beginDelayedTransition()
            if (it == null) {
                binding.progress.show()
                return@observe
            }
            binding.progress.hide()
            binding.infoContainer.apply {
                tvRank.text = it.rank.toString()
                val percentFormat =
                    NumberFormat.getPercentInstance().apply { maximumFractionDigits = 2 }
                tvWinRate.text =
                    percentFormat.format(it.athletic_win.toFloat() / it.athletic_all)
                tvStats.text = R.string.format_stats.format(
                    it.athletic_all,
                    it.athletic_win,
                    it.athletic_lose,
                    it.athletic_draw
                )
                tvDp.text = String.format("%.2f", it.pt)
                ibStar.isActivated = PlayerInfoManager.isFollowed(it.name)
                updateStarTooltip(ibStar)
                rvTags.adapter = tagAdapter
                ibHistory.setOnClickListener {
                    if (tag == "player-info-from-history") {
                        dismiss()
                    }
                    HistoryDialog().setPlayerName(viewModel.playerName)
                        .show(parentFragmentManager, "history")
                }
                ibHistory.setTooltipCompat(R.string.duel_history.resText)
            }
        }
        binding.infoContainer.ibStar.setOnClickListener {
            val name = viewModel.playerName
            val prevFollowed = it.isActivated
            it.isActivated = !prevFollowed
            PlayerInfoManager.toggleFollowingState(name)
            DuelListAdapter.broadcastAllChanged(DuelListAdapter.Payload.FOLLOWING_STATE)
            updateStarTooltip(it)
            mainViewModel.notifyPlayerInfoChanged(name)
            if (it.isActivated) {
                toast(R.string.format_follow_player.format(name))
            } else {
                toast(R.string.format_unfollow_player.format(name))
            }
        }
        tagAdapter.doOnTagChanged { _, _ ->
            mainViewModel.notifyPlayerInfoChanged(viewModel.playerName)
        }
    }
}