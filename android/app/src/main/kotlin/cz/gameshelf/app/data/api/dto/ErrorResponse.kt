package cz.gameshelf.app.data.api.dto

import cz.gameshelf.app.domain.model.ErrorCode
import kotlinx.serialization.Serializable

/** Shape of every error body returned by the API. */
@Serializable
data class ErrorResponse(
    val statusCode: Int,
    val code: ErrorCode,
    val message: String,
    val details: List<String>? = null,
)
