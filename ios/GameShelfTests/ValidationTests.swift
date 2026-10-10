import Foundation
import Testing
@testable import GameShelf

@Suite("Client-side validation")
struct ValidationTests {
    // MARK: Email of a new account (docs/mobile-spec.md, "Validation" and "Email pattern")

    /// The valid column of the spec's table, and other addresses the pattern must accept.
    @Test(arguments: [
        "jan.novak@example.com", "a+b@example.co.uk", "o'brien@example.ie", "jiří@příklad.cz", "user@пример.рф",
        "user@example.xn--p1ai", "a@b.cd",
        "collector@example.com", " a@b.io ", "\u{FEFF}a@b.cz\u{0}", "jan@example.museum", "a@b.рф",
        "!#$%&'*+/=?^_`{|}~-@example.com", "user@XN--P1AI.example.Xn--P1ai", "a@1.example.com",
    ])
    func validNewAccountEmails(_ email: String) {
        #expect(Validation.newAccountEmail(email) == nil)
    }

    /// The invalid column of the spec's table, and other addresses the API's `IsEmail` would reject.
    @Test(arguments: [
        "a..b@example.com", ".a@example.com", "a.@example.com", "a@exa_mple.com", "a@-example.com", "a@example-.com",
        "a@example..com", "a@example.com.", "a(b)@example.com", "a b@example.com", "a@example.c", "a@example",
        "a@1.2.3.4",
        "collector", "a@b", "a b@c.io", "a@b.c", "a@b.c1", "a@b.12", "a@b.c-", "a@@example.com", "a@b@example.com",
        "@example.com", "a@", "a@.example.com", "a\n@example.com",
        // Full-width characters (U+FF01–U+FF5E), anywhere.
        "ａ@example.com", "a@example.ｃｏｍ",
        // Outside the Basic Multilingual Plane.
        "😀@example.com", "a@example.com😀",
        // A label of more than 63 characters, a top-level domain of more than 63 letters.
        "a@" + String(repeating: "b", count: 64) + ".com", "a@example." + String(repeating: "c", count: 64),
    ])
    func invalidNewAccountEmails(_ email: String) {
        #expect(Validation.newAccountEmail(email) == "Enter a valid email address.")
    }

    @Test func aNewAccountNeedsAnEmail() {
        #expect(Validation.newAccountEmail("") == "Enter your email.")
        #expect(Validation.newAccountEmail(" \u{FEFF}") == "Enter your email.")
    }

    @Test func newAccountEmailIsAtMost254CodePoints() {
        // 64 + 1 + 189: labels of at most 63 characters.
        let domain = [String(repeating: "b", count: 63), String(repeating: "c", count: 63), String(repeating: "d", count: 57), "com"]
            .joined(separator: ".")
        let local = String(repeating: "a", count: 64)
        #expect((local + "@" + domain).count == 254)
        #expect(Validation.newAccountEmail(local + "@" + domain) == nil)
        #expect(Validation.newAccountEmail(local + "@e" + domain) == "Must be at most 254 characters.")
    }

    @Test func newAccountEmailPartsAreLimitedInUTF8Bytes() {
        // "ř" is one code point but 2 bytes: 32 are 64 bytes before the @, 33 are 66.
        #expect(Validation.newAccountEmail(String(repeating: "ř", count: 32) + "@example.cz") == nil)
        #expect(Validation.newAccountEmail(String(repeating: "ř", count: 33) + "@example.cz") == "Enter a valid email address.")
        #expect(Validation.newAccountEmail(String(repeating: "a", count: 65) + "@example.cz") == "Enter a valid email address.")
        // A domain of 3 labels of 42 × "č" and "cz": 131 code points, but 257 bytes.
        let label = String(repeating: "č", count: 42)
        let domain = [label, label, label, "cz"].joined(separator: ".")
        #expect(domain.unicodeScalars.count == 131)
        #expect(domain.utf8.count == 257)
        #expect(Validation.newAccountEmail("a@" + domain) == "Enter a valid email address.")
        let longest = [label, label, String(repeating: "č", count: 40), "czk"].joined(separator: ".")
        #expect(longest.utf8.count == 254)
        #expect(Validation.newAccountEmail("a@" + longest) == nil)
    }

    // MARK: Email to sign in with

    @Test(arguments: [
        "collector@example.com", "a@b", "a..b@example.com", "a(b)@example.com", "😀@example.com", "a@1.2.3.4",
        " a@b \u{FEFF}", "@",
    ])
    func anyAddressWithAnAtSignCanSignIn(_ email: String) {
        #expect(Validation.signInEmail(email) == nil)
    }

    @Test func signingInNeedsAnEmailWithAnAtSign() {
        #expect(Validation.signInEmail("") == "Enter your email.")
        #expect(Validation.signInEmail("\u{FEFF} ") == "Enter your email.")
        #expect(Validation.signInEmail("collector") == "Enter a valid email address.")
        #expect(Validation.signInEmail("collector＠example.com") == "Enter a valid email address.")
    }

    @Test func signInEmailIsAtMost254CodePoints() {
        let domain = "@example.com"
        #expect(Validation.signInEmail(String(repeating: "a", count: 254 - domain.count) + domain) == nil)
        #expect(Validation.signInEmail(String(repeating: "a", count: 255 - domain.count) + domain) == "Must be at most 254 characters.")
        // Counted in code points: 254 emoji are 254.
        #expect(Validation.signInEmail(String(repeating: "😀", count: 253) + "@") == nil)
    }

    @Test @MainActor func signingInAndCreatingAnAccountCheckTheEmailByTheirOwnRules() async {
        let session = PreviewData.sessionStore(signedIn: false)
        let login = LoginViewModel()
        login.email = "a..b@example.com"
        // Without a password nothing is sent.
        await login.submit(using: session)
        #expect(login.emailError == nil)
        #expect(login.passwordError == "Enter your password.")

        let register = RegisterViewModel()
        register.email = "a..b@example.com"
        await register.submit(using: session)
        #expect(register.emailError == "Enter a valid email address.")
    }

    @Test @MainActor func theNewPasswordMustDifferFromTheCurrentOne() async {
        let model = ChangePasswordViewModel()
        model.currentPassword = "old-password"
        model.newPassword = "old-password"
        model.confirmation = "old-password"
        await model.submit(using: PreviewData.sessionStore(signedIn: true))
        #expect(model.newPasswordError == "The new password must be different from the current one.")

        // Compared code point by code point, as the API compares them.
        #expect(Validation.differentPassword("caf\u{E9}-password", from: "cafe\u{301}-password") == nil)
    }

    @Test func textIsNormalizedLikeTheAPIDoes() {
        #expect("\u{FEFF}  Doom\u{0} 64 \n\u{FEFF}".normalizedText == "Doom 64")
        #expect("D\u{0}oom".normalizedText == "Doom")
        #expect("\u{FEFF}\u{0} ".isBlank)
        #expect(" \u{FEFF}".nilIfBlank == nil)
        // A pasted byte order mark doesn't count towards the length or make a title.
        #expect(Validation.maxLength("\u{FEFF}ab\u{0}", 2) == nil)
        #expect(Validation.title("\u{FEFF}\u{0}") == "Enter a title.")
    }

    @Test func aDraftIsSavedNormalized() throws {
        var draft = GameDraft()
        draft.title = "\u{FEFF}Do\u{0}om "
        draft.platform = .pc
        draft.notes = "\u{FEFF}"
        draft.currency = " eur\u{FEFF}"
        let request = try #require(draft.makeRequest())
        #expect(request.title == "Doom")
        #expect(request.notes == nil)
        #expect(request.currency == "EUR")
    }

    @Test func passwordLength() {
        #expect(Validation.newPassword("1234567") != nil)
        #expect(Validation.newPassword("12345678") == nil)
        #expect(Validation.newPassword(String(repeating: "x", count: 128)) == nil)
        #expect(Validation.newPassword(String(repeating: "x", count: 129)) != nil)
        #expect(Validation.passwordConfirmation("abc", matching: "abd") == "Passwords don't match.")
    }

    @Test func passwordLengthIsCountedInCodePoints() {
        // 4 flags are 4 characters but 8 code points.
        #expect(Validation.newPassword(String(repeating: "🇨🇿", count: 4)) == nil)
        #expect(Validation.newPassword(String(repeating: "e\u{301}", count: 4)) == nil)
        #expect(Validation.newPassword(String(repeating: "😀", count: 7)) != nil)
        // 65 flags are 130 code points.
        #expect(Validation.newPassword(String(repeating: "🇨🇿", count: 65)) != nil)
    }

    @Test func lengthsAreCountedInCodePoints() {
        #expect(Validation.maxLength("😀", 1) == nil)
        #expect(Validation.maxLength("🇨🇿", 1) == "Must be at most 1 characters.")
        #expect(Validation.maxLength("🇨🇿", 2) == nil)
        // A decomposed "é" (e + U+0301) is 2.
        #expect(Validation.maxLength("e\u{301}", 1) != nil)
        #expect(Validation.maxLength("e\u{301}", 2) == nil)
        // Surrounding whitespace is trimmed before counting.
        #expect(Validation.maxLength("  ab  ", 2) == nil)
    }

    @Test @MainActor func displayNameIsAtMost100CodePoints() {
        #expect(Validation.displayName(String(repeating: "x", count: 100)) == nil)
        #expect(Validation.displayName(String(repeating: "x", count: 99) + "🇨🇿") != nil)
        let model = RegisterViewModel()
        model.displayName = String(repeating: "😀", count: 101)
        #expect(model.displayNameError == "Must be at most 100 characters.")
    }

    /// Every text field of the game form with its limit (docs/mobile-spec.md, "Validation").
    private static let textLimits: [(field: GameDraft.Field, limit: Int)] = [
        (.title, 200), (.edition, 100), (.genre, 100), (.developer, 100), (.publisher, 100),
        (.purchasePlace, 100), (.storageLocation, 100), (.productCode, 50), (.notes, 5000),
    ]

    @Test(arguments: textLimits)
    func gameTextFieldLimits(field: GameDraft.Field, limit: Int) {
        func draft(_ value: String) -> GameDraft {
            var draft = GameDraft()
            draft.title = "Doom"
            draft.platform = .pc
            switch field {
            case .title: draft.title = value
            case .edition: draft.edition = value
            case .genre: draft.genre = value
            case .developer: draft.developer = value
            case .publisher: draft.publisher = value
            case .purchasePlace: draft.purchasePlace = value
            case .storageLocation: draft.storageLocation = value
            case .productCode: draft.productCode = value
            case .notes: draft.notes = value
            default: Issue.record("No text field \(field)")
            }
            return draft
        }
        #expect(draft(String(repeating: "x", count: limit)).errors(includingRequired: true).isEmpty)
        // `limit` characters, but one more code point.
        let tooLong = draft(String(repeating: "x", count: limit - 1) + "🇨🇿")
        #expect(tooLong.errors(includingRequired: true)[field] == "Must be at most \(limit) characters.")
        #expect(tooLong.makeRequest() == nil)
    }

    @Test func coverURLIsAtMost2048CodePoints() {
        let prefix = "https://example.com/"
        #expect(Validation.coverURL(prefix + String(repeating: "a", count: 2048 - prefix.count)) == nil)
        #expect(Validation.coverURL(prefix + String(repeating: "a", count: 2049 - prefix.count)) != nil)
    }

    // MARK: Purchase date

    @Test(arguments: [
        ("1900-01-01", true), ("2100-12-31", true), ("2024-05-17", true),
        ("1899-12-31", false), ("2101-01-01", false), ("0999-12-31", false), ("9999-12-31", false),
    ])
    func purchaseDate(date: String, isValid: Bool) throws {
        let date = try #require(LocalDate(date))
        #expect(Validation.purchaseDate(date) == (isValid ? nil : "Enter a date between 1900 and 2100."))
    }

    @Test func theDatePickersOfferExactlyTheAcceptedDays() {
        let range = Validation.purchaseDatePickerRange
        #expect(LocalDate(range.lowerBound) == LocalDate(year: 1900, month: 1, day: 1))
        #expect(LocalDate(range.upperBound) == LocalDate(year: 2100, month: 12, day: 31))
        // The bounds are the first moment of each day in the calendar the picked dates are read with.
        #expect(LocalDate(range.lowerBound.addingTimeInterval(-1)) == LocalDate(year: 1899, month: 12, day: 31))
        let dayAfter = LocalDate.calendar.date(byAdding: .day, value: 1, to: range.upperBound)!
        #expect(LocalDate(dayAfter) == LocalDate(year: 2101, month: 1, day: 1))
    }

    @Test func aChangedPurchaseDateIsValidated() throws {
        var draft = GameDraft()
        draft.title = "Doom"
        draft.platform = .pc
        draft.purchaseDate = LocalDate(year: 1899, month: 12, day: 31)?.date()
        #expect(draft.errors(includingRequired: false)[.purchaseDate] == "Enter a date between 1900 and 2100.")
        #expect(draft.makeRequest() == nil)

        draft.purchaseDate = LocalDate(year: 1900, month: 1, day: 1)?.date()
        #expect(draft.errors(includingRequired: true).isEmpty)
        #expect(draft.makeRequest()?.purchaseDate == LocalDate(year: 1900, month: 1, day: 1))
    }

    @Test func anUnchangedPurchaseDateIsNotValidated() throws {
        // Stored before the current rules.
        let game = Fixtures.game("Doom") { $0.purchaseDate = LocalDate(year: 1850, month: 6, day: 1) }
        let initial = GameDraft(game: game)
        var draft = initial
        draft.notes = "Edited"
        #expect(draft.errors(includingRequired: true, initial: initial).isEmpty)
        let request = try #require(draft.makeRequest(initial: initial))
        #expect(request.changedFields(comparedTo: SaveGameRequest(game: game)) == [.notes])

        draft.purchaseDate = LocalDate(year: 2101, month: 1, day: 1)?.date()
        #expect(draft.errors(includingRequired: true, initial: initial)[.purchaseDate] == "Enter a date between 1900 and 2100.")
    }

    @Test(arguments: [("", true), ("1950", true), ("2100", true), ("1949", false), ("2101", false), ("19a8", false)])
    func releaseYear(text: String, isValid: Bool) {
        #expect((Validation.releaseYear(text) == nil) == isValid)
    }

    @Test(arguments: [
        ("", nil), ("0", nil), ("1299.9", nil), ("1,299.90", nil), ("12,5", nil), ("1,500", nil),
        ("9 999 999 999,99", nil), ("1,50", nil),
        ("-1", "The amount can't be negative."),
        ("1.234,567", "Use at most 2 decimal places."),
        ("1,5000", "Use at most 2 decimal places."),
        ("0,0001", "Use at most 2 decimal places."),
        ("10000000000", "The amount is too high."),
        ("abc", "Enter a number, e.g. 499.90."),
        ("1.2.3", "Enter a number, e.g. 499.90."),
    ] as [(String, String?)])
    func price(text: String, expected: String?) {
        #expect(Validation.price(text) == expected)
    }

    @Test(arguments: [
        ("", true), ("12345678", true), ("12345678901234", true), ("1234567", false), ("123456789012345", false),
        ("1234abcd", false), ("１２３４５６７８", false),
    ])
    func barcode(text: String, isValid: Bool) {
        #expect((Validation.barcode(text) == nil) == isValid)
    }

    // MARK: Cover URL (the vectors of docs/mobile-spec.md, "Cover URL pattern")

    @Test(arguments: [
        "https://example.com/a.png",
        "HTTPS://Example.COM/A.png",
        "http://localhost/x.png",
        "https://nas/cover.jpg",
        "https://example.com:8443/a?b=c#d",
        "https://example.com?x=1",
        "https://img.example.co.uk/a_b/%C3%A9.jpg",
    ])
    func validCoverURLs(_ url: String) {
        #expect(Validation.coverURL(url) == nil)
    }

    @Test(arguments: [
        "ftp://example.com/a.png",
        "example.com/a.png",
        "https://",
        "https://-example.com/a.png",
        "https://example.com./a.png",
        "https://exa mple.com/a.png",
        "https://example.com/a b.png",
        "https://example.com/é.png",
        "https://user:pw@example.com/a.png",
        "https://my_host/a.png",
        "https://[::1]/a.png",
        "https://example.com:123456/a.png",
    ])
    func invalidCoverURLs(_ url: String) {
        #expect(Validation.coverURL(url) == "Enter a valid URL starting with http:// or https://.")
    }

    @Test func coverURLIsTrimmedAndOptional() {
        #expect(Validation.coverURL("") == nil)
        #expect(Validation.coverURL("  https://example.com/a.png \n") == nil)
        // A decomposed "é" is not ASCII either.
        #expect(Validation.coverURL("https://example.com/e\u{301}.png") != nil)
    }

    // MARK: Currency

    @Test(arguments: ["CZK", "eur", "Usd", " gbp ", "DEM", "SKK", "XYZ"])
    func validCurrencies(_ currency: String) {
        #expect(Validation.currency(currency) == nil)
    }

    @Test(arguments: ["", "CZ", "CZK1", "C1K", "ČZK", "€UR", "C K"])
    func invalidCurrencies(_ currency: String) {
        #expect(Validation.currency(currency) == "The currency must be 3 letters, e.g. CZK.")
    }

    // MARK: Draft

    @Test func draftRequiresTitleAndPlatformOnlyAfterSaveAttempt() {
        var draft = GameDraft()
        #expect(draft.errors(includingRequired: false).isEmpty)
        #expect(draft.errors(includingRequired: true)[.title] == "Enter a title.")
        #expect(draft.errors(includingRequired: true)[.platform] == "Choose a platform.")
        #expect(draft.makeRequest() == nil)

        draft.title = "  Doom  "
        draft.platform = .pc
        draft.purchasePrice = "199.5"
        draft.estimatedValue = "1 500"
        draft.currency = "skk"
        let request = draft.makeRequest()
        #expect(request?.title == "Doom")
        #expect(request?.purchasePrice == Decimal(string: "199.5"))
        #expect(request?.estimatedValue == 1500)
        #expect(request?.currency == "SKK")
        #expect(request?.edition == nil)
    }

    @Test func requiredFieldsAreCheckedEvenWhenUnchanged() {
        let initial = GameDraft()
        #expect(initial.errors(includingRequired: true, initial: initial)[.title] == "Enter a title.")
        #expect(initial.errors(includingRequired: true, initial: initial)[.platform] == "Choose a platform.")
    }

    /// A game the server stored before the current rules (or with values this version can't check).
    private static func legacyGame() -> Game {
        Fixtures.game(String(repeating: "T", count: 250), .snes) {
            $0.coverImageUrl = "https://example.com/é.png"
            $0.barcode = "ABC"
            $0.releaseYear = 1900
            $0.productCode = String(repeating: "P", count: 60)
            $0.status = .unknown
            $0.condition = .unknown
            $0.currency = "XX"
        }
    }

    @Test func valuesTheUserDidNotChangeAreNotValidated() throws {
        let game = Self.legacyGame()
        let initial = GameDraft(game: game)
        var draft = initial
        #expect(!draft.errors(includingRequired: true).isEmpty, "without the initial state everything is checked")
        #expect(draft.errors(includingRequired: true, initial: initial).isEmpty)

        draft.notes = "Edited"
        let request = try #require(draft.makeRequest(initial: initial))
        #expect(request.changedFields(comparedTo: SaveGameRequest(game: game)) == [.notes])

        // A value the user changes is validated again.
        draft.coverImageUrl = "https://example.com/ü.png"
        #expect(draft.errors(includingRequired: true, initial: initial)[.coverImageUrl] != nil)
        #expect(draft.makeRequest(initial: initial) == nil)
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

    @Test func filterPriceBoundsUseTheDecimalInputRules() {
        var draft = FilterDraft()
        draft.purchasePriceMin = "1,500"
        draft.purchasePriceMax = "12.345,50"
        #expect(draft.makeFilter()?.purchasePriceMin == 1500)
        #expect(draft.makeFilter()?.purchasePriceMax == Decimal(string: "12345.5"))

        draft.purchasePriceMax = "1,5000"
        #expect(draft.errors[.purchasePrice] == "Use at most 2 decimal places.")
    }
}

@MainActor
@Suite("Validation in the game form")
struct GameFormValidationTests {
    /// A repository of an activated user holding `games`.
    private func makeRepository(_ games: [Game]) async throws -> GameRepository {
        let server = FakeGameServer()
        for game in games {
            await server.seed(game)
        }
        let repository = GameRepository(store: try GameStore(url: nil), status: SyncStatus())
        let sync = SyncEngine(repository: repository, api: server)
        repository.onLocalChange = nil
        await sync.activate(ownerID: "user-1")
        try await sync.syncNow()
        return repository
    }

    @Test func anEditOfAnotherFieldIsSavedDespiteValuesTheFormWouldReject() async throws {
        let game = Fixtures.game(String(repeating: "T", count: 250), .snes) {
            $0.coverImageUrl = "https://example.com/é.png"
            $0.status = .unknown
        }
        let repository = try await makeRepository([game])
        let stored = try #require(repository.game(id: game.id))
        let model = GameFormViewModel(mode: .edit(stored), repository: repository)
        #expect(model.errors.isEmpty)

        model.draft.notes = "Signed by the developer"
        #expect(await model.save())

        let saved = try #require(repository.game(id: game.id))
        #expect(saved.notes == "Signed by the developer")
        // Untouched values – including the unknown status – are kept and not sent back.
        #expect(saved.title == stored.title)
        #expect(saved.coverImageUrl == "https://example.com/é.png")
        #expect(saved.status == .unknown)
        #expect(try await repository.store.pendingChanges().map(\.fields) == [[.notes]])
    }

    @Test func unknownValuesAreShownAsUnknownAndChangedOnlyByPickingAnother() async throws {
        let game = try Fixtures.gameWithUnknownValues()
        let repository = try await makeRepository([game])
        let model = GameFormViewModel(mode: .edit(try #require(repository.game(id: game.id))), repository: repository)
        // The pickers' current values: "Unknown", and "Other" for platform and region.
        #expect(model.draft.status == .unknown)
        #expect(model.draft.status.label == "Unknown")
        #expect(model.draft.format.label == "Unknown")
        #expect(model.draft.completeness?.label == "Unknown")
        #expect(model.draft.condition?.label == "Unknown")
        #expect(model.draft.playStatus?.label == "Unknown")
        #expect(model.draft.platform?.label == "Other")
        #expect(model.draft.region?.label == "Other")
        #expect(!model.hasChanges)

        // Picking the value that is shown changes nothing.
        model.draft.status = .unknown
        model.draft.platform = .other
        #expect(!model.hasChanges)

        // Picking "Owned" deliberately is a change, and is saved and sent.
        model.draft.status = .owned
        #expect(model.hasChanges)
        #expect(await model.save())

        let saved = try #require(repository.game(id: game.id))
        #expect(saved.status == .owned)
        var expected = Fixtures.unknownWireValues
        expected[.status] = nil
        #expect(saved.unknownWireValues == expected)
        let pending = try #require(try await repository.store.pendingChanges().first)
        #expect(pending.fields == [.status])
        let body = try JSONEncoder.api().encode(UpdateGameRequest(values: SaveGameRequest(game: saved), fields: pending.fields))
        #expect(String(decoding: body, as: UTF8.self) == #"{"status":"OWNED"}"#)
    }

    @Test func anEditOfAnotherFieldKeepsTheUnknownValuesAsTheAPISentThem() async throws {
        let game = try Fixtures.gameWithUnknownValues()
        let repository = try await makeRepository([game])
        let model = GameFormViewModel(mode: .edit(try #require(repository.game(id: game.id))), repository: repository)
        model.draft.rating = 3
        #expect(await model.save())

        let saved = try #require(repository.game(id: game.id))
        #expect(saved.rating == 3)
        #expect(saved.unknownWireValues == Fixtures.unknownWireValues)
        #expect(try await repository.store.pendingChanges().map(\.fields) == [[.rating]])
    }
}
