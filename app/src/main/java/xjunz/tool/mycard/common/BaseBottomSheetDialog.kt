package xjunz.tool.mycard.common

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.doOnPreDraw
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import xjunz.tool.mycard.R
import java.lang.reflect.ParameterizedType


abstract class BaseBottomSheetDialog<T : ViewBinding> : BottomSheetDialogFragment() {

    @Suppress("UNCHECKED_CAST")
    protected val binding by lazy {
        ((javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<*>)
            .getDeclaredMethod("inflate", LayoutInflater::class.java)
            .invoke(null, layoutInflater) as T
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogStyle)
    }

    private lateinit var behavior: BottomSheetBehavior<FrameLayout>

    @SuppressLint("RestrictedApi", "VisibleForTests")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.setContentView(binding.root)
            behavior = (it as BottomSheetDialog).behavior
            //behavior.skipCollapsed = true
            binding.root.doOnPreDraw { v ->
                if ((v.parent as View).top > 0) {
                    behavior.disableShapeAnimations()
                }
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            onDialogCreated(it)
            onDialogCreated(it, savedInstanceState)
        }
    }

    open fun onDialogCreated(dialog: Dialog, savedInstanceState: Bundle?) {

    }

    abstract fun onDialogCreated(dialog: Dialog)
}