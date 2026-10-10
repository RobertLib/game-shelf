package cz.gameshelf.app.ui.auth

import cz.gameshelf.app.R
import cz.gameshelf.app.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `a new password has 8 to 128 code points`() {
        val invalid = UiText(R.string.validation_password_length)

        assertNull(AuthValidation.newPassword("x".repeat(8)))
        assertNull(AuthValidation.newPassword("x".repeat(128)))
        assertEquals(invalid, AuthValidation.newPassword("x".repeat(7)))
        assertEquals(invalid, AuthValidation.newPassword("x".repeat(129)))
        // 4 emoji are 8 UTF-16 chars but only 4 code points; 128 emoji are 128.
        assertEquals(invalid, AuthValidation.newPassword("😀".repeat(4)))
        assertNull(AuthValidation.newPassword("😀".repeat(128)))
    }

    @Test
    fun `a display name has at most 100 code points after trimming`() {
        val tooLong = UiText.plural(R.plurals.validation_too_long, 100)

        assertNull(AuthValidation.displayName(" " + "x".repeat(100) + " "))
        assertNull(AuthValidation.displayName("😀".repeat(100)))
        assertEquals(tooLong, AuthValidation.displayName("😀".repeat(101)))
        assertEquals(tooLong, AuthValidation.displayName("🇨🇿".repeat(50) + "x"))
    }

    @Test
    fun `email must look like an address`() {
        assertNull(AuthValidation.email(" collector@example.com "))
        assertEquals(UiText(R.string.validation_required), AuthValidation.email(" "))
        assertEquals(UiText(R.string.validation_email), AuthValidation.email("collector@example"))
    }

    @Test
    fun `the confirmation must match`() {
        assertNull(AuthValidation.passwordConfirmation("secret123", "secret123"))
        assertEquals(UiText(R.string.validation_password_mismatch), AuthValidation.passwordConfirmation("secret123", "secret124"))
        assertEquals(UiText(R.string.validation_required), AuthValidation.passwordConfirmation("secret123", ""))
    }
}
