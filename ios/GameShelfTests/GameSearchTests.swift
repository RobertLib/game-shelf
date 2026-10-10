import Foundation
import Testing
@testable import GameShelf

private let marioKart8Deluxe = GameSearchResult(
    igdbId: 26758,
    title: "Mario Kart 8 Deluxe",
    platforms: [.switch],
    genre: "Racing",
    developer: "Nintendo EPD",
    publisher: "Nintendo",
    releaseYear: 2017,
    coverImageUrl: "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg"
)

private let marioAndSonic = GameSearchResult(
    igdbId: 1018,
    title: "Mario & Sonic at the Olympic Games",
    platforms: [.wii, .nintendoDS],
    genre: "Sport",
    developer: "Sega Sports R&D",
    publisher: "Sega",
    releaseYear: 2007,
    coverImageUrl: nil
)

private let marioResults = GameSearchResponse(items: [marioKart8Deluxe, marioAndSonic], sources: ["IGDB"])
private let marioKartResults = GameSearchResponse(items: [marioKart8Deluxe], sources: ["IGDB"])

/// Holds answers back until the test opens it, and lets the test wait until one is held.
private actor Gate {
    private var isOpen = false
    private var hasArrived = false
    private var held: [CheckedContinuation<Void, Never>] = []
    private var arrivalWaiters: [CheckedContinuation<Void, Never>] = []

    func pass() async {
        hasArrived = true
        arrivalWaiters.forEach { $0.resume() }
        arrivalWaiters = []
        guard !isOpen else { return }
        await withCheckedContinuation { held.append($0) }
    }

    func waitForArrival() async {
        guard !hasArrived else { return }
        await withCheckedContinuation { arrivalWaiters.append($0) }
    }

    func open() {
        isOpen = true
        held.forEach { $0.resume() }
        held = []
    }
}

/// Answers from `results` by query (other queries find nothing); a query with a gate waits for it.
private actor FakeGameSearchService: GameSearchService {
    struct Search: Equatable {
        var query: String
        var platform: Platform?
    }

    private var results: [String: Result<GameSearchResponse, any Error>]
    private let gates: [String: Gate]
    private(set) var searches: [Search] = []

    init(_ results: [String: Result<GameSearchResponse, any Error>] = [:], gates: [String: Gate] = [:]) {
        self.results = results
        self.gates = gates
    }

    func setResult(_ result: Result<GameSearchResponse, any Error>, for query: String) {
        results[query] = result
    }

    var queries: [String] { searches.map(\.query) }

    func search(_ query: String, platform: Platform?) async throws -> GameSearchResponse {
        searches.append(Search(query: query, platform: platform))
        await gates[query]?.pass()
        return try results[query]?.get() ?? GameSearchResponse(items: [], sources: ["IGDB"])
    }
}

@MainActor
@Suite("Searching the game database")
struct GameSearchViewModelTests {
    @Test func aTextShorterThanTwoCharactersIsNotSearched() async {
        let service = FakeGameSearchService()
        let model = GameSearchViewModel(query: " m ", platform: nil)

        await model.search(using: service)

        #expect(await service.searches.isEmpty)
        #expect(model.content == .prompt)
    }

    @Test func findsGamesWithThePlatformChosenInTheForm() async {
        let service = FakeGameSearchService(["mario kart": .success(marioKartResults)])
        let model = GameSearchViewModel(query: "  mario kart ", platform: .switch)

        await model.search(using: service)

        #expect(await service.searches == [.init(query: "mario kart", platform: .switch)])
        #expect(model.content == .results([marioKart8Deluxe], sources: ["IGDB"], isRefreshing: false))
    }

    @Test func searchesWithoutAPlatformWhenTheFormHasNone() async {
        let service = FakeGameSearchService()
        let model = GameSearchViewModel(query: "mario", platform: nil)

        await model.search(using: service)

        #expect(await service.searches == [.init(query: "mario", platform: nil)])
    }

    @Test func aLongTextIsSearchedByItsBeginning() {
        let model = GameSearchViewModel(query: "  " + String(repeating: "a", count: 99) + " bcd", platform: nil)
        #expect(model.searchTerm == String(repeating: "a", count: 99))
    }

    @Test func theSearchTermIsCutAtACodePointLikeTheAPICountsIt() {
        // 98 + 2 emoji = 100 code points (102 UTF-16 units); the third emoji is cut off whole.
        let model = GameSearchViewModel(query: String(repeating: "a", count: 98) + "😀😀😀", platform: nil)
        #expect(model.searchTerm == String(repeating: "a", count: 98) + "😀😀")
        #expect(model.searchTerm.unicodeScalars.count == 100)

        // A flag is 2 code points: 99 letters leave room for its first half only.
        let flag = GameSearchViewModel(query: String(repeating: "a", count: 99) + "🇨🇿", platform: nil)
        #expect(flag.searchTerm.unicodeScalars.count == 100)
        #expect(flag.searchTerm.unicodeScalars.last == "\u{1F1E8}")
    }

    @Test func twoCodePointsAreEnoughToSearch() {
        #expect(GameSearchViewModel(query: "🇨🇿", platform: nil).content == .searching)
        #expect(GameSearchViewModel(query: "😀", platform: nil).content == .prompt)
    }

    @Test func reportsWhenNothingIsFound() async {
        let model = GameSearchViewModel(query: "qwertz", platform: nil)

        await model.search(using: FakeGameSearchService())

        #expect(model.content == .noResults(query: "qwertz"))
    }

    @Test func aFailedSearchCanBeRetried() async {
        let unavailable = APIError.server(statusCode: 503, code: .lookupUnavailable, details: [])
        let service = FakeGameSearchService(["mario": .failure(unavailable)])
        let model = GameSearchViewModel(query: "mario", platform: nil)

        await model.search(using: service)
        #expect(model.content == .failed("The game database isn't available right now. Try again later."))

        await service.setResult(.success(marioResults), for: "mario")
        await model.retry(using: service)

        #expect(model.content == .results(marioResults.items, sources: ["IGDB"], isRefreshing: false))
        #expect(await service.queries == ["mario", "mario"])
    }

    @Test func anOfflineSearchShowsTheConnectionError() async {
        let service = FakeGameSearchService(["mario": .failure(APIError.network(.notConnectedToInternet))])
        let model = GameSearchViewModel(query: "mario", platform: nil)

        await model.search(using: service)

        #expect(model.content == .failed(ErrorMessage.network))
    }

    @Test func showsProgressUntilTheFirstAnswerArrives() async {
        let gate = Gate()
        let service = FakeGameSearchService(["mario": .success(marioResults)], gates: ["mario": gate])
        let model = GameSearchViewModel(query: "mario", platform: nil)

        let search = Task { await model.search(using: service) }
        await gate.waitForArrival()
        #expect(model.content == .searching)

        await gate.open()
        await search.value
        #expect(model.content == .results(marioResults.items, sources: ["IGDB"], isRefreshing: false))
    }

    @Test func keepsTheResultsOnScreenWhileANewerSearchRuns() async {
        let gate = Gate()
        let service = FakeGameSearchService(
            ["mario": .success(marioResults), "mario kart": .success(marioKartResults)],
            gates: ["mario kart": gate]
        )
        let model = GameSearchViewModel(query: "mario", platform: nil)
        await model.search(using: service)

        model.query = "mario kart"
        #expect(model.content == .results(marioResults.items, sources: ["IGDB"], isRefreshing: true))
        let search = Task { await model.search(using: service) }
        await gate.waitForArrival()
        #expect(model.content == .results(marioResults.items, sources: ["IGDB"], isRefreshing: true))

        await gate.open()
        await search.value
        #expect(model.content == .results([marioKart8Deluxe], sources: ["IGDB"], isRefreshing: false))
    }

    @Test func anAnswerThatComesLateIsIgnored() async {
        let gate = Gate()
        let service = FakeGameSearchService(
            ["mario": .success(marioResults), "mario kart": .success(marioKartResults)],
            gates: ["mario": gate]
        )
        let model = GameSearchViewModel(query: "mario", platform: nil)
        let olderSearch = Task { await model.search(using: service) }
        await gate.waitForArrival()

        model.query = "mario kart"
        await model.search(using: service)
        await gate.open()
        await olderSearch.value

        #expect(model.content == .results([marioKart8Deluxe], sources: ["IGDB"], isRefreshing: false))
        #expect(!model.isLoading)
    }

    @Test func theAnswerOfACancelledSearchIsIgnored() async {
        let gate = Gate()
        let service = FakeGameSearchService(
            ["mario": .success(marioResults), "mario kart": .success(marioKartResults)],
            gates: ["mario kart": gate]
        )
        let model = GameSearchViewModel(query: "mario", platform: nil)
        await model.search(using: service)

        model.query = "mario kart"
        let search = Task { await model.search(using: service) }
        await gate.waitForArrival()
        search.cancel()
        await gate.open()
        await search.value

        #expect(!model.isLoading)
        #expect(model.content == .results(marioResults.items, sources: ["IGDB"], isRefreshing: true))
    }

    @Test func clearsTheResultsWhenTheTextGetsTooShort() async {
        let service = FakeGameSearchService(["mario": .success(marioResults)])
        let model = GameSearchViewModel(query: "mario", platform: nil)
        await model.search(using: service)

        model.query = "m"
        await model.search(using: service)
        #expect(model.content == .prompt)

        model.query = "ma"
        #expect(model.content == .searching)
    }

    @Test(.timeLimit(.minutes(1)))
    func searchesTheTextTheScreenOpenedWithRightAway() async {
        let service = FakeGameSearchService()
        let model = GameSearchViewModel(query: "Mario Kart", platform: nil, debounce: .seconds(3600))

        await model.searchTermChanged(using: service)

        #expect(await service.queries == ["Mario Kart"])
    }

    @Test(.timeLimit(.minutes(1)))
    func aChangedTextIsSearchedOnlyOnceTypingPauses() async {
        let service = FakeGameSearchService()
        let model = GameSearchViewModel(query: "mario", platform: nil, debounce: .seconds(3600))
        await model.searchTermChanged(using: service)

        // Typing on cancels the task of the previous text (`.task(id:)`) before the pause is over.
        model.query = "mario k"
        let typing = Task { await model.searchTermChanged(using: service) }
        typing.cancel()
        await typing.value
        #expect(await service.queries == ["mario"])

        // A text too short clears the results at once.
        model.query = "m"
        await model.searchTermChanged(using: service)
        #expect(model.content == .prompt)
    }

    @Test func aChangedTextIsSearchedAfterThePause() async {
        let service = FakeGameSearchService()
        let model = GameSearchViewModel(query: "mario", platform: nil, debounce: .milliseconds(1))
        await model.searchTermChanged(using: service)

        model.query = "mario kart"
        await model.searchTermChanged(using: service)

        #expect(await service.queries == ["mario", "mario kart"])
    }

    @Test func aTextWhoseAnswerIsShownIsNotSearchedAgain() async {
        let service = FakeGameSearchService(["mario": .success(marioResults)])
        let model = GameSearchViewModel(query: "mario", platform: nil)
        await model.search(using: service)

        model.query = "mario "
        await model.search(using: service)

        #expect(await service.queries == ["mario"])
    }
}

@Suite("Platform of a picked game")
struct PickedPlatformTests {
    @Test func aPlatformChosenInTheFormStays() {
        #expect(PickedPlatform(for: marioKart8Deluxe, formPlatform: .switch2) == .unchanged)
        #expect(PickedPlatform(for: marioAndSonic, formPlatform: .wii) == .unchanged)
    }

    @Test func theOnlyPlatformOfTheGameIsTaken() {
        #expect(PickedPlatform(for: marioKart8Deluxe, formPlatform: nil) == .platform(.switch))
    }

    @Test func severalPlatformsAreOfferedInTheirOrder() {
        #expect(PickedPlatform(for: marioAndSonic, formPlatform: nil) == .ask([.wii, .nintendoDS]))
    }

    @Test func aGameWithoutPlatformsLeavesPlatformEmpty() {
        var game = marioKart8Deluxe
        game.platforms = []
        #expect(PickedPlatform(for: game, formPlatform: nil) == .unchanged)
    }
}

@Suite("Search results")
struct GameSearchResultTests {
    @Test func describesTheReleaseYearAndPlatforms() {
        var game = marioKart8Deluxe
        game.platforms = [.switch, .wiiU]
        #expect(game.yearAndPlatforms == "2017 · Nintendo Switch, Wii U")

        game.platforms = [.nes, .wii, .wiiU, .switch, .gameBoyAdvance]
        #expect(game.yearAndPlatforms == "2017 · NES, Wii, Wii U +2")

        game.releaseYear = nil
        game.platforms = [.switch]
        #expect(game.yearAndPlatforms == "Nintendo Switch")

        game.platforms = []
        #expect(game.yearAndPlatforms.isEmpty)
    }

    @Test func decodesAResponseAndDropsPlatformsTheAppDoesNotKnow() throws {
        let json = Data("""
        {"items": [
          {"igdbId": 26758, "title": "Mario Kart 8 Deluxe", "platforms": ["SWITCH", "SWITCH_9", "WII_U"],
           "genre": "Racing", "developer": "Nintendo EPD", "publisher": "Nintendo", "releaseYear": 2017,
           "coverImageUrl": "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg"},
          {"igdbId": 1, "title": "Unknown", "platforms": [], "genre": null, "developer": null,
           "publisher": null, "releaseYear": null, "coverImageUrl": null}
        ], "sources": ["IGDB"]}
        """.utf8)

        let response = try JSONDecoder.api().decode(GameSearchResponse.self, from: json)

        #expect(response.sources == ["IGDB"])
        #expect(response.items.map(\.id) == [26758, 1])
        #expect(response.items[0].platforms == [.switch, .wiiU])
        #expect(response.items[0].coverURL == URL(string: "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg"))
        #expect(response.items[1] == GameSearchResult(
            igdbId: 1, title: "Unknown", platforms: [], genre: nil, developer: nil, publisher: nil,
            releaseYear: nil, coverImageUrl: nil
        ))
    }
}

@Suite("Game search requests")
struct GameSearchServiceTests {
    private func makeService(
        status: Int,
        body: Data,
        expectedQuery: [URLQueryItem] = [URLQueryItem(name: "q", value: "mario kart")]
    ) -> RemoteGameSearchService {
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            #expect(request.httpMethod == "GET")
            #expect(request.url?.path == "/api/v1/lookup/games")
            #expect(request.url.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false) }?.queryItems == expectedQuery)
            #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer token")
            return (status, body)
        }
        let storage = InMemorySessionStorage(Fixtures.storedSession(access: "token"))
        return RemoteGameSearchService(api: APIClient(
            baseURL: URL(string: "https://\(host)/api/v1/")!,
            tokens: TokenManager(storage: storage, session: storage.load()),
            session: session
        ))
    }

    private let body = Data("""
    {"items": [{"igdbId": 26758, "title": "Mario Kart 8 Deluxe", "platforms": ["SWITCH"], "genre": "Racing",
                "developer": "Nintendo EPD", "publisher": "Nintendo", "releaseYear": 2017,
                "coverImageUrl": "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg"}],
     "sources": ["IGDB"]}
    """.utf8)

    @Test func sendsTheTextAndThePlatform() async throws {
        let service = makeService(status: 200, body: body, expectedQuery: [
            URLQueryItem(name: "q", value: "mario kart"),
            URLQueryItem(name: "platform", value: "SWITCH"),
        ])
        let response = try await service.search("mario kart", platform: .switch)
        #expect(response == marioKartResults)
    }

    @Test func leavesThePlatformOutWhenThereIsNone() async throws {
        let response = try await makeService(status: 200, body: body).search("mario kart", platform: nil)
        #expect(response.items.count == 1)
    }

    @Test func anUnavailableDatabaseIsReported() async throws {
        let body = Fixtures.errorJSON(status: 503, code: "LOOKUP_UNAVAILABLE")
        let error = await #expect(throws: APIError.self) {
            try await makeService(status: 503, body: body).search("mario kart", platform: nil)
        }
        #expect(ErrorMessage.message(for: try #require(error)) == "The game database isn't available right now. Try again later.")
    }
}

@Suite("Filling the form from the database search")
struct GameDraftSearchFillTests {
    @Test func replacesTheDetailsTheDatabaseKnows() {
        var draft = GameDraft()
        draft.title = "mario kart"
        draft.genre = "Kart racing"
        draft.developer = "Nintendo"
        draft.publisher = "Nintendo of Europe"
        draft.releaseYear = "2018"
        draft.coverImageUrl = "https://example.com/mk.jpg"
        draft.edition = "Limited Edition"
        draft.region = .pal
        draft.barcode = "045496420055"
        draft.status = .wishlist
        draft.notes = "A gift"

        draft.fill(fromSearch: marioKart8Deluxe, platform: .switch)

        #expect(draft.title == "Mario Kart 8 Deluxe")
        #expect(draft.platform == .switch)
        #expect(draft.genre == "Racing")
        #expect(draft.developer == "Nintendo EPD")
        #expect(draft.publisher == "Nintendo")
        #expect(draft.releaseYear == "2017")
        #expect(draft.coverImageUrl == marioKart8Deluxe.coverImageUrl)
        // No other field changes.
        #expect(draft.edition == "Limited Edition")
        #expect(draft.region == .pal)
        #expect(draft.barcode == "045496420055")
        #expect(draft.status == .wishlist)
        #expect(draft.notes == "A gift")
    }

    @Test func keepsTheFieldsTheDatabaseDoesNotKnow() {
        var draft = GameDraft()
        draft.platform = .ps2
        draft.genre = "Racing"
        draft.developer = "Studio"
        draft.publisher = "Company"
        draft.releaseYear = "2001"
        draft.coverImageUrl = "https://example.com/cover.jpg"
        let game = GameSearchResult(
            igdbId: 1, title: "Unknown Racer", platforms: [], genre: nil, developer: " ", publisher: nil,
            releaseYear: nil, coverImageUrl: nil
        )

        draft.fill(fromSearch: game, platform: nil)

        #expect(draft.title == "Unknown Racer")
        #expect(draft.platform == .ps2)
        #expect(draft.genre == "Racing")
        #expect(draft.developer == "Studio")
        #expect(draft.publisher == "Company")
        #expect(draft.releaseYear == "2001")
        #expect(draft.coverImageUrl == "https://example.com/cover.jpg")
    }
}

/// Answers every barcode with a game once the test opens the gate.
private struct GatedLookupService: BarcodeLookupService {
    let gate: Gate

    func lookup(barcode: String) async throws -> BarcodeLookup? {
        await gate.pass()
        return BarcodeLookup(
            barcode: barcode, title: "Mario Kart 8 Deluxe", platform: .switch, region: .pal,
            edition: "Limited Edition", genre: "Racing", developer: "Nintendo EPD", publisher: "Nintendo",
            releaseYear: 2017, coverImageUrl: nil, sources: ["UPCitemdb", "IGDB"]
        )
    }
}

@MainActor
@Suite("Database search in the game form")
struct GameFormSearchTests {
    /// A repository of an activated user holding `games`.
    private func makeRepository(_ games: [Game] = []) async throws -> GameRepository {
        let server = FakeGameServer()
        for game in games {
            await server.seed(game)
        }
        let repository = GameRepository(store: try GameStore(url: nil), status: SyncStatus())
        let sync = SyncEngine(repository: repository, api: server)
        repository.onLocalChange = nil
        await sync.activate(ownerID: "user-1")
        try await sync.syncNow()
        return repository
    }

    @Test func aPickedGameFillsTheFormAndShowsTheSource() async throws {
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository())
        model.draft.title = "mk8"
        model.draft.notes = "A gift"

        model.fill(fromSearch: GameSearchPick(game: marioKart8Deluxe, platform: .switch, sources: ["IGDB"]))

        #expect(model.draft.title == "Mario Kart 8 Deluxe")
        #expect(model.draft.platform == .switch)
        #expect(model.draft.developer == "Nintendo EPD")
        #expect(model.draft.notes == "A gift")
        #expect(model.lookupState == .found(sources: ["IGDB"]))
        #expect(model.duplicate == nil)
        #expect(model.hasChanges)
    }

    @Test func warnsWhenTheCollectionHasTheGameOnThatPlatform() async throws {
        let owned = Fixtures.game(" mario kart 8 DELUXE ", .switch)
        let onAnotherPlatform = Fixtures.game("Mario Kart 8 Deluxe", .switch2)
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository([onAnotherPlatform, owned]))

        model.fill(fromSearch: GameSearchPick(game: marioKart8Deluxe, platform: .switch, sources: ["IGDB"]))

        #expect(model.duplicate?.id == owned.id)
    }

    @Test func withoutAPlatformThereIsNoDuplicate() async throws {
        let owned = Fixtures.game("Mario & Sonic at the Olympic Games", .wii)
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository([owned]))

        model.fill(fromSearch: GameSearchPick(game: marioAndSonic, platform: nil, sources: ["IGDB"]))

        #expect(model.draft.platform == nil)
        #expect(model.duplicate == nil)
    }

    @Test func theEditedGameIsNotItsOwnDuplicate() async throws {
        let game = Fixtures.game("MK8 DX", .switch)
        let model = GameFormViewModel(mode: .edit(game), repository: try await makeRepository([game]))

        model.fill(fromSearch: GameSearchPick(game: marioKart8Deluxe, platform: nil, sources: ["IGDB"]))

        #expect(model.draft.title == "Mario Kart 8 Deluxe")
        #expect(model.draft.platform == .switch)
        #expect(model.duplicate == nil)
    }

    @Test func aBarcodeLookupInProgressIsIgnoredOnceAGameIsPicked() async throws {
        let gate = Gate()
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository())
        let lookup = Task { await model.scanned("045496420055", using: GatedLookupService(gate: gate)) }
        await gate.waitForArrival()
        #expect(model.lookupState == .loading)

        model.fill(fromSearch: GameSearchPick(game: marioAndSonic, platform: .wii, sources: ["IGDB"]))
        await gate.open()
        await lookup.value

        #expect(model.lookupState == .found(sources: ["IGDB"]))
        #expect(model.draft.title == "Mario & Sonic at the Olympic Games")
        #expect(model.draft.platform == .wii)
        #expect(model.draft.edition.isEmpty)
        #expect(model.draft.region == nil)
    }
}
