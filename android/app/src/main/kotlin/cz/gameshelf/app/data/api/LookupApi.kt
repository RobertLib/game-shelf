package cz.gameshelf.app.data.api

import cz.gameshelf.app.domain.model.BarcodeLookup
import cz.gameshelf.app.domain.model.GameSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Game databases behind the API. */
interface LookupApi {
    /** `404 BARCODE_NOT_FOUND` when the code is unknown, `503 LOOKUP_UNAVAILABLE` when the database is down. */
    @GET("lookup/barcode/{barcode}")
    suspend fun barcode(@Path("barcode") barcode: String): BarcodeLookup

    /**
     * Games whose title matches [q] (2–100 characters); games on [platform] (a `Platform` API value) are
     * listed first. `503 LOOKUP_UNAVAILABLE` when the database is down.
     */
    @GET("lookup/games")
    suspend fun searchGames(@Query("q") q: String, @Query("platform") platform: String?): GameSearchResponse
}
