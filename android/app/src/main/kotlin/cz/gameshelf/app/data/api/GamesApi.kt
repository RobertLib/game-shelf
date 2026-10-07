package cz.gameshelf.app.data.api

import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.GamePage
import cz.gameshelf.app.domain.model.SaveGameRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GamesApi {
    /** Multi-value filters are sent as repeated parameters (`platform=PS2&platform=PS5`). */
    @GET("games")
    suspend fun listGames(
        @Query("q") q: String?,
        @Query("platform") platform: List<String>,
        @Query("status") status: List<String>,
        @Query("format") format: List<String>,
        @Query("region") region: List<String>,
        @Query("completeness") completeness: List<String>,
        @Query("condition") condition: List<String>,
        @Query("playStatus") playStatus: List<String>,
        @Query("genre") genre: List<String>,
        @Query("publisher") publisher: String?,
        @Query("developer") developer: String?,
        @Query("storageLocation") storageLocation: String?,
        @Query("favorite") favorite: Boolean?,
        @Query("hasCover") hasCover: Boolean?,
        @Query("releaseYearFrom") releaseYearFrom: Int?,
        @Query("releaseYearTo") releaseYearTo: Int?,
        @Query("purchaseDateFrom") purchaseDateFrom: String?,
        @Query("purchaseDateTo") purchaseDateTo: String?,
        @Query("purchasePriceMin") purchasePriceMin: String?,
        @Query("purchasePriceMax") purchasePriceMax: String?,
        @Query("estimatedValueMin") estimatedValueMin: String?,
        @Query("estimatedValueMax") estimatedValueMax: String?,
        @Query("ratingMin") ratingMin: Int?,
        @Query("ratingMax") ratingMax: Int?,
        @Query("sort") sort: String?,
        @Query("order") order: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): GamePage

    @GET("games/facets")
    suspend fun facets(): GameFacets

    @GET("games/{id}")
    suspend fun game(@Path("id") id: String): Game

    @POST("games")
    suspend fun createGame(@Body body: SaveGameRequest): Game

    @PUT("games/{id}")
    suspend fun updateGame(@Path("id") id: String, @Body body: SaveGameRequest): Game

    @DELETE("games/{id}")
    suspend fun deleteGame(@Path("id") id: String)
}
