package cz.gameshelf.app.data.lookup

import cz.gameshelf.app.data.api.LookupApi
import cz.gameshelf.app.data.api.apiCall
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.BarcodeLookup
import cz.gameshelf.app.domain.model.ErrorCode

sealed interface BarcodeLookupResult {
    data class Found(val game: BarcodeLookup) : BarcodeLookupResult

    /** No database knows the code. */
    data object NotFound : BarcodeLookupResult

    data class Failed(val error: AppError) : BarcodeLookupResult
}

/** Finds games by the barcode on their box. Unlike the collection, it needs a connection. */
fun interface BarcodeLookupRepository {
    suspend fun lookup(barcode: String): BarcodeLookupResult
}

class RemoteBarcodeLookupRepository(private val api: LookupApi) : BarcodeLookupRepository {
    override suspend fun lookup(barcode: String): BarcodeLookupResult =
        when (val result = apiCall { api.barcode(barcode) }) {
            is ApiResult.Success -> BarcodeLookupResult.Found(result.value)
            is ApiResult.Failure ->
                if ((result.error as? AppError.Api)?.code == ErrorCode.BARCODE_NOT_FOUND) {
                    BarcodeLookupResult.NotFound
                } else {
                    BarcodeLookupResult.Failed(result.error)
                }
        }
}
