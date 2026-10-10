package cz.gameshelf.app.ui.auth

import cz.gameshelf.app.R
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.codePointLength

/** Client-side checks with exactly the API rules for credentials; lengths count Unicode code points. */
object AuthValidation {
    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_LENGTH = 128
    const val DISPLAY_NAME_MAX_LENGTH = 100

    private val EmailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun email(value: String): UiText? = when {
        value.isBlank() -> UiText(R.string.validation_required)
        !EmailPattern.matches(value.trim()) -> UiText(R.string.validation_email)
        else -> null
    }

    fun requiredPassword(value: String): UiText? =
        if (value.isEmpty()) UiText(R.string.validation_required) else null

    fun newPassword(value: String): UiText? =
        if (value.codePointLength() !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
            UiText(R.string.validation_password_length)
        } else {
            null
        }

    fun passwordConfirmation(password: String, confirmation: String): UiText? = when {
        confirmation.isEmpty() -> UiText(R.string.validation_required)
        password != confirmation -> UiText(R.string.validation_password_mismatch)
        else -> null
    }

    fun displayName(value: String): UiText? =
        if (value.trim().codePointLength() > DISPLAY_NAME_MAX_LENGTH) {
            UiText.plural(R.plurals.validation_too_long, DISPLAY_NAME_MAX_LENGTH)
        } else {
            null
        }
}
