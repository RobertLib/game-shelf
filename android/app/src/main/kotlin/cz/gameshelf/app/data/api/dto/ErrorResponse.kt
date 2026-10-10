package cz.gameshelf.app.data.api.dto

import cz.gameshelf.app.domain.model.ErrorCode
import kotlinx.serialization.Serializable

/**
 * Shape of every error body returned by the API. What makes a body an API error body is the string [code]
 * (any code, also one this app version doesn't know); the rest is not relied on.
 */
@Serializable
data class ErrorResponse(
    val statusCode: Int? = null,
    val code: ErrorCode,
    val message: String? = null,
    val details: List<String>? = null,
)
