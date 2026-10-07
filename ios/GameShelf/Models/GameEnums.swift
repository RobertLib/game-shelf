import Foundation

// Enums without an OTHER value get an explicit `unknown` case for values from a
// newer API. It is excluded from `allCases`, so it never shows up in pickers.

enum CollectionStatus: String, APIEnum {
    case owned = "OWNED"
    case wishlist = "WISHLIST"
    case preordered = "PREORDERED"
    case lent = "LENT"
    case forSale = "FOR_SALE"
    case sold = "SOLD"
    case unknown = "UNKNOWN"

    static let allCases: [CollectionStatus] = [.owned, .wishlist, .preordered, .lent, .forSale, .sold]
    static var fallback: CollectionStatus { .unknown }

    var label: String {
        switch self {
        case .owned: "Owned"
        case .wishlist: "Wishlist"
        case .preordered: "Pre-ordered"
        case .lent: "Lent out"
        case .forSale: "For sale"
        case .sold: "Sold"
        case .unknown: "Unknown status"
        }
    }
}

enum GameFormat: String, APIEnum {
    case physical = "PHYSICAL"
    case digital = "DIGITAL"
    case unknown = "UNKNOWN"

    static let allCases: [GameFormat] = [.physical, .digital]
    static var fallback: GameFormat { .unknown }

    var label: String {
        switch self {
        case .physical: "Physical"
        case .digital: "Digital"
        case .unknown: "Unknown format"
        }
    }
}

enum Region: String, APIEnum {
    case pal = "PAL"
    case ntscU = "NTSC_U"
    case ntscJ = "NTSC_J"
    case regionFree = "REGION_FREE"
    case other = "OTHER"

    static var fallback: Region { .other }

    var label: String {
        switch self {
        case .pal: "PAL"
        case .ntscU: "NTSC-U (Americas)"
        case .ntscJ: "NTSC-J (Japan)"
        case .regionFree: "Region free"
        case .other: "Other"
        }
    }

    /// Short form for the list row's secondary line.
    var shortLabel: String {
        switch self {
        case .pal: "PAL"
        case .ntscU: "NTSC-U"
        case .ntscJ: "NTSC-J"
        case .regionFree: "Region free"
        case .other: "Other region"
        }
    }
}

enum Completeness: String, APIEnum {
    case sealed = "SEALED"
    case cib = "CIB"
    case gameAndBox = "GAME_AND_BOX"
    case gameAndManual = "GAME_AND_MANUAL"
    case loose = "LOOSE"
    case boxOnly = "BOX_ONLY"
    case unknown = "UNKNOWN"

    static let allCases: [Completeness] = [.sealed, .cib, .gameAndBox, .gameAndManual, .loose, .boxOnly]
    static var fallback: Completeness { .unknown }

    var label: String {
        switch self {
        case .sealed: "Sealed"
        case .cib: "Complete in box (CIB)"
        case .gameAndBox: "Game & box"
        case .gameAndManual: "Game & manual"
        case .loose: "Loose"
        case .boxOnly: "Box only"
        case .unknown: "Unknown completeness"
        }
    }
}

enum Condition: String, APIEnum {
    case mint = "MINT"
    case nearMint = "NEAR_MINT"
    case veryGood = "VERY_GOOD"
    case good = "GOOD"
    case fair = "FAIR"
    case poor = "POOR"
    case unknown = "UNKNOWN"

    static let allCases: [Condition] = [.mint, .nearMint, .veryGood, .good, .fair, .poor]
    static var fallback: Condition { .unknown }

    var label: String {
        switch self {
        case .mint: "Mint"
        case .nearMint: "Near mint"
        case .veryGood: "Very good"
        case .good: "Good"
        case .fair: "Fair"
        case .poor: "Poor"
        case .unknown: "Unknown condition"
        }
    }
}

enum PlayStatus: String, APIEnum {
    case unplayed = "UNPLAYED"
    case playing = "PLAYING"
    case completed = "COMPLETED"
    case abandoned = "ABANDONED"
    case unknown = "UNKNOWN"

    static let allCases: [PlayStatus] = [.unplayed, .playing, .completed, .abandoned]
    static var fallback: PlayStatus { .unknown }

    var label: String {
        switch self {
        case .unplayed: "Unplayed"
        case .playing: "Playing"
        case .completed: "Completed"
        case .abandoned: "Abandoned"
        case .unknown: "Unknown play status"
        }
    }
}

enum GameSortField: String, APIEnum {
    case title
    case platform
    case releaseYear
    case purchaseDate
    case purchasePrice
    case estimatedValue
    case rating
    case createdAt
    case updatedAt

    static var fallback: GameSortField { .title }

    var label: String {
        switch self {
        case .title: "Title"
        case .platform: "Platform"
        case .releaseYear: "Release year"
        case .purchaseDate: "Purchase date"
        case .purchasePrice: "Purchase price"
        case .estimatedValue: "Estimated value"
        case .rating: "Rating"
        case .createdAt: "Date added"
        case .updatedAt: "Last modified"
        }
    }

    /// The order that feels natural when the user picks this field.
    var defaultOrder: SortOrder {
        switch self {
        case .title, .platform: .asc
        default: .desc
        }
    }
}

enum SortOrder: String, APIEnum {
    case asc
    case desc

    static var fallback: SortOrder { .asc }

    var label: String {
        switch self {
        case .asc: "Ascending"
        case .desc: "Descending"
        }
    }
}
