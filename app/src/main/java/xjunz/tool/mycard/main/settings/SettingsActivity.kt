package xjunz.tool.mycard.main.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePadding
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import xjunz.tool.mycard.R
import xjunz.tool.mycard.databinding.ActivitySettingsBinding
import xjunz.tool.mycard.ktx.applySystemInsets
import xjunz.tool.mycard.ktx.dpFloat
import xjunz.tool.mycard.ktx.showSimplePromptDialog
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.DuelListAdapter
import xjunz.tool.mycard.monitor.push.DuelPushManager
import xjunz.tool.mycard.monitor.push.DuelPushManager.addToAll

/**
 * @author xjunz 2022/3/16
 */
class SettingsActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivitySettingsBinding.inflate(layoutInflater)
    }

    private val adapter by lazy {
        PushCriteriaAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        initTransitions()
        setContentView(binding.root)
        initToggles()
        binding.rvCriteria.adapter = adapter
        binding.btnAddCriteria.setOnClickListener {
            if (DuelPushManager.ALL_CRITERIA.size == Configs.MAX_PUSH_CRITERIA_COUNT) {
                toast(R.string.prompt_max_criteria_count)
                return@setOnClickListener
            }
            DuelPushCriteriaEditorDialog().doOnConfirmed {
                it.addToAll()
                adapter.beginDelayedTransition()
                adapter.notifyItemInserted(DuelPushManager.ALL_CRITERIA.size - 1)
                // hide bottom divider
                adapter.notifyItemChanged(DuelPushManager.ALL_CRITERIA.size - 2)
            }.show(supportFragmentManager, "criteria-editor")
        }
        binding.scrollView.applySystemInsets { v, insets ->
            v.updatePadding(bottom = insets.bottom)
        }
        binding.btnManageTags.isCheckable = false
        binding.btnManageTags.setOnClickListener {
            TagManagerDialog().show(supportFragmentManager, "tag-manager")
        }
        binding.btnManageFollowed.isCheckable = false
        binding.btnManageFollowed.setOnClickListener {
            FollowedPlayerManagerDialog().show(supportFragmentManager, "followed-manager")
        }
        binding.btnClose.setOnClickListener {
            finishAfterTransition()
        }
    }

    private fun initTransitions() {
        window.sharedElementEnterTransition = buildSharedElementEnterTransition()
        window.sharedElementReturnTransition = buildSharedElementReturnTransition()
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        binding.root.doOnPreDraw {
            binding.scrollView.background.alpha = (.92 * 0xff).toInt()
        }
    }

    private fun initToggles() {
        binding.swDisableNotifications.isChecked = Configs.isNotificationDisabled
        binding.swDisableNotifications.setOnCheckedChangeListener { _, isChecked ->
            Configs.isNotificationDisabled = isChecked
            if (isChecked && DuelPushManager.shownPushes.isNotEmpty()) {
                showSimplePromptDialog(msg = R.string.prompt_clear_all_notifications) {
                    DuelPushManager.cancelAll()
                }
            }
        }
        binding.swPinFollowedDuels.isChecked = Configs.shouldPinFollowedDuels
        binding.swPinFollowedDuels.setOnCheckedChangeListener { _, isChecked ->
            Configs.shouldPinFollowedDuels = isChecked
            DuelListAdapter.broadcastOrderChanged()
        }
    }

    private fun buildSharedElementEnterTransition() =
        MaterialContainerTransform(this, true).apply {
            addTarget(R.id.scroll_view)
            scrimColor = 0
            startElevation = 6.dpFloat
            endElevation = 10.dpFloat
            duration = 300
            fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
        }

    private fun buildSharedElementReturnTransition() =
        MaterialContainerTransform(this, false).apply {
            addTarget(R.id.scroll_view)
            startElevation = 10.dpFloat
            endElevation = 6.dpFloat
            scrimColor = 0
            duration = 250
            fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
        }
}