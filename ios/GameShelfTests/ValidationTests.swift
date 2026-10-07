import Foundation
import Testing
@testable import GameShelf

@Suite("Client-side validation")
struct ValidationTests {
    @Test(arguments: ["collector@example.com", " a@b.io "])
    func validEmails(_ email: String) {
        #expect(Validation.email(email) == nil)
    }

    @Test(arguments: ["", "collector", "a@b", "a b@c.io"])
    func invalidEmails(_ email: String) {
        #expect(Validation.email(email) != nil)
    }

    @Test func passwordLength() {
        #expect(Validation.newPassword("1234567") != nil)
        #expect(Validation.newPassword("12345678") == nil)
        #expect(Validation.newPassword(String(repeating: "x", count: 129)) != nil)
        #expect(Validation.passwordConfirmation("abc", matching: "abd") == "Passwords don't match.")
    }

    @Test(arguments: [("", true), ("1950", true), ("2100", true), ("1949", false), ("2101", false), ("19a8", false)])
    func releaseYear(text: String, isValid: Bool) {
        #expect((Validation.releaseYear(text) == nil) == isValid)
    }

    @Test(arguments: [
        ("", true), ("0", true), ("1299.9", true), ("1,299.90", true), ("12,5", true),
        ("-1", false), ("1.234", false), ("abc", false), ("1.2.3", false), ("10000000000", false),
    ])
    func price(text: String, isValid: Bool) {
        #expect((Validation.price(text, locale: Locale(identifier: "en_US")) == nil) == isValid)
    }

    @Test(arguments: [("", true), ("12345678", true), ("12345678901234", true), ("1234567", false), ("123456789012345", false), ("1234abcd", false)])
    func barcode(text: String, isValid: Bool) {
        #expect((Validation.barcode(text) == nil) == isValid)
    }

    @Test(arguments: [("", true), ("https://example.com/a.jpg", true), ("http://localhost/x.png", true), ("ftp://example.com/a", false), ("example.com/a.jpg", false)])
    func coverURL(text: String, isValid: Bool) {
        #expect((Validation.coverURL(text) == nil) == isValid)
    }

    @Test func currency() {
        #expect(Validation.currency("CZK") == nil)
        #expect(Validation.currency("eur") == nil)
        #expect(Validation.currency("CZ") != nil)
        #expect(Validation.currency("CZK1") != nil)
    }

    @Test func numberInputUnderstandsTheLocaleSeparators() {
        #expect(NumberInput.decimal("1,299.90", locale: Locale(identifier: "en_US")) == Decimal(string: "1299.9"))
        #expect(NumberInput.decimal("1.299,90", locale: Locale(identifier: "de_DE")) == Decimal(string: "1299.9"))
        #expect(NumberInput.decimal("499,90", locale: Locale(identifier: "en_US")) == Decimal(string: "499.9"))
        #expect(NumberInput.decimal("1 299.90", locale: Locale(identifier: "en_US")) == Decimal(string: "1299.9"))
        #expect(NumberInput.decimal("1-2") == nil)
        #expect(NumberInput.integer(" 1998 ") == 1998)
    }

    @Test func draftRequiresTitleAndPlatformOnlyAfterSaveAttempt() {
        var draft = GameDraft()
        #expect(draft.errors(includingRequired: false).isEmpty)
        #expect(draft.errors(includingRequired: true)[.title] == "Enter a title.")
        #expect(draft.errors(includingRequired: true)[.platform] == "Choose a platform.")
        #expect(draft.makeRequest() == nil)

        draft.title = "  Doom  "
        draft.platform = .pc
        draft.purchasePrice = "199.5"
        draft.currency = "eur"
        let request = draft.makeRequest()
        #expect(request?.title == "Doom")
        #expect(request?.purchasePrice == Decimal(string: "199.5"))
        #expect(request?.currency == "EUR")
        #expect(request?.edition == nil)
    }

    @Test func filterDraftRejectsInvertedRanges() {
        var draft = FilterDraft()
        draft.releaseYearFrom = "2000"
        draft.releaseYearTo = "1990"
        #expect(draft.errors[.releaseYear] != nil)
        #expect(draft.makeFilter() == nil)

        draft.releaseYearTo = "2005"
        draft.purchasePriceMin = "100.5"
        let filter = draft.makeFilter()
        #expect(filter?.releaseYearFrom == 2000)
        #expect(filter?.releaseYearTo == 2005)
        #expect(filter?.purchasePriceMin == Decimal(string: "100.5"))
    }
}
