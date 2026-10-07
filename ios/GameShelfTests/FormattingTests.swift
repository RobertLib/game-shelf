import Foundation
import Testing
@testable import GameShelf

@Suite("Locale-aware formatting")
struct FormattingTests {
    private let english = Locale(identifier: "en_US")
    private let german = Locale(identifier: "de_DE")

    /// ICU separates currency and amount with (narrow) no-break spaces; compare with plain spaces.
    private func normalized(_ text: String) -> String {
        text.replacingOccurrences(of: "\u{00A0}", with: " ").replacingOccurrences(of: "\u{202F}", with: " ")
    }

    @Test func pricesUseTheGamesCurrencyAndTheDeviceLocale() {
        #expect(normalized(AppFormat.currency(1500, code: "CZK", locale: english)) == "CZK 1,500")
        #expect(normalized(AppFormat.currency(Decimal(string: "1299.9")!, code: "CZK", locale: english)) == "CZK 1,299.90")
        #expect(normalized(AppFormat.currency(35, code: "EUR", locale: english)) == "€35")
        #expect(normalized(AppFormat.currency(1500, code: "CZK", locale: german)) == "1.500 CZK")
    }

    @Test func editableNumbersHaveNoGrouping() {
        #expect(AppFormat.editableNumber(Decimal(string: "1299.9")!, locale: english) == "1299.9")
        #expect(AppFormat.editableNumber(Decimal(string: "1299.9")!, locale: german) == "1299,9")
    }

    @Test func dates() {
        let date = LocalDate("2024-05-17")!
        #expect(AppFormat.date(date, locale: english) == "May 17, 2024")
    }

    @Test func ranges() {
        #expect(AppFormat.range(from: "1990", to: "1999") == "1990–1999")
        #expect(AppFormat.range(from: "1990", to: nil) == "from 1990")
        #expect(AppFormat.range(from: nil, to: "1999") == "to 1999")
    }
}
