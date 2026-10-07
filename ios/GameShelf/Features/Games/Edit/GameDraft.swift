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

    /// Validation messages per field. "Required" errors are only reported once the
    /// user tried to save, format errors immediately.
    func errors(includingRequired: Bool) -> [Field: String] {
        var errors: [Field: String] = [:]
        func check(_ field: Field, _ message: String?) {
            if let message { errors[field] = message }
        }

        if includingRequired || !title.isBlank {
            check(.title, Validation.title(title))
        }
        if includingRequired, platform == nil {
            errors[.platform] = "Choose a platform."
        }
        check(.edition, Validation.maxLength(edition, 100))
        check(.genre, Validation.maxLength(genre, 100))
        check(.developer, Validation.maxLength(developer, 100))
        check(.publisher, Validation.maxLength(publisher, 100))
        check(.releaseYear, Validation.releaseYear(releaseYear))
        check(.coverImageUrl, Validation.coverURL(coverImageUrl))
        check(.barcode, Validation.barcode(barcode))
        check(.productCode, Validation.maxLength(productCode, 50))
        check(.storageLocation, Validation.maxLength(storageLocation, 100))
        check(.purchasePrice, Validation.price(purchasePrice))
        check(.estimatedValue, Validation.price(estimatedValue))
        if includingRequired || !currency.isBlank {
            check(.currency, Validation.currency(currency))
        }
        check(.purchasePlace, Validation.maxLength(purchasePlace, 100))
        check(.notes, Validation.maxLength(notes, 5000))
        return errors
    }

    /// The request to send, or `nil` while the draft is invalid.
    func makeRequest() -> SaveGameRequest? {
        guard errors(includingRequired: true).isEmpty, let platform else { return nil }
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
