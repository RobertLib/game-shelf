package cz.gameshelf.app.domain.model

/** Failure of a remote operation, independent of the transport details. */
sealed interface AppError {
    /**
     * The API answered with an `ErrorResponse` (an API error body: JSON with a string `code`); branch on
     * [code], never on the message. A code this app version doesn't know is [ErrorCode.UNKNOWN].
     */
    data class Api(
        val statusCode: Int,
        val code: ErrorCode,
        val details: List<String> = emptyList(),
    ) : AppError

    /**
     * An HTTP error without an API error body – an HTML page from a proxy, a `403` from a firewall, a `404`
     * from a misrouted request. It did not come from the Game Shelf API, so it says nothing about the request.
     */
    data class Http(val statusCode: Int) : AppError

    /** No connection, DNS failure, timeout… */
    data object Network : AppError

    /** Anything else (unparseable response, unexpected exception). */
    data object Unexpected : AppError
}

sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(value)
    return this
}

inline fun <T> ApiResult<T>.onFailure(action: (AppError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(error)
    return this
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
