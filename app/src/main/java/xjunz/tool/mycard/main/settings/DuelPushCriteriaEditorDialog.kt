package xjunz.tool.mycard.main.settings

import android.app.Dialog
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import androidx.annotation.IntRange
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.common.DropdownArrayAdapter
import xjunz.tool.mycard.databinding.DialogCriteriaEditorBinding
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.monitor.push.DuelPushCriteria
import xjunz.tool.mycard.monitor.push.DuelPushManager
import xjunz.tool.mycard.util.Motions

/**
 * @author xjunz 2022/3/23
 */
class DuelPushCriteriaEditorDialog : BaseBottomSheetDialog<DialogCriteriaEditorBinding>() {

    private var criteria2edit: DuelPushCriteria? = null

    fun editCriteria(criteria: DuelPushCriteria): DuelPushCriteriaEditorDialog {
        criteria2edit = criteria
        return this
    }

    private val presetTags by lazy {
        (PlayerInfoManager.getAllDistinctTags() + R.array.preset_tags.resArray).distinct()
            .toMutableList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogStyle)
    }

    override fun onDialogCreated(dialog: Dialog) {
        initViews()
    }

    private fun initViews() {
        initPlayerCriteria(0)
        initPlayerCriteria(1)
        if (!isInEditMode) {
            binding.tilPreset.isVisible = true
            binding.menuPresetCriteria.setEntries(R.array.preset_criteria) {
                TransitionManager.beginDelayedTransition(
                    dialog?.window!!.findViewById(android.R.id.content),
                    ChangeBounds().setInterpolator(Motions.EASING_EMPHASIZED)
                )
                updateCriteria(DuelPushCriteria.PRESET_CRITERIA_ARRAY[it].copy())
            }
        }
        binding.etPushDelay.setMaxLength(Configs.MAX_PUSH_DELAY_DIGIT_COUNT)
        criteria2edit?.let {
            if (it.pushDelayInMinute != 0) binding.etPushDelay.setText(it.pushDelayInMinute.toString())
        }
        binding.btnDiscard.setOnClickListener {
            dismiss()
        }
    }

    private fun getPlayerBinding(@IntRange(from = 0, to = 1) index: Int) =
        if (index == 0) binding.cardPlayer1 else binding.cardPlayer2

    private fun updateCriteria(criteria: DuelPushCriteria) {
        for (i in 0..1) {
            getPlayerBinding(i).apply {
                (criteria.getPlayerCriteria(i) ?: DuelPushCriteria.PlayerCriteria()).let {
                    etRankStart.setText(if (it.isStartRankLimited) it.rankStart.toString() else null)
                    etRankEnd.setText(if (it.isEndRankLimited) it.rankEnd.toString() else null)
                    swRequireFollowedPlayer.isChecked = it.requireFollowed
                    menuTag.setText(it.requiredTag, false)
                }
            }
        }
        binding.etPushDelay.setText(
            if (criteria.pushDelayInMinute == 0) null
            else criteria.pushDelayInMinute.toString()
        )
    }

    private fun initPlayerCriteria(index: Int) {
        getPlayerBinding(index).apply {
            etRankStart.setMaxLength(Configs.MAX_RANK_DIGIT_COUNT)
            etRankEnd.setMaxLength(Configs.MAX_RANK_DIGIT_COUNT)
            menuTag.setMaxLength(Configs.MAX_TAG_TEXT_LENGTH)
            tvTitle.setText(
                if (index == 0) R.string.criteria_for_one_player else R.string.criteria_for_the_other_player
            )
            criteria2edit?.getPlayerCriteria(index)?.let {
                etRankStart.setText(if (it.isStartRankLimited) it.rankStart.toString() else null)
                etRankEnd.setText(if (it.isEndRankLimited) it.rankEnd.toString() else null)
                swRequireFollowedPlayer.isChecked = it.requireFollowed
                swRequireFollowedPlayer.setOnCheckedChangeListener { _, isChecked ->
                    it.requireFollowed = isChecked
                }
                menuTag.setText(it.requiredTag, false)
            }
            menuTag.setAdapter(
                DropdownArrayAdapter(requireContext(), presetTags)
            )
        }
    }

    private inline val isInEditMode get() = criteria2edit != null

    fun doOnConfirmed(block: (DuelPushCriteria) -> Unit): DuelPushCriteriaEditorDialog {
        lifecycleScope.launchWhenStarted {
            val playerCriteria = Array(2) { DuelPushCriteria.PlayerCriteria() }
            binding.btnConfirm.setOnClickListener {
                for (i in 0..1) {
                    getPlayerBinding(i).apply {
                        val criterion = playerCriteria[i]
                        val start = etRankStart.textString.toIntOrNull()
                        val end = etRankEnd.textString.toIntOrNull()
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
                        criterion.requireFollowed = swRequireFollowedPlayer.isChecked
                        val requiredTag = menuTag.textString
                        if (requiredTag.isNotEmpty()) criterion.requiredTag = requiredTag
                    }
                }
                val limited1 = playerCriteria[0].isLimited
                val limited2 = playerCriteria[1].isLimited
                if (limited1 || limited2) {
                    val target = criteria2edit ?: DuelPushCriteria()
                    target.onePlayerCriteria = if (limited1) playerCriteria[0] else null
                    target.theOtherPlayerCriteria = if (limited2) playerCriteria[1] else null
                    target.pushDelayInMinute = binding.etPushDelay.textString.toIntOrNull() ?: 0
                    if (!isInEditMode && DuelPushManager.ALL_CRITERIA.any { it == target }) {
                        toast(R.string.criteria_existed)
                        return@setOnClickListener
                    }
                    block(target)
                    dismiss()
                } else {
                    toast(R.string.unlimited_criteria)
                }
            }
        }
        return this
    }


}