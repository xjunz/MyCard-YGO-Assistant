package xjunz.tool.mycard.ktx

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner


operator fun <T : ViewModel> ViewModelStoreOwner.get(cls: Class<T>): T {
    return ViewModelProvider(this)[cls]
}

inline fun <reified VM : ViewModel> ViewModelStoreOwner.lazyViewModel() = lazy {
    get(VM::class.java)
}

inline fun <reified VM : ViewModel> Fragment.lazyActivityViewModel() = lazy {
    requireActivity()[VM::class.java]
}

inline fun <reified VM : ViewModel> ViewModelStoreOwner.lazyInnerViewModel() = lazy {
    ViewModelProvider(this)[VM::class.java]
}

