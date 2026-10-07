package cz.gameshelf.app.ui.common

import android.app.Application
import cz.gameshelf.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** SDK 34 is the newest Robolectric sandbox that runs on JDK 17; plural rules do not differ by API level. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS", application = Application::class)
class PluralsTest {

    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `games count uses singular only for one`() {
        fun games(count: Int) = resources.getQuantityString(R.plurals.games_count, count, count)

        assertEquals("0 games", games(0))
        assertEquals("1 game", games(1))
        assertEquals("2 games", games(2))
        assertEquals("21 games", games(21))
        assertEquals("132 games", games(132))
    }

    @Test
    fun `active filter count`() {
        fun filters(count: Int) = resources.getQuantityString(R.plurals.active_filters, count, count)

        assertEquals("1 active filter", filters(1))
        assertEquals("3 active filters", filters(3))
    }

    @Test
    fun `plural text resolves through UiText`() {
        assertEquals("Maximum 1 character.", UiText.plural(R.plurals.validation_too_long, 1).resolve(resources))
        assertEquals("Maximum 200 characters.", UiText.plural(R.plurals.validation_too_long, 200).resolve(resources))
    }
}
