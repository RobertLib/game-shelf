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
    fun `a new password must differ from the current one`() {
        assertEquals(
            UiText(R.string.validation_password_unchanged),
            AuthValidation.changedPassword(currentPassword = "secret123", newPassword = "secret123"),
        )
        assertNull(AuthValidation.changedPassword(currentPassword = "secret123", newPassword = "secret124"))
        // The length rule comes first.
        assertEquals(UiText(R.string.validation_password_length), AuthValidation.changedPassword("short", "short"))
    }

    @Test
    fun `a display name has at most 100 code points after normalizing`() {
        val tooLong = UiText.plural(R.plurals.validation_too_long, 100)

        assertNull(AuthValidation.displayName(" " + "x".repeat(100) + " "))
        assertNull(AuthValidation.displayName("\uFEFF" + "x".repeat(100) + "\u0000\n"))
        assertNull(AuthValidation.displayName("😀".repeat(100)))
        assertEquals(tooLong, AuthValidation.displayName("😀".repeat(101)))
        assertEquals(tooLong, AuthValidation.displayName("🇨🇿".repeat(50) + "x"))
    }

    private val invalidEmail = UiText(R.string.validation_email)

    @Test
    fun `a new account's email is checked against every row of the spec's table`() {
        listOf(
            "jan.novak@example.com",
            "a+b@example.co.uk",
            "o'brien@example.ie",
            "jiří@příklad.cz",
            "user@пример.рф",
            "user@example.xn--p1ai",
            "a@b.cd",
        ).forEach { assertNull(it, AuthValidation.newAccountEmail(it)) }
        listOf(
            "a..b@example.com",
            ".a@example.com",
            "a.@example.com",
            "a@exa_mple.com",
            "a@-example.com",
            "a@example-.com",
            "a@example..com",
            "a@example.com.",
            "a(b)@example.com",
            "a b@example.com",
            "a@example.c",
            "a@example",
            "a@1.2.3.4",
        ).forEach { assertEquals(it, invalidEmail, AuthValidation.newAccountEmail(it)) }
    }

    @Test
    fun `a new account's email is normalized and required`() {
        assertNull(AuthValidation.newAccountEmail(" collector@example.com "))
        assertNull(AuthValidation.newAccountEmail("\uFEFFcollector@example.cz\u0000"))
        assertNull(AuthValidation.newAccountEmail("collector@example.com\u2003"))
        assertEquals(UiText(R.string.validation_required), AuthValidation.newAccountEmail(" \uFEFF"))
    }

    @Test
    fun `a new account's email has no character outside the BMP and no full-width ones`() {
        listOf(
            "😀@example.com",
            "a😀b@example.com",
            "a@exa😀mple.com",
            "ａ@example.com",
            "a@ｅxample.com",
            "a\u00A0b@example.com",
        ).forEach { assertEquals(it, invalidEmail, AuthValidation.newAccountEmail(it)) }
        // Letters of the BMP are fine, also before the @.
        assertNull(AuthValidation.newAccountEmail("žluťoučký.kůň@example.cz"))
    }

    @Test
    fun `a new account's email has at most 64 bytes before the @ and 254 after it`() {
        assertNull(AuthValidation.newAccountEmail("a".repeat(64) + "@example.com"))
        assertEquals(invalidEmail, AuthValidation.newAccountEmail("a".repeat(65) + "@example.com"))
        // 32 and 33 code points, but 64 and 66 bytes.
        assertNull(AuthValidation.newAccountEmail("é".repeat(32) + "@example.com"))
        assertEquals(invalidEmail, AuthValidation.newAccountEmail("é".repeat(33) + "@example.com"))
        // Domains of 128 and 131 code points, but 251 and 257 bytes.
        fun domain(labelLength: Int) = List(3) { "č".repeat(labelLength) }.joinToString(".") + ".cz"
        assertNull(AuthValidation.newAccountEmail("a@" + domain(41)))
        assertEquals(invalidEmail, AuthValidation.newAccountEmail("a@" + domain(42)))
    }

    @Test
    fun `an email has at most 254 code points`() {
        val maximal = "${"a".repeat(64)}@${"b".repeat(61)}.${"c".repeat(61)}.${"d".repeat(61)}.com"
        assertEquals(254, maximal.length)
        val tooLong = UiText.plural(R.plurals.validation_too_long, 254)

        assertNull(AuthValidation.newAccountEmail(maximal))
        assertEquals(tooLong, AuthValidation.newAccountEmail("a$maximal"))
        assertNull(AuthValidation.signInEmail(maximal))
        assertEquals(tooLong, AuthValidation.signInEmail("a$maximal"))
    }

    @Test
    fun `signing in needs only an @, so that every existing account can sign in`() {
        listOf("a..b@example.com", "a@example", "a@1.2.3.4", "😀@example.com", "a b@c", " collector@example.com ")
            .forEach { assertNull(it, AuthValidation.signInEmail(it)) }
        assertEquals(invalidEmail, AuthValidation.signInEmail("collector.example.com"))
        assertEquals(UiText(R.string.validation_required), AuthValidation.signInEmail(" \uFEFF"))
    }

    @Test
    fun `the confirmation must match`() {
        assertNull(AuthValidation.passwordConfirmation("secret123", "secret123"))
        assertEquals(UiText(R.string.validation_password_mismatch), AuthValidation.passwordConfirmation("secret123", "secret124"))
        assertEquals(UiText(R.string.validation_required), AuthValidation.passwordConfirmation("secret123", ""))
    }
}
