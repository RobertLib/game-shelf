import Foundation

/// Editable form state of a game. Text inputs stay strings until saving so that
/// partially typed values (e.g. "12,") can be validated inline.
struct GameDraft: Equatable {
    enum Field: Hashable {
        case title, platform, edition, genre, developer, publisher, releaseYear, coverImageUrl
        case barcode, productCode, storageLocation
        case purchasePrice, estimatedValue, currency, purchasePlace
        case notes
    }

    var title = ""
    var platform: Platform?
    var edition = ""
    var genre = ""
    var developer = ""
    var publisher = ""
    var releaseYear = ""
    var coverImageUrl = ""

    var status: CollectionStatus = .owned
    var format: GameFormat = .physical
    var region: Region?
    var completeness: Completeness?
    var condition: Condition?
    var barcode = ""
    var productCode = ""
    var quantity = 1
    var storageLocation = ""

    var purchasePrice = ""
    var estimatedValue = ""
    var currency = "CZK"
    var purchaseDate: Date?
    var purchasePlace = ""

    var playStatus: PlayStatus?
    var rating: Int?
    var favorite = false
    var notes = ""

    init() {}

    init(game: Game) {
        let request = SaveGameRequest(game: game)
        title = request.title
        platform = request.platform
        edition = request.edition ?? ""
        genre = request.genre ?? ""
        developer = request.developer ?? ""
        publisher = request.publisher ?? ""
        releaseYear = request.releaseYear.map(String.init) ?? ""
        coverImageUrl = request.coverImageUrl ?? ""
        status = request.status
        format = request.format
        region = request.region
        completeness = request.completeness
        condition = request.condition
        barcode = request.barcode ?? ""
        productCode = request.productCode ?? ""
        quantity = request.quantity
        storageLocation = request.storageLocation ?? ""
        purchasePrice = request.purchasePrice.map { AppFormat.editableNumber($0) } ?? ""
        estimatedValue = request.estimatedValue.map { AppFormat.editableNumber($0) } ?? ""
        currency = request.currency
        purchaseDate = request.purchaseDate?.date()
        purchasePlace = request.purchasePlace ?? ""
        playStatus = request.playStatus
        rating = request.rating
        favorite = request.favorite
        notes = request.notes ?? ""
    }

    /// Fills the empty fields with what a barcode lookup found; whatever the user entered stays.
    mutating func fill(from lookup: BarcodeLookup) {
        func fill(_ field: inout String, with value: String?) {
            if field.isBlank, let value { field = value }
        }
        fill(&title, with: lookup.title)
        fill(&edition, with: lookup.edition)
        fill(&genre, with: lookup.genre)
        fill(&developer, with: lookup.developer)
        fill(&publisher, with: lookup.publisher)
        fill(&releaseYear, with: lookup.releaseYear.map(String.init))
        fill(&coverImageUrl, with: lookup.coverImageUrl)
        fill(&barcode, with: lookup.barcode)
        platform = platform ?? lookup.platform
        region = region ?? lookup.region
    }

    /// Fills in the game picked in the database search. Unlike a barcode lookup it replaces what the
    /// fields hold (the user chose this game); a value the database doesn't know leaves its field as it is.
    /// `platform`: the one chosen for the user's copy; `nil` leaves Platform as it is.
    mutating func fill(fromSearch game: GameSearchResult, platform: Platform?) {
        func replace(_ field: inout String, with value: String?) {
            if let value, !value.isBlank { field = value }
        }
        replace(&title, with: game.title)
        replace(&genre, with: game.genre)
        replace(&developer, with: game.developer)
        replace(&publisher, with: game.publisher)
        replace(&releaseYear, with: game.releaseYear.map(String.init))
        replace(&coverImageUrl, with: game.coverImageUrl)
        if let platform {
            self.platform = platform
        }
    }

    /// Validation messages per field. "Required" errors are only reported once the
    /// user tried to save, format errors immediately.
    ///
    /// Values the user did not change – equal to those in `initial`, the form's initial state – are not
    /// validated: they came from the server, and an edit sends only the changed fields
    /// (docs/mobile-spec.md, "Validation"). Required fields are always checked.
    func errors(includingRequired: Bool, initial: GameDraft? = nil) -> [Field: String] {
        var errors: [Field: String] = [:]
        func check(_ field: Field, _ value: KeyPath<GameDraft, String>, _ rule: (String) -> String?) {
            guard self[keyPath: value] != initial?[keyPath: value], let message = rule(self[keyPath: value]) else { return }
            errors[field] = message
        }
        func checkLength(_ field: Field, _ value: KeyPath<GameDraft, String>, _ limit: Int) {
            check(field, value) { Validation.maxLength($0, limit) }
        }

        if title.isBlank {
            if includingRequired { errors[.title] = Validation.title(title) }
        } else {
            check(.title, \.title, Validation.title)
        }
        if includingRequired, platform == nil {
            errors[.platform] = "Choose a platform."
        }
        checkLength(.edition, \.edition, Validation.maxTextLength)
        checkLength(.genre, \.genre, Validation.maxTextLength)
        checkLength(.developer, \.developer, Validation.maxTextLength)
        checkLength(.publisher, \.publisher, Validation.maxTextLength)
        check(.releaseYear, \.releaseYear, Validation.releaseYear)
        check(.coverImageUrl, \.coverImageUrl, Validation.coverURL)
        check(.barcode, \.barcode, Validation.barcode)
        checkLength(.productCode, \.productCode, Validation.maxProductCodeLength)
        checkLength(.storageLocation, \.storageLocation, Validation.maxTextLength)
        check(.purchasePrice, \.purchasePrice, Validation.price)
        check(.estimatedValue, \.estimatedValue, Validation.price)
        if includingRequired || !currency.isBlank {
            check(.currency, \.currency, Validation.currency)
        }
        checkLength(.purchasePlace, \.purchasePlace, Validation.maxTextLength)
        checkLength(.notes, \.notes, Validation.maxNotesLength)
        return errors
    }

    /// The request to send, or `nil` while the draft is invalid. `initial`: the form's initial state,
    /// whose values are not validated again (see ``errors(includingRequired:initial:)``).
    func makeRequest(initial: GameDraft? = nil) -> SaveGameRequest? {
        guard errors(includingRequired: true, initial: initial).isEmpty, let platform else { return nil }
        return SaveGameRequest(
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            platform: platform,
            status: status,
            format: format,
            region: region,
            edition: edition.nilIfBlank,
            completeness: completeness,
            condition: condition,
            playStatus: playStatus,
            genre: genre.nilIfBlank,
            developer: developer.nilIfBlank,
            publisher: publisher.nilIfBlank,
            releaseYear: NumberInput.integer(releaseYear),
            barcode: barcode.nilIfBlank,
            productCode: productCode.nilIfBlank,
            quantity: quantity,
            purchasePrice: NumberInput.decimal(purchasePrice),
            purchaseDate: purchaseDate.map { LocalDate($0) },
            purchasePlace: purchasePlace.nilIfBlank,
            estimatedValue: NumberInput.decimal(estimatedValue),
            currency: currency.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
            storageLocation: storageLocation.nilIfBlank,
            rating: rating,
            favorite: favorite,
            coverImageUrl: coverImageUrl.nilIfBlank,
            notes: notes.nilIfBlank
        )
    }
}
