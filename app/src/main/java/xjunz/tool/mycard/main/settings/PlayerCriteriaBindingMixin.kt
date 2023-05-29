package xjunz.tool.mycard.main.settings

import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.DropdownArrayAdapter
import xjunz.tool.mycard.databinding.LayoutPlayerCriteriaEditorBinding
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.beginDelayedTransition
import xjunz.tool.mycard.ktx.setMaxLength
import xjunz.tool.mycard.ktx.textString
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.monitor.push.DuelFilterCriteria
import xjunz.tool.mycard.monitor.push.DuelPushManager

/**
 * @author xjunz 2023/05/25
 */
class PlayerCriteriaBindingMixin(
    private val bindings: Array<LayoutPlayerCriteriaEditorBinding>,
    private val etDuration: EditText
) {

    fun init(
        criteria: DuelFilterCriteria?,
        btnConfirm: View,
        filterMode: Boolean,
        onConfirmed: (DuelFilterCriteria) -> Unit
    ) {
        bindings.forEachIndexed { player, binding ->
            binding.apply {
                etRankStart.setMaxLength(Configs.MAX_RANK_DIGIT_COUNT)
                etRankEnd.setMaxLength(Configs.MAX_RANK_DIGIT_COUNT)
                menuTag.setMaxLength(Configs.MAX_TAG_TEXT_LENGTH)
                tvTitle.setText(
                    if (player == Duel.PLAYER_1) R.string.criteria_for_one_player
                    else R.string.criteria_for_the_other_player
                )
                criteria?.getPlayerCriteria(player)?.let {
                    etRankStart.setText(if (it.isStartRankLimited) it.rankStart.toString() else null)
                    etRankEnd.setText(if (it.isEndRankLimited) it.rankEnd.toString() else null)
                    swRequireFollowedPlayer.isChecked = it.requireFollowed
                    swRequireFollowedPlayer.setOnCheckedChangeListener { _, isChecked ->
                        it.requireFollowed = isChecked
                    }
                    menuTag.setText(it.requiredTag, false)
                }
                val presetTags = PlayerInfoManager.getAllDistinctTags().distinct().toMutableList()
                menuTag.setAdapter(
                    DropdownArrayAdapter(binding.root.context, presetTags)
                )
                btnReset.setOnClickListener {
                    etRankStart.text?.clear()
                    etRankEnd.text?.clear()
                    menuTag.text.clear()
                    swRequireFollowedPlayer.isChecked = false
                }
                val shouldExpand =
                    !filterMode || (criteria?.getPlayerCriteria(player)?.isLimited == true)
                ibExpand.isActivated = shouldExpand
                group.isVisible = shouldExpand
                ibExpand.setOnClickListener {
                    ibExpand.isActivated = !ibExpand.isActivated
                    binding.root.rootView.beginDelayedTransition()
                    group.isVisible = ibExpand.isActivated
                }
            }
        }
        etDuration.setMaxLength(Configs.MAX_PUSH_DELAY_DIGIT_COUNT)
        doOnConfirm(criteria, btnConfirm, filterMode, onConfirmed)
    }

    fun bind(criteria: DuelFilterCriteria?) {
        bindings.forEachIndexed { player, binding ->
            binding.apply {
                (criteria?.getPlayerCriteria(player) ?: DuelFilterCriteria.PlayerCriteria()).let {
                    etRankStart.setText(if (it.isStartRankLimited) it.rankStart.toString() else null)
                    etRankEnd.setText(if (it.isEndRankLimited) it.rankEnd.toString() else null)
                    swRequireFollowedPlayer.isChecked = it.requireFollowed
                    menuTag.setText(it.requiredTag, false)
                }
            }
        }
        etDuration.setText(
            if (criteria?.pushDelayInMinute == 0) null
            else criteria?.pushDelayInMinute?.toString()
        )
    }

    private fun doOnConfirm(
        criteria: DuelFilterCriteria?,
        btnConfirm: View,
        filterMode: Boolean,
        onConfirmed: (DuelFilterCriteria) -> Unit
    ) {
        val playerCriteria = Array(2) { DuelFilterCriteria.PlayerCriteria() }
        btnConfirm.setOnClickListener {
            Duel.PLAYERS.forEach {
                val binding = bindings[it]
                val criterion = playerCriteria[it]
                val start = binding.etRankStart.textString.toIntOrNull()
                val end = binding.etRankEnd.textString.toIntOrNull()
                if (start != null && end != null && end < start) {
                    toast(R.string.invalid_rank_range)
                    return@setOnClickListener
                }
                if (start != null && start == 0) {
                    toast(R.string.prompt_rank_zero)
                    return@setOnClickListener
                }
                if (end != null && end == 0) {
                    toast(R.string.prompt_rank_zero)
                    return@setOnClickListener
                }
                if (start != null && start > 1) criterion.rankStart = start
                if (end != null) criterion.rankEnd = end
                criterion.requireFollowed = binding.swRequireFollowedPlayer.isChecked
                val requiredTag = binding.menuTag.textString
                if (requiredTag.isNotEmpty()) criterion.requiredTag = requiredTag

            }
            val limited1 = playerCriteria[0].isLimited
            val limited2 = playerCriteria[1].isLimited
            val duration = etDuration.textString.toIntOrNull() ?: 0
            if (filterMode || duration > 0 || limited1 || limited2) {
                val target = criteria ?: DuelFilterCriteria()
                target.onePlayerCriteria = if (limited1) playerCriteria[0] else null
                target.theOtherPlayerCriteria = if (limited2) playerCriteria[1] else null
                target.pushDelayInMinute = duration
                if (!filterMode && criteria == null && DuelPushManager.ALL_CRITERIA.any { it == target }) {
                    toast(R.string.criteria_existed)
                    return@setOnClickListener
                }
                onConfirmed(target)
            } else {
                toast(R.string.unlimited_criteria)
            }
        }
    }
}