package xjunz.tool.mycard.main

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xjunz.tool.mycard.BuildConfig
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.databinding.ActivityMainBinding
import xjunz.tool.mycard.ktx.applySystemInsets
import xjunz.tool.mycard.ktx.lazyViewModel
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.monitor.DuelMonitorService
import xjunz.tool.mycard.outer.UpdateChecker
import xjunz.tool.mycard.util.debugLog

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val mineFragment by lazy {
        MineFragment()
    }

    private val duelListFragment by lazy {
        DuelListFragment()
    }

    private val viewModel by lazyViewModel<MainViewModel>()

    private val fragments by lazy {
        if (Configs.isMineAsHome) arrayOf(
            mineFragment, duelListFragment, LeaderboardFragment()
        ) else arrayOf(
            duelListFragment, LeaderboardFragment(), mineFragment
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        initViewPager()
        initNavigationBar()
        bindMonitorService()
        checkForUpdatesIfNeeded()
    }

    private fun checkForUpdatesIfNeeded() {
        if (app.hasUpdates == null) lifecycleScope.launch(Dispatchers.IO) {
            UpdateChecker().use {
                it.checkUpdate()
            }.onSuccess { info ->
                app.hasUpdates = info.build.toIntOrNull() ?: 0 > BuildConfig.VERSION_CODE
                viewModel.hasUpdates.postValue(app.hasUpdates)
                debugLog(info)
            }.onFailure {
                it.printStackTrace()
            }
        } else {
            viewModel.hasUpdates.value = app.hasUpdates
        }
        viewModel.hasUpdates.observe(this) {
            val badge = binding.realNavigationBar.getOrCreateBadge(R.id.item_mine)
            badge.isVisible = it
        }
    }

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                service as DuelMonitorService.DuelMonitorBinder
                viewModel.binder = service
                viewModel.isServiceBound.value = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private fun bindMonitorService() {
        val intent = Intent(this, DuelMonitorService::class.java)
        if (startService(intent) != null) bindService(intent, serviceConn, BIND_AUTO_CREATE)
    }

    private fun unbindServiceIfNeeded() {
        if (viewModel.isServiceBound()) {
            unbindService(serviceConn)
            viewModel.isServiceBound.value = false
        }
    }

    private fun initNavigationBar() {
        binding.navigationBar.apply {
            val behavior = (layoutParams as CoordinatorLayout.LayoutParams).behavior
            if (behavior != null && behavior is HideBottomViewOnScrollBehavior) {
                viewModel.shouldShowBottomBar.observe(this@MainActivity) { should ->
                    if (should) behavior.slideUp(this) else behavior.slideDown(this)
                }
            }
            doOnPreDraw {
                viewModel.bottomBarHeight.value = it.height
            }
        }
        binding.fakeNavigationBar.apply {
            applySystemInsets { v, insets ->
                v.updateLayoutParams { height = insets.bottom }
            }
        }
        binding.realNavigationBar.apply {
            menuInflater.inflate(
                if (Configs.isMineAsHome) R.menu.mine_as_home else R.menu.menu_bottom_bar, menu
            )
            binding.fakeNavigationBar.elevation = elevation
            val back = background.constantState?.newDrawable() as MaterialShapeDrawable
            back.initializeElevationOverlay(this@MainActivity)
            binding.fakeNavigationBar.background = back
            setOnItemSelectedListener s@{
                menu.forEachIndexed { index, item ->
                    if (item === it) {
                        binding.viewPager.setCurrentItem(index, true)
                        return@s true
                    }
                }
                return@s false
            }
            setOnApplyWindowInsetsListener(null)
            updatePadding(bottom = 0)
        }
    }

    private fun initViewPager() = binding.viewPager.apply {
        adapter = object : FragmentStateAdapter(this@MainActivity) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.realNavigationBar.menu[position].isChecked = true
            }
        })
    }

    override fun onBackPressed() {
        if (BuildConfig.DEBUG) {
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindServiceIfNeeded()
    }

}