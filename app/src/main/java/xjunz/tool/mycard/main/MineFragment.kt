package xjunz.tool.mycard.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.forEachIndexed
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xjunz.tool.mycard.R
import xjunz.tool.mycard.databinding.FragmentMineBinding
import xjunz.tool.mycard.game.GameLauncher
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.account.AccountManager
import xjunz.tool.mycard.main.account.LoginDialog
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.main.tools.CustomRoomDialog
import xjunz.tool.mycard.main.tools.PrescriptsDownloadDialog
import xjunz.tool.mycard.main.tools.ProbabilityCalculatorDialog
import xjunz.tool.mycard.outer.Feedbacks

class MineFragment : Fragment(), LifecycleEventObserver {

    private val viewModel by lazyActivityViewModel<MainViewModel>()

    private lateinit var binding: FragmentMineBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        viewModel.isServiceBound.observe(viewLifecycleOwner) {
            if (it) {
                updateLoginState()
                initUserInfo()
                requireActivity().lifecycle.addObserver(this@MineFragment)
            }
        }
    }

    private suspend fun loadAvatar(): Bitmap? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = AccountManager.getAvatarUrl().openConnection()
                conn.connectTimeout = 2_000
                conn.readTimeout = 5_000
                val bmp = BitmapFactory.decodeStream(conn.getInputStream())
                AccountManager.persistAvatarIfNeeded(bmp)
                return@runCatching bmp
            }.onFailure {
                it.printStackTrace()
            }.getOrNull()
        }
    }

    private fun initViews() = binding.apply {
        btnReload.isCheckable = false
        val refresh = {
            updateLoginState()
            initUserInfo()
            loadPlayerInfo()
        }
        btnLogInOut.setOnClickListener {
            if (AccountManager.hasLogin()) {
                requireActivity().showSimplePromptDialog(msg = R.string.prompt_log_out) {
                    AccountManager.logout()
                    refresh()
                }
            } else {
                LoginDialog().doOnSuccess(refresh).show(parentFragmentManager, "login")
            }
        }
        btnReload.setOnClickListener {
            loadPlayerInfo(true)
        }
        btnReload.setTooltipCompat(R.string.reload.resText)
        root.applySystemInsets { v, insets ->
            v.updatePadding(top = insets.top, bottom = viewModel.bottomBarHeight.value!!)
        }
        btnMatch.setOnClickListener {
            if (GameLauncher.exists()) {
                MatchDialog().show(parentFragmentManager, "match")
            } else {
                toast(R.string.no_game_launcher_found)
            }
        }
        if (Configs.isMineAsHome) {
            itemSetAsHome.setText(R.string.cancel_set_as_home)
        } else {
            itemSetAsHome.setText(R.string.set_as_home)
        }
        itemSetAsHome.setOnClickListener {
            val next = !Configs.isMineAsHome
            Configs.isMineAsHome = next
            viewModel.isMineAsHome.value = next
            if (next) {
                itemSetAsHome.setText(R.string.cancel_set_as_home)
            } else {
                itemSetAsHome.setText(R.string.set_as_home)
            }
        }
        itemCalculator.setOnClickListener {
            ProbabilityCalculatorDialog().show(parentFragmentManager, "calculator")
        }
        itemRoomAssistant.setOnClickListener {
            CustomRoomDialog().show(parentFragmentManager, "custom-room")
        }
        itemDownloadPrescripts.setOnClickListener {
            PrescriptsDownloadDialog().show(parentFragmentManager, "download")
        }
        itemVersionInfo.setOnClickListener {
            AboutDialog().show(parentFragmentManager, "about")
        }
        viewModel.hasUpdates.observe(viewLifecycleOwner) {
            tvUpdatesBadge.isVisible = it
        }
        itemFeedback.setOnClickListener {
            Feedbacks.showFeedbackDialog(requireContext())
        }
    }

    private var userInfoLoaded = false
    private var playerInfoLoaded = false

    private inline val isAllLoaded get() = userInfoLoaded && playerInfoLoaded

    private fun initUserInfo() {
        if (!AccountManager.hasLogin()) return
        // load user info
        lifecycleScope.launch {
            binding.apply {
                userInfoLoaded = false
                btnLogInOut.isEnabled = false
                btnReload.isEnabled = false
                // nothing to do with user info when updating
                val userInfo = AccountManager.peekUser()
                if (userInfo.name != null && userInfo.username != userInfo.name) {
                    tvPlayerName.text = userInfo.username
                    tvName.isVisible = true
                }
                tvName.text = userInfo.name
                tvMemberSince.text = userInfo.memberSince
                val avatar = loadAvatar()
                if (avatar == null) {
                    ivAvatar.setImageResource(R.mipmap.ic_launcher_round)
                } else {
                    ivAvatar.setImageBitmap(avatar)
                }
                userInfoLoaded = true
                btnLogInOut.isEnabled = isAllLoaded
                btnReload.isEnabled = isAllLoaded
            }
        }
    }

    private fun loadPlayerInfo(showToast: Boolean = false) {
        if (!AccountManager.hasLogin()) return
        // load player info
        lifecycleScope.launch {
            binding.apply {
                playerInfoLoaded = false
                btnLogInOut.isEnabled = false
                btnReload.isEnabled = false
                val loaded = viewModel.playerInfoLoader.queryMyInfo()
                if (loaded != null) {
                    binding.apply {
                        tvRank.text = loaded.rank.toString()
                        tvPt.text = loaded.pt.toString()
                        tvExp.text = loaded.exp.toString()
                        tvStats.text = R.string.format_stats.format(
                            loaded.athletic_all, loaded.athletic_win,
                            loaded.athletic_lose, loaded.athletic_draw
                        )
                        tvWinRate.text =
                            R.string.format_percent.format(loaded.athletic_wl_ratio)
                    }
                    if (showToast) toast(R.string.refresh_successfully)
                } else if (showToast) {
                    toast(R.string.refresh_failed)
                }
                playerInfoLoaded = true
                btnLogInOut.isEnabled = isAllLoaded
                btnReload.isEnabled = isAllLoaded
            }
        }
    }

    private fun updateLoginState() {
        binding.apply {
            val loggedIn = AccountManager.hasLogin()
            btnLogInOut.isActivated = loggedIn
            btnReload.isEnabled = loggedIn
            btnMatch.isEnabled = loggedIn
            if (!loggedIn) {
                tvName.isVisible = false
                btnLogInOut.text = R.string.log_in_to_mycard.resStr
                clInfoContainer.forEachIndexed { index, view ->
                    if (index % 2 != 0 && view is TextView) view.text = R.string.loading.resText
                }
                tvPlayerName.text = R.string.pls_login.resText
                ivAvatar.setImageResource(R.mipmap.ic_launcher_round)
            } else {
                btnLogInOut.text = R.string.log_out.resStr
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().lifecycle.removeObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_START) {
            loadPlayerInfo()
        }
    }
}