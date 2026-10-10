package cz.gameshelf.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLengthTest {

    @Test
    fun `normalizing removes U+0000 anywhere, then white space and U+FEFF around the text`() {
        assertEquals("Doom", " \uFEFF\tDo\u0000om\u00A0\n\u2003".normalizedText())
        assertEquals("Doom", "\u0000 Doom \u0000".normalizedText())
        // Inside the text, white space and U+FEFF stay.
        assertEquals("Doom \uFEFF II", "\uFEFFDoom \uFEFF II\uFEFF".normalizedText())
        assertEquals("", "\u0000 \uFEFF\r\n".normalizedText())
    }

    @Test
    fun `shortening never cuts a surrogate pair in half`() {
        assertEquals("ab", "abc".takeCodePoints(2))
        assertEquals("😀😀", "😀😀😀".takeCodePoints(2))
        assertEquals("a😀", "a😀".takeCodePoints(5))
        assertEquals("", "😀".takeCodePoints(0))
        // A flag is two code points.
        assertEquals("🇨", "🇨🇿".takeCodePoints(1))
    }
}
