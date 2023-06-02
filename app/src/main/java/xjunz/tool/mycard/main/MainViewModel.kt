package xjunz.tool.mycard.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import xjunz.tool.mycard.main.settings.Configs
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

    val isMineAsHome = MutableLiveData(Configs.isMineAsHome)

    val onPlayerInfoChanged = MutableLiveData<String?>()

    val onDuelListFilterChanged = MutableLiveData(false)

    val onNavigationReselected = MutableLiveData<Int>()

    val shouldShowBackToTopBalloon = MutableLiveData(false)

    fun notifyOnNavigationItemReselected(id: Int) {
        onNavigationReselected.value = id
        onNavigationReselected.postValue(-1)
    }

    fun notifyPlayerInfoChanged(name: String) {
        onPlayerInfoChanged.value = name
        onPlayerInfoChanged.postValue(null)
    }

    fun notifyDuelListFilterChanged() {
        onDuelListFilterChanged.value = true
        onDuelListFilterChanged.postValue(false)
    }
}