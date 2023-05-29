package xjunz.tool.mycard.main.settings

import android.app.Dialog
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.common.DropdownArrayAdapter
import xjunz.tool.mycard.databinding.DialogTagManagerBinding
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.dp
import xjunz.tool.mycard.ktx.showSimplePromptDialog
import xjunz.tool.mycard.ktx.textString
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.DuelListAdapter
import xjunz.tool.mycard.main.detail.TagAdapter
import xjunz.tool.mycard.util.Motions

class TagManagerDialog : BaseBottomSheetDialog<DialogTagManagerBinding>() {

    private val BUNDLE_KEY_SHOW_CLEAR_ALL_DIALOG = "bundle.key.SHOW_CLEAR_ALL_DIALOG"

    private var clearAllConfirmationDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogStyle)
    }

    private val taggedPlayer by lazy {
        PlayerInfoManager.getTaggedPlayers().toMutableList()
    }

    private val taggedPlayerAdapter by lazy {
        DropdownArrayAdapter(requireContext(), taggedPlayer)
    }

    private val tagAdapter by lazy {
        TagAdapter(currentPlayerName!!).doOnTagChanged { action, _ ->
            DuelListAdapter.broadcastAllChanged(DuelListAdapter.Payload.TAGS)
            if (action == TagAdapter.Action.REMOVE
                && PlayerInfoManager.getTags(currentPlayerName).isEmpty()
            ) {
                TransitionManager.beginDelayedTransition(
                    dialog?.window?.findViewById(android.R.id.content),
                    ChangeBounds().setInterpolator(Motions.EASING_EMPHASIZED)
                )
                binding.rvTagList.isVisible = false
                binding.menuTaggedPlayer.text = null
                taggedPlayer.remove(currentPlayerName)
                taggedPlayerAdapter.notifyDataSetChanged()
            } else if (action == TagAdapter.Action.ADD
                && PlayerInfoManager.getTags(currentPlayerName).size == 1
            ) {
                taggedPlayer.add(currentPlayerName!!)
                taggedPlayerAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun refreshTagAdapter() {
        TransitionManager.beginDelayedTransition(
            dialog?.window?.findViewById(android.R.id.content),
            ChangeBounds().setInterpolator(Motions.EASING_EMPHASIZED)
        )
        if (!binding.rvTagList.isVisible) binding.rvTagList.isVisible = true
        if (binding.rvTagList.adapter == null) {
            binding.rvTagList.adapter = tagAdapter
        } else {
            tagAdapter.updatePlayerName(currentPlayerName!!)
        }
    }

    private var currentPlayerName: String? = null

    override fun onDialogCreated(dialog: Dialog) {
        binding.menuTaggedPlayer.setAdapter(taggedPlayerAdapter)
        binding.menuTaggedPlayer.setOnItemClickListener { _, _, position, _ ->
            currentPlayerName = taggedPlayer[position]
            refreshTagAdapter()
        }
        binding.btnAddTag.setOnClickListener {
            val name = binding.etAddPlayer.textString
            if (name.isBlank()) {
                toast(R.string.input_is_empty)
                return@setOnClickListener
            }
            if (taggedPlayer.contains(name)) {
                toast(R.string.player_already_tagged)
                return@setOnClickListener
            }
            currentPlayerName = name
            refreshTagAdapter()
        }
        binding.etAddPlayer.doOnPreDraw { v ->
            v.updatePadding(right = binding.btnAddTag.width + 16.dp)
        }
        binding.btnClearAll.setOnClickListener {
            showClearAllConfirmationDialog()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            BUNDLE_KEY_SHOW_CLEAR_ALL_DIALOG,
            clearAllConfirmationDialog?.isShowing == true
        )
    }

    override fun onDialogCreated(dialog: Dialog, savedInstanceState: Bundle?) {
        super.onDialogCreated(dialog, savedInstanceState)
        if (savedInstanceState?.getBoolean(BUNDLE_KEY_SHOW_CLEAR_ALL_DIALOG) == true) {
            showClearAllConfirmationDialog()
        }
    }

    private fun showClearAllConfirmationDialog() {
        clearAllConfirmationDialog =
            requireContext().showSimplePromptDialog(msg = R.string.prompt_remove_all_tags) {
                DuelListAdapter.broadcastAllChanged(DuelListAdapter.Payload.TAGS)
                PlayerInfoManager.unfollowAll()
                taggedPlayer.clear()
                refreshTagAdapter()
            }
    }
}