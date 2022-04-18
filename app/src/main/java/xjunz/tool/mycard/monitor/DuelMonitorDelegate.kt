package xjunz.tool.mycard.monitor

import androidx.lifecycle.MutableLiveData
import xjunz.tool.mycard.model.Duel

/**
 * The delegate of an abstract duel monitor.
 *
 * @author xjunz 2022/3/3
 */
interface DuelMonitorDelegate {

    val state: MutableLiveData<Int>

    val duelList: List<Duel>

    val isInitialized: Boolean

    val lastUpdateTimestamp: Long

    fun addEventObserverIfAbsent(observer: DuelMonitorEventObserver)

    fun removeEventObserve(observer: DuelMonitorEventObserver)

    fun clearAllIfOutOfDate()

}