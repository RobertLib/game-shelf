package cz.gameshelf.app.testing

import cz.gameshelf.app.data.lookup.BarcodeLookupRepository
import cz.gameshelf.app.data.lookup.BarcodeLookupResult
import cz.gameshelf.app.domain.model.BarcodeLookup
import cz.gameshelf.app.domain.model.Platform

val MARIO_KART_LOOKUP = BarcodeLookup(
    barcode = "045496420055",
    title = "Mario Kart 8 Deluxe",
    platform = Platform.SWITCH,
    region = null,
    edition = null,
    genre = "Racing",
    developer = "Nintendo EPD",
    publisher = "Nintendo",
    releaseYear = 2017,
    coverImageUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg",
    sources = listOf("UPCitemdb", "IGDB"),
)

/** Answers from [results] by barcode; other codes are not found. */
class FakeBarcodeLookupRepository(
    var results: Map<String, BarcodeLookupResult> = emptyMap(),
) : BarcodeLookupRepository {

    val lookedUp = mutableListOf<String>()

    override suspend fun lookup(barcode: String): BarcodeLookupResult {
        lookedUp += barcode
        return results[barcode] ?: BarcodeLookupResult.NotFound
    }
}
