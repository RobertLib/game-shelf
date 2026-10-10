package cz.gameshelf.app.ui.common

import cz.gameshelf.app.R
import java.time.Duration
import java.time.Instant

/**
 * "Just now", "5 minutes ago", "2 hours ago", "3 days ago" from the app's own strings, never the device
 * language's (mobile-spec.md, "Profile & settings"); `null` from a week on, when a date says more.
 */
fun relativeTime(time: Instant, now: Instant): UiText? {
    val elapsed = Duration.between(time, now)
    return when {
        // Also a time slightly ahead of this device's clock.
        elapsed < Duration.ofMinutes(1) -> UiText(R.string.sync_just_now)
        elapsed < Duration.ofHours(1) -> UiText.plural(R.plurals.time_minutes_ago, elapsed.toMinutes().toInt())
        elapsed < Duration.ofDays(1) -> UiText.plural(R.plurals.time_hours_ago, elapsed.toHours().toInt())
        elapsed < Duration.ofDays(7) -> UiText.plural(R.plurals.time_days_ago, elapsed.toDays().toInt())
        else -> null
    }
}
