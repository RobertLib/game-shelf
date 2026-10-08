package cz.gameshelf.app.testing

import cz.gameshelf.app.data.lookup.GameSearchOutcome
import cz.gameshelf.app.data.lookup.GameSearchRepository
import cz.gameshelf.app.domain.model.GameSearchResponse
import cz.gameshelf.app.domain.model.GameSearchResult
import cz.gameshelf.app.domain.model.Platform
import kotlinx.coroutines.CompletableDeferred

val MARIO_KART_SEARCH_RESULT = GameSearchResult(
    igdbId = 26758,
    title = "Mario Kart 8 Deluxe",
    platforms = listOf(Platform.SWITCH),
    genre = "Racing",
    developer = "Nintendo EPD",
    publisher = "Nintendo",
    releaseYear = 2017,
    coverImageUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg",
)

val ZELDA_SEARCH_RESULT = GameSearchResult(
    igdbId = 1029,
    title = "The Legend of Zelda: Ocarina of Time",
    platforms = listOf(Platform.N64, Platform.GAMECUBE, Platform.NINTENDO_3DS),
    genre = "Adventure",
    developer = "Nintendo EAD",
    publisher = "Nintendo",
    releaseYear = 1998,
    coverImageUrl = null,
)

/** A successful search answered by IGDB. */
fun searchFound(vararg games: GameSearchResult) =
    GameSearchOutcome.Found(GameSearchResponse(items = games.toList(), sources = listOf("IGDB")))

/** Answers from [results] by query; other queries find nothing. */
class FakeGameSearchRepository(
    var results: Map<String, GameSearchOutcome> = emptyMap(),
) : GameSearchRepository {

    data class Search(val query: String, val platform: Platform?)

    val searches = mutableListOf<Search>()

    /** A search for a query with a gate waits until the gate is completed. */
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    override suspend fun search(query: String, platform: Platform?): GameSearchOutcome {
        searches += Search(query, platform)
        gates[query]?.await()
        return results[query] ?: searchFound()
    }
}
