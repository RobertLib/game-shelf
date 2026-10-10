package cz.gameshelf.app.data.api

import android.util.Log
import cz.gameshelf.app.data.api.dto.ErrorResponse
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.ErrorCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "ApiCall"

/** Runs a Retrofit call and maps every failure to an [AppError]. Cancellation is propagated. */
suspend fun <T> apiCall(json: Json = ApiJson, block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        ApiResult.Failure(e.toAppError(json))
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network)
    } catch (e: SerializationException) {
        Log.w(TAG, "Unexpected response body", e)
        ApiResult.Failure(AppError.Unexpected)
    } catch (e: RuntimeException) {
        Log.e(TAG, "API call failed", e)
        ApiResult.Failure(AppError.Unexpected)
    }

/**
 * [AppError.Api] for a response with an API error body – JSON with a string `code`, which may be one this app
 * version doesn't know ([ErrorCode.UNKNOWN]); [AppError.Http] for any other body (or none).
 */
fun HttpException.toAppError(json: Json = ApiJson): AppError {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val error = body?.let { runCatching { json.decodeFromString<ErrorResponse>(it) }.getOrNull() }
        ?: return AppError.Http(code())
    return AppError.Api(statusCode = code(), code = error.code, details = error.details.orEmpty())
}
