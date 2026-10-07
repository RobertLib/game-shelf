package cz.gameshelf.app.data.api

import cz.gameshelf.app.data.api.dto.GameChanges
import cz.gameshelf.app.domain.model.Game
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Game endpoints used by the sync engine (offline-sync.md, "API"). */
interface GamesApi {
    /** Change feed; a `null` [cursor] starts from the beginning. */
    @GET("games/changes")
    suspend fun changes(@Query("cursor") cursor: String?, @Query("limit") limit: Int): GameChanges

    @GET("games/{id}")
    suspend fun game(@Path("id") id: String): Game

    /**
     * [body] is a `CreateGameRequest` (every `SaveGameRequest` field plus the client-generated `id`).
     * `201` = created, `200` = a game with this id already existed and nothing was changed.
     */
    @POST("games")
    suspend fun createGame(@Body body: JsonObject): Response<Game>

    /** [body] is an `UpdateGameRequest`: only the fields present change, `null` clears an optional one. */
    @PATCH("games/{id}")
    suspend fun updateGame(@Path("id") id: String, @Body body: JsonObject): Game

    @DELETE("games/{id}")
    suspend fun deleteGame(@Path("id") id: String)
}
