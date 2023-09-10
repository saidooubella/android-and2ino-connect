package saidooubella.and.ino.conn

import android.content.Intent
import android.os.Build
import android.os.Parcelable

@Suppress("DEPRECATION")
inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
        else -> getParcelableExtra(key)
    }
}

inline fun <T, R> T.tryOrNull(block: T.() -> R): R? {
    return try {
        block()
    } catch (_: Exception) {
        null
    }
}
