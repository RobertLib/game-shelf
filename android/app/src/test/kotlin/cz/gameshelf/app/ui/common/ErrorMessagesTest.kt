package cz.gameshelf.app.ui.common

import cz.gameshelf.app.R
import cz.gameshelf.app.data.api.toAppError
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.ErrorCode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorMessagesTest {

    private fun api(code: ErrorCode, status: Int = 400, details: List<String> = emptyList()) =
        AppError.Api(status, code, details)

    @Test
    fun `maps documented error codes to their messages`() {
        val expected = mapOf(
            api(ErrorCode.INVALID_CREDENTIALS, 401) to R.string.error_invalid_credentials,
            api(ErrorCode.EMAIL_ALREADY_REGISTERED, 409) to R.string.error_email_already_registered,
            api(ErrorCode.INVALID_CURRENT_PASSWORD) to R.string.error_invalid_current_password,
            api(ErrorCode.VALIDATION_FAILED) to R.string.error_validation_failed,
            api(ErrorCode.TOO_MANY_REQUESTS, 429) to R.string.error_too_many_requests,
            api(ErrorCode.GAME_NOT_FOUND, 404) to R.string.error_game_not_found,
            AppError.Network to R.string.error_network,
        )
        expected.forEach { (error, messageRes) -> assertEquals(error.toString(), UiText(messageRes), error.toUiText()) }
    }

    @Test
    fun `anything else is a generic message`() {
        listOf(
            api(ErrorCode.INTERNAL_ERROR, 500),
            api(ErrorCode.FORBIDDEN, 403),
            api(ErrorCode.UNKNOWN, 418),
            AppError.Unexpected,
        ).forEach { assertEquals(UiText(R.string.error_unknown), it.toUiText()) }
    }

    @Test
    fun `validation details are appended`() {
        val details = listOf("title should not be empty", "quantity must not be less than 1")
        val error = api(ErrorCode.VALIDATION_FAILED, details = details)

        assertEquals(
            UiText(R.string.error_validation_failed_details, "title should not be empty\nquantity must not be less than 1"),
            error.toUiText(),
        )
    }

    @Test
    fun `rate limiting is recognised by status when the code is missing`() {
        assertEquals(UiText(R.string.error_too_many_requests), api(ErrorCode.UNKNOWN, 429).toUiText())
    }

    @Test
    fun `parses the error body of an HTTP failure`() {
        val body = """{"statusCode":400,"code":"INVALID_CURRENT_PASSWORD","message":"Current password is incorrect"}"""

        val error = httpError(400, body).toAppError()

        assertEquals(AppError.Api(400, ErrorCode.INVALID_CURRENT_PASSWORD), error)
    }

    @Test
    fun `an unknown code is still an API error`() {
        val body = """{"statusCode":402,"code":"PAYMENT_REQUIRED","message":"Pay","details":["plan"]}"""

        assertEquals(AppError.Api(402, ErrorCode.UNKNOWN, listOf("plan")), httpError(402, body).toAppError())
        assertEquals(AppError.Api(422, ErrorCode.UNKNOWN), httpError(422, """{"code":"SOMETHING_NEW"}""").toAppError())
    }

    @Test
    fun `a body without a string code did not come from the API`() {
        listOf(
            "<html>Bad gateway</html>",
            "",
            "Forbidden",
            """{"statusCode":403,"message":"Forbidden"}""",
            """{"statusCode":404,"code":404,"message":"Not found"}""",
            """{"statusCode":404,"code":null,"message":"Not found"}""",
            """["GAME_NOT_FOUND"]""",
        ).forEach { body -> assertEquals(body, AppError.Http(404), httpError(404, body).toAppError()) }
    }

    @Test
    fun `errors without an API body are generic, except rate limiting`() {
        assertEquals(UiText(R.string.error_unknown), AppError.Http(502).toUiText())
        assertEquals(UiText(R.string.error_too_many_requests), AppError.Http(429).toUiText())
    }

    @Test
    fun `wrong password in the delete-account dialog is reported plainly`() {
        assertEquals(
            UiText(R.string.error_incorrect_password),
            api(ErrorCode.INVALID_CURRENT_PASSWORD).toDeleteAccountUiText(),
        )
        assertEquals(UiText(R.string.error_network), AppError.Network.toDeleteAccountUiText())
    }

    private fun httpError(status: Int, body: String) =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))
}
