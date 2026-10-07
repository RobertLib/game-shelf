import Foundation
import Observation

/// Distinct values in the collection, used for filter options and form suggestions.
@Observable
@MainActor
final class FacetsStore {
    private(set) var facets: GameFacets?

    @ObservationIgnored private let service: any GameService

    init(service: any GameService, facets: GameFacets? = nil) {
        self.service = service
        self.facets = facets
    }

    /// Facets are auxiliary; a failed reload keeps the previous values.
    func reload() async {
        do {
            facets = try await service.facets()
        } catch {
            debugLog("Facets failed to load: \(error)")
        }
    }

    var genres: [String] { facets?.genres.map(\.value) ?? [] }
    var publishers: [String] { facets?.publishers.map(\.value) ?? [] }
    var developers: [String] { facets?.developers.map(\.value) ?? [] }
    var storageLocations: [String] { facets?.storageLocations.map(\.value) ?? [] }
}

/// A change made on the detail or form screen that the list should reflect.
enum GameChange: Sendable {
    case created(Game)
    case updated(Game)
    case deleted(Game.ID)
}
