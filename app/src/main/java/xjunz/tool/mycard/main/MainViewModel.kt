package xjunz.tool.mycard.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import xjunz.tool.mycard.monitor.DuelMonitorService

class MainViewModel : ViewModel() {

    fun isServiceBound(): Boolean {
        return isServiceBound.value == true
    }

    var isServiceBound = MutableLiveData<Boolean>()

    val shouldShowBottomBar = MutableLiveData<Boolean>()

    val bottomBarHeight = MutableLiveData<Int>()

    var binder: DuelMonitorService.DuelMonitorBinder? = null

    val monitorService get() = binder!!

    val monitorState get() = monitorService.state

    val playerInfoLoader get() = monitorService.playerInfoLoader

    val hasDataShown = MutableLiveData(false)

    val hasUpdates = MutableLiveData(false)
}