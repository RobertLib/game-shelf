package cz.gameshelf.app.ui.common

import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.ErrorCode

private const val HTTP_TOO_MANY_REQUESTS = 429

/** User-facing message for a failed request; branches on the error `code`, never on its message. */
fun AppError.toUiText(): UiText = when (this) {
    AppError.Network -> UiText(R.string.error_network)
    AppError.Unexpected -> UiText(R.string.error_unknown)
    is AppError.Http ->
        if (statusCode == HTTP_TOO_MANY_REQUESTS) UiText(R.string.error_too_many_requests) else UiText(R.string.error_unknown)
    is AppError.Api -> when {
        code == ErrorCode.INVALID_CREDENTIALS -> UiText(R.string.error_invalid_credentials)
        code == ErrorCode.EMAIL_ALREADY_REGISTERED -> UiText(R.string.error_email_already_registered)
        code == ErrorCode.INVALID_CURRENT_PASSWORD -> UiText(R.string.error_invalid_current_password)
        code == ErrorCode.VALIDATION_FAILED && details.isNotEmpty() ->
            UiText(R.string.error_validation_failed_details, details.joinToString(separator = "\n"))
        code == ErrorCode.VALIDATION_FAILED -> UiText(R.string.error_validation_failed)
        code == ErrorCode.TOO_MANY_REQUESTS || statusCode == HTTP_TOO_MANY_REQUESTS ->
            UiText(R.string.error_too_many_requests)
        code == ErrorCode.GAME_NOT_FOUND -> UiText(R.string.error_game_not_found)
        code == ErrorCode.LOOKUP_UNAVAILABLE -> UiText(R.string.error_lookup_unavailable)
        else -> UiText(R.string.error_unknown)
    }
}

/** The delete-account dialog asks for the password only, so a wrong one is reported plainly. */
fun AppError.toDeleteAccountUiText(): UiText =
    if (this is AppError.Api && code == ErrorCode.INVALID_CURRENT_PASSWORD) {
        UiText(R.string.error_incorrect_password)
    } else {
        toUiText()
    }
