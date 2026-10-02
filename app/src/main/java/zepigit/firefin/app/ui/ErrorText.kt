package zepigit.firefin.app.ui

import android.content.Context
import androidx.annotation.StringRes
import zepigit.firefin.app.R
import zepigit.firefin.app.data.LocalizedIOException

/** User-facing text for [error]: localized when it carries a resource, otherwise its message. */
fun Context.errorMessage(error: Throwable, @StringRes fallback: Int = R.string.error_generic): String =
    if (error is LocalizedIOException) getString(error.messageRes, *error.formatArgs.toTypedArray())
    else error.message ?: getString(fallback)
