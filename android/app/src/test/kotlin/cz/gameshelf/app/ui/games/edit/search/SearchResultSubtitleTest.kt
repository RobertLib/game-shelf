package cz.gameshelf.app.ui.games.edit.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultSubtitleTest {

    private fun subtitle(releaseYear: Int?, vararg platforms: String) =
        searchResultSubtitle(releaseYear, platforms.toList()) { "+$it" }

    @Test
    fun `shows the release year and the platforms`() {
        assertEquals("2017 · Nintendo Switch, Wii U", subtitle(2017, "Nintendo Switch", "Wii U"))
    }

    @Test
    fun `shows only what is known`() {
        assertEquals("2017", subtitle(2017))
        assertEquals("PC", subtitle(null, "PC"))
        assertEquals("", subtitle(null))
    }

    @Test
    fun `lists up to three platforms`() {
        assertEquals("PC, Mac, Amiga", subtitle(null, "PC", "Mac", "Amiga"))
    }

    @Test
    fun `shortens more than three platforms`() {
        assertEquals(
            "2014 · PC, PlayStation 4, PlayStation 5 +2",
            subtitle(2014, "PC", "PlayStation 4", "PlayStation 5", "Xbox One", "Xbox Series X|S"),
        )
    }
}
