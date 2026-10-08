package cz.gameshelf.app.ui.games.edit.search

import cz.gameshelf.app.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformChoiceTest {

    @Test
    fun `a platform chosen in the form stays`() {
        assertEquals(
            PlatformChoice.Decided(Platform.WII_U),
            PlatformChoice.of(Platform.WII_U, listOf(Platform.WII_U, Platform.SWITCH)),
        )
    }

    @Test
    fun `a platform chosen in the form stays even when the game didn't come out on it`() {
        assertEquals(PlatformChoice.Decided(Platform.PS2), PlatformChoice.of(Platform.PS2, listOf(Platform.SWITCH)))
        assertEquals(PlatformChoice.Decided(Platform.PS2), PlatformChoice.of(Platform.PS2, emptyList()))
    }

    @Test
    fun `the only platform of the game is taken`() {
        assertEquals(PlatformChoice.Decided(Platform.SWITCH), PlatformChoice.of(null, listOf(Platform.SWITCH)))
    }

    @Test
    fun `several platforms are offered in the listed order`() {
        assertEquals(
            PlatformChoice.Ask(listOf(Platform.WII_U, Platform.SWITCH)),
            PlatformChoice.of(null, listOf(Platform.WII_U, Platform.SWITCH)),
        )
    }

    @Test
    fun `a game without platforms leaves the platform empty`() {
        assertEquals(PlatformChoice.Decided(null), PlatformChoice.of(null, emptyList()))
    }
}
