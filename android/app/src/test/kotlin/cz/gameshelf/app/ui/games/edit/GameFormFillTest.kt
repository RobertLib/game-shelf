package cz.gameshelf.app.ui.games.edit

import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.testing.MARIO_KART_LOOKUP
import org.junit.Assert.assertEquals
import org.junit.Test

class GameFormFillTest {

    @Test
    fun `fills every empty field the lookup knows`() {
        val form = GameForm().fillFrom(MARIO_KART_LOOKUP.copy(region = Region.PAL, edition = "Limited Edition"))

        assertEquals(
            GameForm(
                title = "Mario Kart 8 Deluxe",
                platform = Platform.SWITCH,
                edition = "Limited Edition",
                genre = "Racing",
                developer = "Nintendo EPD",
                publisher = "Nintendo",
                releaseYear = "2017",
                coverImageUrl = MARIO_KART_LOOKUP.coverImageUrl!!,
                region = Region.PAL,
                barcode = "045496420055",
            ),
            form,
        )
    }

    @Test
    fun `keeps what the user entered`() {
        val entered = GameForm(
            title = "MK8 Deluxe",
            platform = Platform.SWITCH_2,
            genre = "Kart racing",
            region = Region.NTSC_J,
            barcode = "0045496420055",
        )

        val form = entered.fillFrom(MARIO_KART_LOOKUP.copy(region = Region.PAL))

        assertEquals("MK8 Deluxe", form.title)
        assertEquals(Platform.SWITCH_2, form.platform)
        assertEquals("Kart racing", form.genre)
        assertEquals(Region.NTSC_J, form.region)
        assertEquals("0045496420055", form.barcode)
        assertEquals("Nintendo EPD", form.developer)
    }
}
