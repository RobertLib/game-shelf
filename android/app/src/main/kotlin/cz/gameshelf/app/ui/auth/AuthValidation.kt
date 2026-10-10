package cz.gameshelf.app.ui.auth

import cz.gameshelf.app.R
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.codePointLength
import cz.gameshelf.app.ui.common.normalizedText

/** Client-side checks with exactly the API rules for credentials; lengths count Unicode code points. */
object AuthValidation {
    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_LENGTH = 128
    const val DISPLAY_NAME_MAX_LENGTH = 100
    const val EMAIL_MAX_LENGTH = 254

    private const val EMAIL_LOCAL_PART_MAX_BYTES = 64
    private const val EMAIL_DOMAIN_MAX_BYTES = 254

    /**
     * The email pattern of mobile-spec.md, identical on all platforms: everything it accepts, the API's `IsEmail`
     * (validator.js) accepts too. `\p{L}` / `\p{N}` are Unicode letters and digits; it holds no `\s`, whose meaning
     * differs between regex engines (on Android it is ASCII white space only).
     */
    private val EmailPattern = Regex(
        """^(?!.*[\uFF01-\uFF5E])[A-Za-z0-9!#$%&'*+/=?^_`{|}~\u00A1-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF-]+""" +
            """(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~\u00A1-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF-]+)*""" +
            """@(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+""" +
            """(?:\p{L}{2,63}|[Xx][Nn]--[A-Za-z0-9-]{0,58}[A-Za-z0-9])$""",
    )

    /**
     * Creating an account: at most 254 code points, none outside the Basic Multilingual Plane (e.g. emoji), at most 64
     * bytes before the `@` and 254 after it in UTF-8, and it matches [EmailPattern] (mobile-spec.md, "Validation").
     */
    fun newAccountEmail(value: String): UiText? {
        val email = value.normalizedText()
        requiredEmail(email)?.let { return it }
        // A code point above U+FFFF is a surrogate pair.
        val valid = email.none(Char::isSurrogate) &&
            email.substringBeforeLast('@').utf8Length() <= EMAIL_LOCAL_PART_MAX_BYTES &&
            email.substringAfterLast('@', missingDelimiterValue = "").utf8Length() <= EMAIL_DOMAIN_MAX_BYTES &&
            EmailPattern.matches(email)
        return if (valid) null else UiText(R.string.validation_email)
    }

    /**
     * Signing in: required, at most 254 code points, contains `@` – no other format check, so that every existing
     * account can sign in; the API answers `INVALID_CREDENTIALS` for an address it doesn't know.
     */
    fun signInEmail(value: String): UiText? {
        val email = value.normalizedText()
        return requiredEmail(email) ?: UiText(R.string.validation_email).takeIf { '@' !in email }
    }

    private fun requiredEmail(email: String): UiText? = when {
        email.isEmpty() -> UiText(R.string.validation_required)
        email.codePointLength() > EMAIL_MAX_LENGTH -> UiText.plural(R.plurals.validation_too_long, EMAIL_MAX_LENGTH)
        else -> null
    }

    private fun String.utf8Length(): Int = encodeToByteArray().size

    fun requiredPassword(value: String): UiText? =
        if (value.isEmpty()) UiText(R.string.validation_required) else null

    fun newPassword(value: String): UiText? =
        if (value.codePointLength() !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
            UiText(R.string.validation_password_length)
        } else {
            null
        }

    /** Changing the password: the API rejects a new password equal to the current one. */
    fun changedPassword(currentPassword: String, newPassword: String): UiText? =
        newPassword(newPassword)
            ?: UiText(R.string.validation_password_unchanged).takeIf { newPassword == currentPassword }

    fun passwordConfirmation(password: String, confirmation: String): UiText? = when {
        confirmation.isEmpty() -> UiText(R.string.validation_required)
        password != confirmation -> UiText(R.string.validation_password_mismatch)
        else -> null
    }

    fun displayName(value: String): UiText? =
        if (value.normalizedText().codePointLength() > DISPLAY_NAME_MAX_LENGTH) {
            UiText.plural(R.plurals.validation_too_long, DISPLAY_NAME_MAX_LENGTH)
        } else {
            null
        }
}
