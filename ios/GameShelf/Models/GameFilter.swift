import Foundation

/// Filter criteria of the collection list. Empty values mean "no restriction".
struct GameFilter: Hashable, Sendable {
    enum CoverFilter: String, CaseIterable, Hashable, Sendable {
        case any, withCover, withoutCover

        var label: String {
            switch self {
            case .any: "Any"
            case .withCover: "With cover"
            case .withoutCover: "Without cover"
            }
        }
    }

    var platforms: Set<Platform> = []
    var statuses: Set<CollectionStatus> = []
    var formats: Set<GameFormat> = []
    var regions: Set<Region> = []
    var completeness: Set<Completeness> = []
    var conditions: Set<Condition> = []
    var playStatuses: Set<PlayStatus> = []
    var genres: Set<String> = []
    var publisher = ""
    var developer = ""
    var storageLocation = ""
    var favoritesOnly = false
    var cover: CoverFilter = .any
    var releaseYearFrom: Int?
    var releaseYearTo: Int?
    var purchasePriceMin: Decimal?
    var purchasePriceMax: Decimal?
    var estimatedValueMin: Decimal?
    var estimatedValueMax: Decimal?
    var purchaseDateFrom: LocalDate?
    var purchaseDateTo: LocalDate?
    var ratingMin: Int?

    var isEmpty: Bool { self == GameFilter() }

    /// Number of active criteria; matches the number of chips.
    var activeCount: Int { chips.count }
}

// MARK: - Chips

struct FilterChip: Identifiable, Hashable, Sendable {
    enum Key: Hashable, Sendable {
        case platform(Platform)
        case status(CollectionStatus)
        case format(GameFormat)
        case region(Region)
        case completeness(Completeness)
        case condition(Condition)
        case playStatus(PlayStatus)
        case genre(String)
        case publisher, developer, storageLocation, favorite, cover
        case releaseYear, purchasePrice, estimatedValue, purchaseDate, rating
    }

    let key: Key
    let label: String

    var id: Key { key }
}

extension GameFilter {
    /// Active criteria in display order, each removable on its own.
    var chips: [FilterChip] {
        var chips: [FilterChip] = []

        func add(_ key: FilterChip.Key, _ label: String) {
            chips.append(FilterChip(key: key, label: label))
        }
        func addRange(_ key: FilterChip.Key, _ title: String, _ lower: String?, _ upper: String?) {
            guard lower != nil || upper != nil else { return }
            add(key, "\(title): \(AppFormat.range(from: lower, to: upper))")
        }
        func addText(_ key: FilterChip.Key, _ title: String, _ text: String) {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { add(key, "\(title): \(trimmed)") }
        }

        for platform in Platform.allCases where platforms.contains(platform) {
            add(.platform(platform), platform.label)
        }
        for status in CollectionStatus.allCases where statuses.contains(status) {
            add(.status(status), status.label)
        }
        for format in GameFormat.allCases where formats.contains(format) {
            add(.format(format), format.label)
        }
        for region in Region.allCases where regions.contains(region) {
            add(.region(region), "Region: \(region.shortLabel)")
        }
        for level in Completeness.allCases where completeness.contains(level) {
            add(.completeness(level), level.label)
        }
        for condition in Condition.allCases where conditions.contains(condition) {
            add(.condition(condition), "Condition: \(condition.label)")
        }
        for playStatus in PlayStatus.allCases where playStatuses.contains(playStatus) {
            add(.playStatus(playStatus), playStatus.label)
        }
        for genre in genres.sorted() {
            add(.genre(genre), "Genre: \(genre)")
        }
        addText(.publisher, "Publisher", publisher)
        addText(.developer, "Developer", developer)
        addText(.storageLocation, "Storage location", storageLocation)
        if favoritesOnly {
            add(.favorite, "Favorites")
        }
        if cover != .any {
            add(.cover, cover.label)
        }
        addRange(.releaseYear, "Release year", releaseYearFrom.map(String.init), releaseYearTo.map(String.init))
        addRange(.purchasePrice, "Purchase price", purchasePriceMin.map { AppFormat.number($0) }, purchasePriceMax.map { AppFormat.number($0) })
        addRange(.estimatedValue, "Estimated value", estimatedValueMin.map { AppFormat.number($0) }, estimatedValueMax.map { AppFormat.number($0) })
        addRange(.purchaseDate, "Purchase date", purchaseDateFrom.map { AppFormat.date($0) }, purchaseDateTo.map { AppFormat.date($0) })
        if let ratingMin {
            add(.rating, "Rating: \(ratingMin)+")
        }
        return chips
    }

    mutating func remove(_ key: FilterChip.Key) {
        switch key {
        case .platform(let platform): platforms.remove(platform)
        case .status(let status): statuses.remove(status)
        case .format(let format): formats.remove(format)
        case .region(let region): regions.remove(region)
        case .completeness(let level): completeness.remove(level)
        case .condition(let condition): conditions.remove(condition)
        case .playStatus(let playStatus): playStatuses.remove(playStatus)
        case .genre(let genre): genres.remove(genre)
        case .publisher: publisher = ""
        case .developer: developer = ""
        case .storageLocation: storageLocation = ""
        case .favorite: favoritesOnly = false
        case .cover: cover = .any
        case .releaseYear:
            releaseYearFrom = nil
            releaseYearTo = nil
        case .purchasePrice:
            purchasePriceMin = nil
            purchasePriceMax = nil
        case .estimatedValue:
            estimatedValueMin = nil
            estimatedValueMax = nil
        case .purchaseDate:
            purchaseDateFrom = nil
            purchaseDateTo = nil
        case .rating: ratingMin = nil
        }
    }
}

// MARK: - List query

/// Everything that determines the content of the collection list.
struct GameListQuery: Hashable, Sendable {
    var search = ""
    var filter = GameFilter()
    var sort: GameSortField = .title
    var order: SortOrder = .asc
    /// Show the results in one section per platform (a toggle in the sort menu).
    var groupByPlatform = true

    /// A search or filter narrows the results.
    var isFiltered: Bool {
        !search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !filter.isEmpty
    }
}
