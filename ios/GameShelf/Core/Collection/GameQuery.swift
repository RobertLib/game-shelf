import Foundation

// The collection list and its facets are computed on the device with the same semantics as
// `GET games` and `GET games/facets` of the API (docs/mobile-spec.md, "API basics").

extension GameListQuery {
    /// The games matching the search and the filters, in the requested order.
    func results(in games: some Sequence<Game>) -> [Game] {
        let words = search.split(whereSeparator: \.isWhitespace).map(String.init)
        let filter = filter.trimmed
        let matching = games.filter { filter.matches($0) && $0.matchesSearch(words) }
        let ordering = GameOrdering(field: sort, order: order)
        return matching.sorted(by: ordering.areInIncreasingOrder)
    }
}

// MARK: - Filtering

extension Game {
    /// Every word must be contained (ignoring case) in at least one of the searchable fields.
    func matchesSearch(_ words: [String]) -> Bool {
        words.allSatisfy { word in
            [title, edition, developer, publisher, genre, productCode, barcode, notes].contains { field in
                field?.containsIgnoringCase(word) == true
            }
        }
    }
}

extension GameFilter {
    func matches(_ game: Game) -> Bool {
        guard Self.matches(game.platform, platforms),
              Self.matches(game.status, statuses),
              Self.matches(game.format, formats),
              Self.matches(game.region, regions),
              Self.matches(game.completeness, completeness),
              Self.matches(game.condition, conditions),
              Self.matches(game.playStatus, playStatuses)
        else { return false }

        if !genres.isEmpty {
            guard let genre = game.genre,
                  genres.contains(where: { $0.caseInsensitiveCompare(genre) == .orderedSame })
            else { return false }
        }
        guard Self.contains(game.publisher, publisher),
              Self.contains(game.developer, developer),
              Self.contains(game.storageLocation, storageLocation)
        else { return false }

        if favoritesOnly, !game.favorite { return false }
        switch cover {
        case .any: break
        case .withCover: if game.coverImageUrl == nil { return false }
        case .withoutCover: if game.coverImageUrl != nil { return false }
        }

        return Self.isInRange(game.releaseYear, releaseYearFrom, releaseYearTo)
            && Self.isInRange(game.purchaseDate, purchaseDateFrom, purchaseDateTo)
            && Self.isInRange(game.purchasePrice, purchasePriceMin, purchasePriceMax)
            && Self.isInRange(game.estimatedValue, estimatedValueMin, estimatedValueMax)
            && Self.isInRange(game.rating, ratingMin, nil)
    }

    /// The text criteria without surrounding whitespace, as they are applied.
    var trimmed: GameFilter {
        var filter = self
        filter.publisher = publisher.trimmingCharacters(in: .whitespacesAndNewlines)
        filter.developer = developer.trimmingCharacters(in: .whitespacesAndNewlines)
        filter.storageLocation = storageLocation.trimmingCharacters(in: .whitespacesAndNewlines)
        return filter
    }

    /// Multi-value criteria match any selected value; a game without a value never matches.
    private static func matches<Value: Hashable>(_ value: Value?, _ selection: Set<Value>) -> Bool {
        guard !selection.isEmpty else { return true }
        return value.map(selection.contains) ?? false
    }

    private static func contains(_ value: String?, _ text: String) -> Bool {
        guard !text.isEmpty else { return true }
        return value?.containsIgnoringCase(text) == true
    }

    /// Inclusive range; a game without a value never matches a range.
    private static func isInRange<Value: Comparable>(_ value: Value?, _ lower: Value?, _ upper: Value?) -> Bool {
        guard lower != nil || upper != nil else { return true }
        guard let value else { return false }
        if let lower, value < lower { return false }
        if let upper, value > upper { return false }
        return true
    }
}

private extension String {
    func containsIgnoringCase(_ other: String) -> Bool {
        range(of: other, options: .caseInsensitive) != nil
    }
}

// MARK: - Sorting

/// Sort by one field (games without a value always last), then by title and id.
struct GameOrdering: Sendable {
    let field: GameSortField
    let order: SortOrder

    private static let platformRanks = Dictionary(
        uniqueKeysWithValues: Platform.allCases.enumerated().map { ($1, $0) }
    )

    func areInIncreasingOrder(_ lhs: Game, _ rhs: Game) -> Bool {
        let primary = comparePrimary(lhs, rhs)
        if primary != .orderedSame {
            return primary == .orderedAscending
        }
        if field != .title {
            let titles = lhs.title.localizedStandardCompare(rhs.title)
            if titles != .orderedSame {
                return titles == .orderedAscending
            }
        }
        return lhs.id < rhs.id
    }

    private func comparePrimary(_ lhs: Game, _ rhs: Game) -> ComparisonResult {
        switch field {
        case .title: directed(lhs.title.localizedStandardCompare(rhs.title))
        case .platform: compare(Self.platformRanks[lhs.platform], Self.platformRanks[rhs.platform])
        case .releaseYear: compare(lhs.releaseYear, rhs.releaseYear)
        case .purchaseDate: compare(lhs.purchaseDate, rhs.purchaseDate)
        case .purchasePrice: compare(lhs.purchasePrice, rhs.purchasePrice)
        case .estimatedValue: compare(lhs.estimatedValue, rhs.estimatedValue)
        case .rating: compare(lhs.rating, rhs.rating)
        case .createdAt: compare(lhs.createdAt, rhs.createdAt)
        case .updatedAt: compare(lhs.updatedAt, rhs.updatedAt)
        }
    }

    /// Values in the requested direction; missing values last in both directions.
    private func compare<Value: Comparable>(_ lhs: Value?, _ rhs: Value?) -> ComparisonResult {
        switch (lhs, rhs) {
        case (nil, nil): .orderedSame
        case (nil, _): .orderedDescending
        case (_, nil): .orderedAscending
        case let (lhs?, rhs?): directed(lhs < rhs ? .orderedAscending : lhs > rhs ? .orderedDescending : .orderedSame)
        }
    }

    private func directed(_ result: ComparisonResult) -> ComparisonResult {
        guard order == .desc else { return result }
        switch result {
        case .orderedAscending: return .orderedDescending
        case .orderedDescending: return .orderedAscending
        case .orderedSame: return .orderedSame
        }
    }
}

// MARK: - Facets

extension GameFacets {
    /// Distinct values of `games` with their counts, sorted by count (descending), then by value.
    /// Text values that differ only in letter case are one facet labelled with the most common spelling.
    init(games: some Collection<Game>) {
        self.init(
            totalItems: games.count,
            platforms: Self.facets(games.map(\.platform.rawValue)),
            statuses: Self.facets(games.map(\.status.rawValue)),
            genres: Self.facets(games.compactMap(\.genre), mergingCase: true),
            publishers: Self.facets(games.compactMap(\.publisher), mergingCase: true),
            developers: Self.facets(games.compactMap(\.developer), mergingCase: true),
            storageLocations: Self.facets(games.compactMap(\.storageLocation), mergingCase: true),
            releaseYearMin: games.compactMap(\.releaseYear).min(),
            releaseYearMax: games.compactMap(\.releaseYear).max()
        )
    }

    private static func facets(_ values: [String], mergingCase: Bool = false) -> [FacetValue] {
        var counts: [String: Int] = [:]
        for value in values {
            counts[value, default: 0] += 1
        }
        var facets = counts.map { FacetValue(value: $0.key, count: $0.value) }
        if mergingCase {
            facets = Dictionary(grouping: facets) { $0.value.lowercased() }.values.map { spellings in
                // The most common spelling wins; ties go to the one that sorts first.
                let label = spellings.min(by: isOrderedBefore)
                return FacetValue(value: label?.value ?? "", count: spellings.reduce(0) { $0 + $1.count })
            }
        }
        return facets.sorted(by: isOrderedBefore)
    }

    /// Count descending, then value (locale-aware, with a stable final tie-break).
    private static func isOrderedBefore(_ lhs: FacetValue, _ rhs: FacetValue) -> Bool {
        if lhs.count != rhs.count {
            return lhs.count > rhs.count
        }
        let values = lhs.value.localizedCompare(rhs.value)
        return values == .orderedSame ? lhs.value < rhs.value : values == .orderedAscending
    }
}
