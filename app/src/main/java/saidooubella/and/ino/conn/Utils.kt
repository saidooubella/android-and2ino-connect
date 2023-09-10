package saidooubella.and.ino.conn

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

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

operator fun PaddingValues.plus(that: PaddingValues): PaddingValues {
    return PaddingValuesCombiner(this, that)
}

private class PaddingValuesCombiner(
    private val left: PaddingValues,
    private val right: PaddingValues,
) : PaddingValues {

    override fun calculateTopPadding(): Dp {
        return left.calculateTopPadding() + right.calculateTopPadding()
    }

    override fun calculateBottomPadding(): Dp {
        return left.calculateBottomPadding() + right.calculateBottomPadding()
    }

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
        return left.calculateLeftPadding(layoutDirection) +
                right.calculateLeftPadding(layoutDirection)
    }

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
        return left.calculateRightPadding(layoutDirection) +
                right.calculateRightPadding(layoutDirection)
    }
}
