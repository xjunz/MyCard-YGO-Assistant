package xjunz.tool.mycard.main

import android.app.Dialog
import android.view.View
import androidx.lifecycle.lifecycleScope
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogLeaderboardPlayerInfoBinding
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.setTooltipCompat
import xjunz.tool.mycard.main.detail.TagAdapter
import xjunz.tool.mycard.model.LeaderboardPlayer
import java.text.NumberFormat

class LeaderboardPlayerInfoDialog : BaseBottomSheetDialog<DialogLeaderboardPlayerInfoBinding>() {

    lateinit var player: LeaderboardPlayer

    fun setLeaderboardPlayer(player: LeaderboardPlayer): LeaderboardPlayerInfoDialog {
        this.player = player
        return this
    }

    fun doOnStarClicked(callback: (Boolean) -> Unit): LeaderboardPlayerInfoDialog {
        lifecycleScope.launchWhenStarted {
            binding.infoContainer.ibStar.setOnClickListener {
                val prevFollowed = it.isActivated
                it.isActivated = !prevFollowed
                callback(!prevFollowed)
                updateStarTooltip(it)
            }
        }
        return this
    }

    private val tagAdapter by lazy {
        TagAdapter(player.name)
    }

    private fun updateStarTooltip(ibStar: View) {
        if (ibStar.isActivated) {
            ibStar.setTooltipCompat(getString(R.string.unfollow))
        } else {
            ibStar.setTooltipCompat(getString(R.string.follow))
        }
    }

    fun doOnTagChanged(callback: () -> Unit): LeaderboardPlayerInfoDialog {
        lifecycleScope.launchWhenStarted {
            tagAdapter.doOnTagChanged { _, _ ->
                callback()
            }
        }
        return this
    }

    override fun onDialogCreated(dialog: Dialog) {
        binding.infoContainer.apply {
            tvPlayerName.text = player.name
            tvRank.text = player.rank.toString()
            val percentFormat =
                NumberFormat.getPercentInstance().apply { maximumFractionDigits = 2 }
            tvWinRate.text =
                percentFormat.format(player.athletic_win.toFloat() / player.athletic_all)
            tvStats.text = R.string.format_stats.format(
                player.athletic_all,
                player.athletic_win,
                player.athletic_lose,
                player.athletic_draw
            )
            tvDp.text = String.format("%.2f", player.pt)
            ibStar.isActivated = PlayerInfoManager.isFollowed(player.name)
            updateStarTooltip(ibStar)
            rvTags.adapter = tagAdapter
        }
    }
}