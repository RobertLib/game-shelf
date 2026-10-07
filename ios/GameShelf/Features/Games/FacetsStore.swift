import Foundation
import Observation

/// Distinct values in the collection, used for filter options and form suggestions.
/// Computed from the local games and recomputed only after they change.
@Observable
@MainActor
final class FacetsStore {
    @ObservationIgnored private let repository: GameRepository
    @ObservationIgnored private var cache: (version: Int, facets: GameFacets)?

    init(repository: GameRepository) {
        self.repository = repository
    }

    var facets: GameFacets {
        let version = repository.version
        if let cache, cache.version == version {
            return cache.facets
        }
        let facets = GameFacets(games: repository.games)
        cache = (version, facets)
        return facets
    }

    var genres: [String] { facets.genres.map(\.value) }
    var publishers: [String] { facets.publishers.map(\.value) }
    var developers: [String] { facets.developers.map(\.value) }
    var storageLocations: [String] { facets.storageLocations.map(\.value) }
}
