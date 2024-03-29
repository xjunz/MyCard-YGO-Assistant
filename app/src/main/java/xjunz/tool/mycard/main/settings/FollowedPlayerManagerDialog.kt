package xjunz.tool.mycard.main.settings

import android.app.Dialog
import android.os.Bundle
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.common.DropdownArrayAdapter
import xjunz.tool.mycard.databinding.DialogFollowedPlayerManagerBinding
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.showSimplePromptDialog
import xjunz.tool.mycard.ktx.textString
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.DuelListAdapter

class FollowedPlayerManagerDialog : BaseBottomSheetDialog<DialogFollowedPlayerManagerBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogStyle)
    }

    private val BUNDLE_KEY_SHOW_UNFOLLOW_ALL_DIALOG = "bundle.key.SHOW_UNFOLLOW_ALL_DIALOG"

    private var unfollowAllConfirmationDialog: Dialog? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.setContentView(binding.root)
        }
    }

    private val followedPlayers by lazy {
        PlayerInfoManager.getFollowedPlayers().toMutableList()
    }

    private val playerAdapter by lazy {
        DropdownArrayAdapter(requireContext(), followedPlayers)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            BUNDLE_KEY_SHOW_UNFOLLOW_ALL_DIALOG,
            unfollowAllConfirmationDialog?.isShowing == true
        )
    }

    override fun onDialogCreated(dialog: Dialog) {
        binding.menuFollowedPlayer.setAdapter(playerAdapter)
        binding.btnFollowPlayer.setOnClickListener {
            val name = binding.etPlayerName.textString
            if (name.isBlank()) {
                toast(R.string.input_is_empty)
                return@setOnClickListener
            }
            if (followedPlayers.contains(name)) {
                toast(R.string.player_already_followed)
                return@setOnClickListener
            }
            PlayerInfoManager.toggleFollowingState(name)
            followedPlayers.add(name)
            playerAdapter.notifyDataSetChanged()
            binding.etPlayerName.text = null
            toast(R.string.format_follow_player.format(name))
            DuelListAdapter.broadcastOrderChanged()
        }
        binding.menuFollowedPlayer.setOnItemClickListener { _, _, pos, _ ->
            val name = followedPlayers[pos]
            requireContext().showSimplePromptDialog(
                msg = R.string.format_prompt_unfollow_player.format(name),
                showCancellationBtn = true
            ) {
                PlayerInfoManager.toggleFollowingState(name)
                followedPlayers.removeAt(pos)
                playerAdapter.notifyDataSetChanged()
                DuelListAdapter.broadcastOrderChanged()
            }.setOnDismissListener {
                binding.menuFollowedPlayer.text = null
            }
        }
        binding.btnUnfollowAll.setOnClickListener {
            showUnfollowAllConfirmationDialog()
        }
    }

    override fun onDialogCreated(dialog: Dialog, savedInstanceState: Bundle?) {
        if (savedInstanceState?.getBoolean(BUNDLE_KEY_SHOW_UNFOLLOW_ALL_DIALOG) == true) {
            showUnfollowAllConfirmationDialog()
        }
    }


    private fun showUnfollowAllConfirmationDialog() {
        unfollowAllConfirmationDialog =
            requireContext().showSimplePromptDialog(msg = R.string.prompt_unfollow_all_players) {
                PlayerInfoManager.unfollowAll()
                followedPlayers.clear()
                playerAdapter.notifyDataSetChanged()
                DuelListAdapter.broadcastOrderChanged()
            }
    }
}