import Foundation

/// Editable copy of ``GameFilter`` used by the filter sheet. Numeric ranges are kept
/// as text until "Apply" so they can be validated inline.
struct FilterDraft: Equatable {
    enum RangeField: Hashable {
        case releaseYear, purchasePrice, estimatedValue, purchaseDate
    }

    var filter: GameFilter
    var releaseYearFrom: String
    var releaseYearTo: String
    var purchasePriceMin: String
    var purchasePriceMax: String
    var estimatedValueMin: String
    var estimatedValueMax: String
    var purchaseDateFrom: Date?
    var purchaseDateTo: Date?

    init(_ filter: GameFilter = GameFilter()) {
        self.filter = filter
        releaseYearFrom = filter.releaseYearFrom.map(String.init) ?? ""
        releaseYearTo = filter.releaseYearTo.map(String.init) ?? ""
        purchasePriceMin = filter.purchasePriceMin.map { AppFormat.editableNumber($0) } ?? ""
        purchasePriceMax = filter.purchasePriceMax.map { AppFormat.editableNumber($0) } ?? ""
        estimatedValueMin = filter.estimatedValueMin.map { AppFormat.editableNumber($0) } ?? ""
        estimatedValueMax = filter.estimatedValueMax.map { AppFormat.editableNumber($0) } ?? ""
        purchaseDateFrom = filter.purchaseDateFrom?.date()
        purchaseDateTo = filter.purchaseDateTo?.date()
    }

    var isEmpty: Bool { self == FilterDraft() }

    var errors: [RangeField: String] {
        var errors: [RangeField: String] = [:]

        if let message = Validation.releaseYear(releaseYearFrom) ?? Validation.releaseYear(releaseYearTo) {
            errors[.releaseYear] = message
        } else if let from = NumberInput.integer(releaseYearFrom), let to = NumberInput.integer(releaseYearTo), from > to {
            errors[.releaseYear] = Self.orderMessage
        }

        for (field, min, max) in [
            (RangeField.purchasePrice, purchasePriceMin, purchasePriceMax),
            (RangeField.estimatedValue, estimatedValueMin, estimatedValueMax),
        ] {
            if let message = Validation.price(min) ?? Validation.price(max) {
                errors[field] = message
            } else if let low = NumberInput.decimal(min), let high = NumberInput.decimal(max), low > high {
                errors[field] = Self.orderMessage
            }
        }

        if let from = purchaseDateFrom, let to = purchaseDateTo, LocalDate(from) > LocalDate(to) {
            errors[.purchaseDate] = Self.orderMessage
        }
        return errors
    }

    /// The filter to apply, or `nil` while a range is invalid.
    func makeFilter() -> GameFilter? {
        guard errors.isEmpty else { return nil }
        var result = filter
        result.publisher = filter.publisher.trimmingCharacters(in: .whitespacesAndNewlines)
        result.developer = filter.developer.trimmingCharacters(in: .whitespacesAndNewlines)
        result.storageLocation = filter.storageLocation.trimmingCharacters(in: .whitespacesAndNewlines)
        result.releaseYearFrom = NumberInput.integer(releaseYearFrom)
        result.releaseYearTo = NumberInput.integer(releaseYearTo)
        result.purchasePriceMin = NumberInput.decimal(purchasePriceMin)
        result.purchasePriceMax = NumberInput.decimal(purchasePriceMax)
        result.estimatedValueMin = NumberInput.decimal(estimatedValueMin)
        result.estimatedValueMax = NumberInput.decimal(estimatedValueMax)
        result.purchaseDateFrom = purchaseDateFrom.map { LocalDate($0) }
        result.purchaseDateTo = purchaseDateTo.map { LocalDate($0) }
        return result
    }

    private static let orderMessage = "The “from” value can't be greater than “to”."
}
