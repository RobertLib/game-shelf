package cz.gameshelf.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCoverTest {

    @Test
    fun `covers load over HTTPS, also when their address starts with http`() {
        assertEquals("https://example.com/doom.jpg", coverLoadUrl("http://example.com/doom.jpg"))
        assertEquals("https://example.com:8080/doom.jpg?size=big", coverLoadUrl("HTTP://example.com:8080/doom.jpg?size=big"))
        assertEquals("https://example.com/doom.jpg", coverLoadUrl("https://example.com/doom.jpg"))
        assertEquals("HTTPS://example.com/doom.jpg", coverLoadUrl("HTTPS://example.com/doom.jpg"))
    }
}
