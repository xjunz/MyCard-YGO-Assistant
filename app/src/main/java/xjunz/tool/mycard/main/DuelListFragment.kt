package xjunz.tool.mycard.main

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import xjunz.tool.mycard.R
import xjunz.tool.mycard.databinding.FragmentDuelBinding
import xjunz.tool.mycard.ktx.applySystemInsets
import xjunz.tool.mycard.ktx.asStateList
import xjunz.tool.mycard.ktx.beginDelayedTransition
import xjunz.tool.mycard.ktx.dp
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.formatToDate
import xjunz.tool.mycard.ktx.launchSharedElementActivity
import xjunz.tool.mycard.ktx.lazyActivityViewModel
import xjunz.tool.mycard.ktx.resColor
import xjunz.tool.mycard.ktx.resText
import xjunz.tool.mycard.ktx.resolveAttribute
import xjunz.tool.mycard.ktx.setTooltipCompat
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.filter.DuelListFilterDialog
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.main.settings.SettingsActivity
import xjunz.tool.mycard.monitor.State

/**
 * @author xjunz 2022/2/10
 */
class DuelListFragment : Fragment() {

    private val viewModel by lazyActivityViewModel<MainViewModel>()

    private val duelAdapter by lazy { DuelListAdapter(viewModel) }

    private lateinit var binding: FragmentDuelBinding

    private val headerChecker = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentDuelBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var shouldCountDown = false

    private val countDownHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (viewModel.monitorState.value !in State.DISCONNECTED) return
            binding.topBar.beginDelayedTransition()
            val remaining = msg.what
            if (remaining == 0) {
                binding.btnController.isEnabled = true
                binding.btnController.text = R.string.restart_service.resText
            } else {
                binding.btnController.text = R.string.format_retry_countdown.format(remaining)
                sendEmptyMessageDelayed(remaining - 1, 1000)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().setExitSharedElementCallback(
            MaterialContainerTransformSharedElementCallback()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerViewAndTopBar()
        binding.fabConfig.setOnClickListener {
            requireActivity().launchSharedElementActivity(SettingsActivity::class.java, it)
        }
        viewModel.bottomBarHeight.observe(viewLifecycleOwner) {
            binding.fabConfig.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = it + 16.dp
            }
        }
        viewModel.hasDataShown.observe(viewLifecycleOwner) {
            binding.root.beginDelayedTransition(
                MaterialFadeThrough(), target = binding.connectivityPrompt
            )
            binding.connectivityPrompt.isVisible = !it
            if (Configs.hasFilterApplied) {
                if (!it && viewModel.isServiceBound()
                    && viewModel.monitorState.value == State.CONNECTED
                ) {
                    binding.btnFilter.iconTint =
                        requireContext().resolveAttribute(com.google.android.material.R.attr.colorError).resColor.asStateList
                } else {
                    binding.btnFilter.iconTint =
                        requireContext().resolveAttribute(com.google.android.material.R.attr.colorPrimary).resColor.asStateList
                }
            } else {
                binding.btnFilter.iconTint =
                    requireContext().resolveAttribute(android.R.attr.textColorTertiary).resColor.asStateList
            }
        }
        binding.btnClearAll.setOnClickListener {
            viewModel.monitorService.clearAllIfOutOfDate()
            shouldShowHeader = false
        }
        viewModel.isServiceBound.observe(viewLifecycleOwner) {
            if (it) {
                binding.rvDuel.doOnPreDraw {
                    binding.rvDuel.adapter = duelAdapter
                }
                viewModel.monitorState.observe(viewLifecycleOwner, controllerObserver)
                viewModel.monitorState.observe(viewLifecycleOwner, promptObserver)
            }
        }
        viewModel.onDuelListFilterChanged.observe(viewLifecycleOwner) {
            if (it) {
                duelAdapter.notifyFilterChanged()
            }
        }
        val llm = binding.rvDuel.layoutManager as LinearLayoutManager
        viewModel.onNavigationReselected.observe(viewLifecycleOwner) {
            if (it == R.id.item_duel) {
                if (llm.findFirstCompletelyVisibleItemPosition() != 0) {
                    binding.rvDuel.smoothScrollToPosition(0)
                    toast(R.string.tip_return_to_top)
                }
            }
        }
        if (Configs.shouldShowBackToTopBalloon) {
            binding.rvDuel.addOnScrollListener(object : OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        if (llm.findFirstCompletelyVisibleItemPosition() >= 20) {
                            binding.rvDuel.removeOnScrollListener(this)
                            viewModel.shouldShowBackToTopBalloon.value = true
                        }
                    }
                }
            })
        }
        binding.tvTitle.setOnLongClickListener {
            Configs.clearBalloonPrefs()
            return@setOnLongClickListener true
        }
        val checker = object : Runnable {
            override fun run() {
                if (shouldShowHeader && System.currentTimeMillis()
                    - viewModel.monitorService.lastUpdateTimestamp > 1000 * 60 * 30
                ) {
                    viewModel.monitorService.clearAllIfOutOfDate()
                    shouldShowHeader = false
                }
                headerChecker.postDelayed(this, 30 * 1000)
            }
        }
        headerChecker.post(checker)
        binding.tvTitle.setOnClickListener {
            binding.btnFilter.performClick()
        }
        binding.btnFilter.setOnClickListener {
            DuelListFilterDialog().show(parentFragmentManager, "filter")
        }
        binding.btnFilter.setTooltipCompat(R.string.filter_and_sort.resText)
        binding.btnFilter.setOnLongClickListener {
            duelAdapter.notifyFilterChanged()
            return@setOnLongClickListener true
        }
    }

    private val promptObserver = Observer<Int> {
        shouldShowHeader = viewModel.monitorService.isInitialized && it in State.DISCONNECTED
        val prompt = when (it) {
            State.DISCONNECTED_SERVER -> R.string.server_error
            State.DISCONNECTED_NETWORK -> R.string.network_error
            State.DISCONNECTED_TIMED_OUT -> R.string.request_timed_out
            State.DISCONNECTED_UNEXPECTED -> R.string.unknown_error
            State.DISCONNECTED_REJECTED -> R.string.too_frequent
            State.DISCONNECTED_USER_REQUEST -> R.string.service_is_stopped
            State.CONNECTING -> R.string.connecting_to_to_server
            State.CONNECTED -> R.string.service_is_running
            else -> R.string.service_is_idle
        }
        binding.tvPrompt.setText(prompt)
        if (it == State.CONNECTED && viewModel.monitorService.duelList.isEmpty()) {
            binding.tvPrompt.setText(R.string.no_duels_now)
        }
        if (it in State.DISCONNECTED_ERROR && viewModel.hasDataShown.value == true) toast(prompt)
    }

    private var balloon: Balloon? = null

    private val controllerObserver = Observer<Int> { state ->
        binding.topBar.beginDelayedTransition()
        binding.btnController.apply {
            when (state) {
                in State.DISCONNECTED -> {
                    setOnClickListener {
                        shouldCountDown = true
                        viewModel.monitorService.startMonitor()
                    }
                    isEnabled = true
                    isActivated = false
                    when (state) {
                        State.DISCONNECTED_IDLE -> {
                            text = R.string.start_service.resText
                        }

                        State.DISCONNECTED_NETWORK -> {
                            text = R.string.restart_service.resText
                        }

                        else -> if (shouldCountDown) {
                            isEnabled = false
                            shouldCountDown = false
                            countDownHandler.sendEmptyMessage(3)
                        } else {
                            text = R.string.restart_service.resText
                        }
                    }
                }

                State.CONNECTED -> {
                    if (Configs.shouldShowFilterBalloon) {
                        if (balloon != null) {
                            balloon?.dismiss()
                        } else {
                            balloon = createBalloon(requireContext()) {
                                setHeight(BalloonSizeSpec.WRAP)
                                setTextResource(R.string.tip_filter_and_sort)
                                setTextSize(13f)
                                setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                                setArrowSize(10)
                                setArrowPosition(0.5f)
                                setPadding(12)
                                setCornerRadius(8f)
                                setBackgroundColor(requireContext().resolveAttribute(com.google.android.material.R.attr.colorPrimary).resColor)
                                setBalloonAnimation(BalloonAnimation.FADE)
                                setLifecycleOwner(viewLifecycleOwner)
                                build()
                                setOnBalloonDismissListener {
                                    Configs.shouldShowFilterBalloon = false
                                }
                            }
                            balloon?.showAlignBottom(binding.btnFilter)
                        }
                    }
                    setOnClickListener {
                        MaterialAlertDialogBuilder(context).setTitle(R.string.prompt)
                            .setMessage(R.string.tip_stop_service)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                shouldCountDown = true
                                viewModel.monitorService.stopMonitor()
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    }
                    isActivated = true
                    isEnabled = true
                    text = R.string.stop_monitor_service.resText
                }

                State.CONNECTING -> {
                    isActivated = false
                    isEnabled = false
                    text = R.string.starting_service.resText
                }
            }
        }
    }

    private var shouldShowHeader: Boolean = false
        set(should) {
            if (should == field) return
            field = should
            updateHeader()
        }

    private fun updateHeader() {
        binding.topBar.beginDelayedTransition()
        if (shouldShowHeader) {
            binding.cardHeader.isVisible = true
            binding.cardHeader.doOnPreDraw {
                binding.rvDuel.updatePadding(top = it.bottom + 4.dp)
            }
            val timestamp = viewModel.monitorService.lastUpdateTimestamp
            if (timestamp > 0 && viewModel.monitorService.duelList.isNotEmpty()) {
                binding.tvTimelinessTip.text =
                    R.string.format_last_update_time.format(timestamp.formatToDate())
            }
        } else {
            binding.cardHeader.isVisible = false
            binding.rvDuel.updatePadding(top = binding.cvTitle.bottom)
        }
    }

    private fun initRecyclerViewAndTopBar(
        rv: RecyclerView = binding.rvDuel, topBar: View = binding.topBar
    ) {
        var paddingTop = -1
        rv.applySystemInsets { _, insets ->
            topBar.updatePadding(top = insets.top)
            if (paddingTop == -1) {
                paddingTop = topBar.height + insets.top
                rv.updatePadding(top = paddingTop)
                // Make an extra padding to avoid the bottom bar overlapping items while the recyclerview
                // is not even scrollable.
                rv.updatePadding(bottom = insets.bottom + viewModel.bottomBarHeight.value!!)
            }
        }
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy == 0) return
                viewModel.shouldShowBottomBar.value = dy < 0
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!viewModel.isServiceBound()) return
        viewModel.monitorState.apply {
            removeObservers(viewLifecycleOwner)
            if (value in State.DISCONNECTED) value = State.DISCONNECTED_IDLE
        }
        countDownHandler.removeCallbacksAndMessages(null)
    }
}