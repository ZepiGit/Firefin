package zepigit.firefin.app.data

import androidx.annotation.StringRes
import java.io.IOException

/**
 * An [IOException] whose user-facing text lives in string resources so the UI can
 * show it in the device language. [message] is an English diagnostic for logs and tests.
 */
open class LocalizedIOException(
    @StringRes val messageRes: Int,
    message: String,
    val formatArgs: List<Any> = emptyList(),
) : IOException(message)
