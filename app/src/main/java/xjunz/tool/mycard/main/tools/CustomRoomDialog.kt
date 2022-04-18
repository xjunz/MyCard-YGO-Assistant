package xjunz.tool.mycard.main.tools

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.EditText
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogCustomRoomBinding
import xjunz.tool.mycard.game.Game
import xjunz.tool.mycard.game.GameLauncher.launch
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.account.AccountManager

class CustomRoomDialog : BaseBottomSheetDialog<DialogCustomRoomBinding>() {

    private val game = Game()

    override fun onDialogCreated(dialog: Dialog) {
        bindViews()
        initViews()
    }

    private fun checkFields(): Boolean {
        binding.apply {
            if (!checkEmpty(menuHostName) { host = it }) return false
            if (!checkRange(etPort, 0..65535) { port = it }) return false
            if (!checkRange(etLp, Game.LIFE_POINTS_RANGE) { lp = it }) return false
            if (!checkRange(etStartDraw, Game.START_DRAW_RANGE) { startDraw = it }) return false
            if (!checkRange(etPerDraw, Game.PER_DRAW_RANGE) { perDraw = it }) return false
            if (!checkRange(etDuration, Game.TURN_DURATION_RANGE) {
                    turnDuration = it
                }) return false
            game.noCheck = swNoCheck.isChecked
            game.noFlList = swNoFlList.isChecked
            game.noShuffle = swNoShuffle.isChecked
            game.password = etPassword.textString
            if (etUsername.textString.isNotBlank()) {
                game.username = etUsername.textString
            }
        }
        return true
    }

    private fun initViews() = binding.apply {
        val presetHosts = R.array.preset_hosts.resArray
        menuHostName.setEntries(presetHosts, true) { game.host = presetHosts[it] }
        menuRule.setEntries(R.array.rules, true) { game.rule = it }
        menuMode.setEntries(R.array.modes, true) { game.mode = it }
        menuCardPool.setEntries(R.array.card_pools, true) { game.cardPool = it }
        btnGo.setOnClickListener {
            if (!checkFields()) return@setOnClickListener
            game.launch()
        }
        btnCopy.setOnClickListener {
            if (!checkFields()) return@setOnClickListener
            val clipboardManager = requireContext().getSystemService(ClipboardManager::class.java)
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText("ygo-room-id", game.generateRoomId())
            )
            toast(R.string.copied_to_clipboard)
        }
        if (AccountManager.hasLogin()) {
            etUsername.setText(AccountManager.reqUsername())
        }
    }

    private fun bindViews() = binding.apply {
        etPort.setText(game.port.toString())
        etLp.setText(game.lp.toString())
        etDuration.setText(game.turnDuration.toString())
        etStartDraw.setText(game.startDraw.toString())
        etPerDraw.setText(game.perDraw.toString())
    }

    private inline fun checkEmpty(et: EditText, doOnNotEmpty: Game.(String) -> Unit): Boolean {
        val str = et.textString
        if (str.isBlank()) {
            et.requestFocus()
            et.shake()
            toast(R.string.input_is_empty)
            doOnNotEmpty(game, str)
            return false
        }
        return true
    }

    private inline fun checkRange(
        et: EditText,
        range: IntRange,
        doOnSuccess: Game.(Int) -> Unit
    ): Boolean {
        val num = et.textString.toIntOrNull() ?: -1
        if (num !in range) {
            et.requestFocus()
            et.shake()
            toast(R.string.format_error_range.format(range.first, range.last))
            return false
        }
        doOnSuccess(game, num)
        return true
    }
}