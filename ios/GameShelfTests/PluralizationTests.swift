import Foundation
import Testing
@testable import GameShelf

@Suite("Pluralization")
struct PluralizationTests {
    private let english = Locale(identifier: "en_US")

    @Test(arguments: [
        (0, "0 games"),
        (1, "1 game"),
        (2, "2 games"),
        (5, "5 games"),
        (21, "21 games"),
        (132, "132 games"),
    ])
    func gameCounts(count: Int, expected: String) {
        #expect(Pluralization.games(count, locale: english) == expected)
    }

    @Test func countIsFormattedWithTheLocale() {
        #expect(Pluralization.games(1234, locale: english) == "1,234 games")
        #expect(Pluralization.games(1234, locale: Locale(identifier: "de_DE")) == "1.234 games")
    }

    @Test func customNouns() {
        #expect(Pluralization.format(1, one: "copy", other: "copies", locale: english) == "1 copy")
        #expect(Pluralization.format(3, one: "copy", other: "copies", locale: english) == "3 copies")
    }
}
