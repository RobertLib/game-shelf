import Foundation
import Testing
@testable import GameShelf

/// The vectors of docs/mobile-spec.md, "Decimal input".
@Suite("Decimal input")
struct DecimalInputTests {
    @Test(arguments: [
        ("1299.90", "1299.90", 2),
        ("1299,9", "1299.9", 1),
        ("1 299,90", "1299.90", 2),
        ("1,299.90", "1299.90", 2),
        ("1.299,90", "1299.90", 2),
        ("1,5", "1.5", 1),
        ("1,50", "1.50", 2),
        ("1,000", "1000", 0),
        ("2.500", "2500", 0),
        ("1.234.567,89", "1234567.89", 2),
        ("12,345,678", "12345678", 0),
        (",5", "0.5", 1),
        ("5,", "5", 0),
        ("1,5000", "1.5", 4),
    ])
    func validInput(text: String, value: String, decimalPlaces: Int) throws {
        let parsed = try #require(NumberInput.parse(text))
        #expect(parsed.value == Decimal(string: value))
        #expect(parsed.decimalPlaces == decimalPlaces)
        #expect(NumberInput.decimal(text) == Decimal(string: value))
    }

    @Test(arguments: ["1.23,45", "1.2.3", "1,2.3", "1,2,3", ".", "12a", "", ",", "1,,5", ",500", "1234,567", "0,500", "0.001", "012,345", "-1", "1e3", "١٢٣"])
    func invalidInput(_ text: String) {
        #expect(NumberInput.parse(text) == nil)
        #expect(NumberInput.decimal(text) == nil)
    }

    @Test(arguments: ["\u{00A0}", "\u{202F}", "\u{2009}", " ", "\t"])
    func everyKindOfWhitespaceIsRemoved(_ space: String) {
        #expect(NumberInput.decimal("1\(space)299,90") == Decimal(string: "1299.90"))
        #expect(NumberInput.decimal("\(space)12\(space)345\(space)") == 12345)
    }

    @Test func theDecimalPlacesAreCountedAsTyped() {
        #expect(Validation.price("1,50") == nil)
        #expect(Validation.price("1,500") == nil)
        #expect(NumberInput.decimal("1,500") == 1500)
        #expect(Validation.price("1,5000") == "Use at most 2 decimal places.")
        #expect(Validation.price("1.500,000") == "Use at most 2 decimal places.")
    }

    @Test func editableNumbersAreReadBackInEveryLocale() {
        let values: [Decimal] = [0, 5, Decimal(string: "0.5")!, Decimal(string: "1299.9")!, 1500, Decimal(string: "9999999999.99")!]
        for identifier in ["en_US", "de_DE", "cs_CZ", "fr_FR", "ar_SA", "hi_IN", "fa_IR"] {
            let locale = Locale(identifier: identifier)
            for value in values {
                let text = AppFormat.editableNumber(value, locale: locale)
                #expect(NumberInput.decimal(text) == value, "\(value) in \(identifier) is edited as \(text)")
            }
        }
    }
}
