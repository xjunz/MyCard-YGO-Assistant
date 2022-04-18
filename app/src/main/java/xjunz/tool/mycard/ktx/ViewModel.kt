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
    ViewModelProvider(this, object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val constructor = modelClass.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        }
    })[VM::class.java]
}

