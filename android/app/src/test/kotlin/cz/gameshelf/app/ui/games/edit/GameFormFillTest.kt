package cz.gameshelf.app.ui.games.edit

import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.testing.MARIO_KART_LOOKUP
import cz.gameshelf.app.testing.MARIO_KART_SEARCH_RESULT
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

    @Test
    fun `a game picked in the search replaces the fields it knows`() {
        val entered = GameForm(
            title = "mario kart",
            edition = "Deluxe bundle",
            genre = "Kart racing",
            developer = "Nintendo",
            publisher = "Nintendo of Europe",
            releaseYear = "2014",
            coverImageUrl = "https://example.com/old.jpg",
            status = CollectionStatus.WISHLIST,
            region = Region.PAL,
            barcode = "045496420055",
            notes = "Birthday present",
        )

        val form = entered.fillFrom(MARIO_KART_SEARCH_RESULT, Platform.SWITCH)

        assertEquals(
            entered.copy(
                title = "Mario Kart 8 Deluxe",
                platform = Platform.SWITCH,
                genre = "Racing",
                developer = "Nintendo EPD",
                publisher = "Nintendo",
                releaseYear = "2017",
                coverImageUrl = MARIO_KART_SEARCH_RESULT.coverImageUrl!!,
            ),
            form,
        )
    }

    @Test
    fun `values the database doesn't know leave their fields as they are`() {
        val entered = GameForm(
            title = "MK8",
            platform = Platform.WII_U,
            genre = "Kart racing",
            developer = "Nintendo",
            publisher = "Nintendo of Europe",
            releaseYear = "2014",
            coverImageUrl = "https://example.com/old.jpg",
        )
        val unknown = MARIO_KART_SEARCH_RESULT.copy(
            genre = null,
            developer = " ",
            publisher = null,
            releaseYear = null,
            coverImageUrl = null,
        )

        val form = entered.fillFrom(unknown, platform = null)

        assertEquals(entered.copy(title = "Mario Kart 8 Deluxe"), form)
    }
}
