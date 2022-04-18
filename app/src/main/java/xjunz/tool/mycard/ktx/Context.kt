package xjunz.tool.mycard.ktx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import androidx.annotation.AnyRes
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import xjunz.tool.mycard.R

/**
 * Resolve the resource id of an attr resource.
 */
@AnyRes
fun Context.resolveAttribute(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.resourceId
}

/**
 * Launch an [Activity] auto using transitions or auto adding the [Intent.FLAG_ACTIVITY_NEW_TASK] flag.
 */
inline fun <T : Activity> Context.launchActivity(clazz: Class<T>, block: Intent.() -> Unit = {}) {
    if (getActivity() == null) {
        startActivity(Intent(this, clazz).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also(block))
    } else {
        val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(this as Activity).toBundle()
        startActivity(Intent(this, clazz).also(block), bundle)
    }
}

fun <T : Activity> Activity.launchSharedElementActivity(clazz: Class<T>, sharedElement: View) {
    val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
        this, Pair.create(sharedElement, sharedElement.transitionName)
    ).toBundle()
    startActivity(Intent(this, clazz), bundle)
}

fun Context.viewUrlSafely(url: String, modifier: (Intent.() -> Unit)? = null) {
    launchIntentSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { modifier?.invoke(this) })
}

fun Context.viewUriSafely(uri: Uri, modifier: (Intent.() -> Unit)? = null) {
    launchIntentSafely(Intent(Intent.ACTION_VIEW, uri).apply { modifier?.invoke(this) })
}

fun Context.launchIntentSafely(
    intent: Intent,
    promptWhenFailed: String = R.string.no_way_to_open.resStr
) {
    runCatching {
        if (getActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }.onFailure {
        it.printStackTrace()
        toast(promptWhenFailed)
    }
}

fun Context.getDrawableCompat(@DrawableRes res: Int) = ContextCompat.getDrawable(this, res)

fun Context.requireActivity(): AppCompatActivity {
    return checkNotNull(getActivity()) { "Not a context wrapper of AppCompatActivity" }
}

inline fun Context.broadcast(action: String, block: Intent.() -> Unit = {}) {
    sendBroadcast(Intent(action).also(block))
}

/**
 * Returns the [Activity] given a [Context] or throw a exception if there is no [Activity],
 * taking into account the potential hierarchy of [ContextWrappers][ContextWrapper].
 */
fun Context.getActivity(): AppCompatActivity? {
    var context = this
    if (this is AppCompatActivity) return this
    while (context is ContextWrapper) {
        if (context is AppCompatActivity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

inline fun Context.showSimplePromptDialog(
    title: Int = R.string.prompt,
    msg: Int,
    showCancellationBtn: Boolean = true,
    crossinline positiveAction: () -> Unit = {}
): AlertDialog {
    return showSimplePromptDialog(title.resText, msg.resText, showCancellationBtn, positiveAction)
}

inline fun Context.showSimplePromptDialog(
    title: CharSequence = R.string.prompt.resText,
    msg: CharSequence,
    showCancellationBtn: Boolean = true,
    crossinline positiveAction: () -> Unit = {}
): AlertDialog {
    val builder = MaterialAlertDialogBuilder(this).setTitle(title).setMessage(msg)
    if (showCancellationBtn) {
        builder.setNegativeButton(android.R.string.cancel, null)
    }
    builder.setPositiveButton(android.R.string.ok) { _, _ -> positiveAction.invoke() }
    return builder.show()
}