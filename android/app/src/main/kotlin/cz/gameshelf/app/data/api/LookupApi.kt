package cz.gameshelf.app.data.api

import cz.gameshelf.app.domain.model.BarcodeLookup
import retrofit2.http.GET
import retrofit2.http.Path

/** Game databases behind the API. */
interface LookupApi {
    /** `404 BARCODE_NOT_FOUND` when the code is unknown, `503 LOOKUP_UNAVAILABLE` when the database is down. */
    @GET("lookup/barcode/{barcode}")
    suspend fun barcode(@Path("barcode") barcode: String): BarcodeLookup
}
