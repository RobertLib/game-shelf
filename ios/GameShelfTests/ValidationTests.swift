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
}
