import Foundation
import Observation

/// A game picked in the database search, to fill in the form with.
struct GameSearchPick: Equatable {
    var game: GameSearchResult
    /// Chosen for the user's copy; `nil` leaves the form's Platform as it is.
    var platform: Platform?
    /// Attribution of the search the game comes from.
    var sources: [String]
}

/// Which platform a game picked in the search gets.
enum PickedPlatform: Equatable {
    /// The form's Platform stays as it is (empty when it has none).
    case unchanged
    case platform(Platform)
    /// The game came out on several platforms: ask which one the user's copy is for.
    case ask([Platform])

    /// A platform chosen in the form stays; otherwise the game's only platform, if it has exactly one.
    init(for game: GameSearchResult, formPlatform: Platform?) {
        if formPlatform != nil {
            self = .unchanged
            return
        }
        switch game.platforms.count {
        case 0: self = .unchanged
        case 1: self = .platform(game.platforms[0])
        default: self = .ask(game.platforms)
        }
    }
}

/// "Search game database": finds games by the title typed in, so that the form can be filled in from
/// one. Searching needs a connection; nothing is saved.
@Observable
@MainActor
final class GameSearchViewModel {
    /// What the screen shows.
    enum Content: Equatable {
        /// Fewer than ``GameSearchViewModel/minimumLength`` characters typed.
        case prompt
        /// Searching with nothing to show yet.
        case searching
        /// `isRefreshing`: a newer search is on its way to replace these results.
        case results([GameSearchResult], sources: [String], isRefreshing: Bool)
        case noResults(query: String)
        case failed(String)
    }

    /// Lengths in code points, as the API counts them.
    static let minimumLength = 2
    /// The API's limit; a longer text is searched by its beginning.
    static let maximumLength = 100

    var query: String
    /// Chosen in the form; the games on it are listed first.
    let platform: Platform?
    private(set) var isLoading = false
    /// The answer to the latest search and the term it is for.
    private var answer: (term: String, outcome: Outcome)?

    private enum Outcome {
        case found(GameSearchResponse)
        case failed(String)
    }

    @ObservationIgnored private let debounce: Duration
    @ObservationIgnored private var hasSearchedInitialQuery = false
    /// Identifies the latest search, so that an older one answering late changes nothing.
    @ObservationIgnored private var generation = 0

    init(query: String, platform: Platform?, debounce: Duration = .milliseconds(400)) {
        self.query = query
        self.platform = platform
        self.debounce = debounce
    }

    /// The text searched for: ``query`` trimmed and cut to ``maximumLength`` code points (whole
    /// scalars, so an emoji is never cut in half).
    var searchTerm: String {
        String(query.trimmingCharacters(in: .whitespacesAndNewlines).unicodeScalars.prefix(Self.maximumLength))
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// The search term is long enough to be searched.
    private var canSearch: Bool { searchTerm.codePointCount >= Self.minimumLength }

    var content: Content {
        let term = searchTerm
        guard canSearch else { return .prompt }
        guard let answer else { return .searching }
        let isPending = isLoading || answer.term != term
        switch answer.outcome {
        case .found(let response) where !response.items.isEmpty:
            return .results(response.items, sources: response.sources, isRefreshing: isPending)
        case .found where !isPending:
            return .noResults(query: answer.term)
        case .failed(let message) where !isPending:
            return .failed(message)
        default:
            return .searching
        }
    }

    /// Runs whenever ``searchTerm`` changes, in a task that a newer term cancels (`.task(id:)`): searches
    /// once typing has paused. The text the screen opened with is searched right away.
    func searchTermChanged(using service: any GameSearchService) async {
        if hasSearchedInitialQuery, canSearch {
            do {
                try await Task.sleep(for: debounce)
            } catch {
                return
            }
        }
        hasSearchedInitialQuery = true
        await search(using: service)
    }

    /// Searches ``searchTerm`` unless its answer is shown already; a text too short clears the results.
    func search(using service: any GameSearchService) async {
        let term = searchTerm
        guard canSearch else {
            generation += 1
            isLoading = false
            answer = nil
            return
        }
        guard answer?.term != term else { return }
        await perform(term, using: service)
    }

    func retry(using service: any GameSearchService) async {
        let term = searchTerm
        guard canSearch else { return }
        await perform(term, using: service)
    }

    private func perform(_ term: String, using service: any GameSearchService) async {
        generation += 1
        let generation = generation
        isLoading = true
        let outcome: Outcome?
        do {
            outcome = .found(try await service.search(term, platform: platform))
        } catch {
            outcome = ErrorMessage.isCancellation(error) ? nil : .failed(ErrorMessage.message(for: error))
        }
        // A newer search has replaced this one.
        guard generation == self.generation else { return }
        isLoading = false
        if let outcome, !Task.isCancelled {
            answer = (term, outcome)
        }
    }
}
