import Foundation
import Testing
@testable import GameShelf

private let marioKart = BarcodeLookup(
    barcode: "045496420055",
    title: "Mario Kart 8 Deluxe",
    platform: .switch,
    region: nil,
    edition: nil,
    genre: "Racing",
    developer: "Nintendo EPD",
    publisher: "Nintendo",
    releaseYear: 2017,
    coverImageUrl: "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg",
    sources: ["UPCitemdb", "IGDB"]
)

/// Answers from `results` by barcode; other codes are not found.
private actor FakeLookupService: BarcodeLookupService {
    var results: [String: Result<BarcodeLookup, any Error>]
    private(set) var lookedUp: [String] = []

    init(_ results: [String: Result<BarcodeLookup, any Error>] = [marioKart.barcode: .success(marioKart)]) {
        self.results = results
    }

    func setResult(_ result: Result<BarcodeLookup, any Error>, for barcode: String) {
        results[barcode] = result
    }

    func lookup(barcode: String) async throws -> BarcodeLookup? {
        lookedUp.append(barcode)
        return try results[barcode]?.get()
    }
}

@Suite("Barcodes")
struct BarcodeTests {
    @Test func dropsTheZerosThatPadUPCAToEAN13OrGTIN14() {
        #expect(Barcode.normalized("0045496420055") == "045496420055")
        #expect(Barcode.normalized("00045496420055") == "045496420055")
        #expect(Barcode.normalized(" 045496420055 ") == "045496420055")
        #expect(Barcode.normalized("5030917077713") == "5030917077713")
        #expect(Barcode.normalized("96385074") == "96385074")
    }

    @Test func comparesTheFormsOfOneCodeAsTheSameProduct() {
        #expect(Barcode.sameProduct("0045496420055", "045496420055"))
        #expect(!Barcode.sameProduct("5030917077713", "045496420055"))
    }

    @Test func accepts8To14Digits() {
        #expect(Barcode.isValid("96385074"))
        #expect(!Barcode.isValid("1234567"))
        #expect(!Barcode.isValid("ABC45496420055"))
        #expect(!Barcode.isValid(""))
    }
}

@Suite("Filling the form from a lookup")
struct GameDraftFillTests {
    @Test func fillsEveryEmptyFieldTheLookupKnows() {
        var lookup = marioKart
        lookup.region = .pal
        lookup.edition = "Limited Edition"
        var draft = GameDraft()

        draft.fill(from: lookup)

        #expect(draft.title == "Mario Kart 8 Deluxe")
        #expect(draft.platform == .switch)
        #expect(draft.edition == "Limited Edition")
        #expect(draft.genre == "Racing")
        #expect(draft.developer == "Nintendo EPD")
        #expect(draft.publisher == "Nintendo")
        #expect(draft.releaseYear == "2017")
        #expect(draft.coverImageUrl == marioKart.coverImageUrl)
        #expect(draft.region == .pal)
        #expect(draft.barcode == "045496420055")
    }

    @Test func keepsWhatTheUserEntered() {
        var draft = GameDraft()
        draft.title = "MK8 Deluxe"
        draft.platform = .switch2
        draft.genre = "Kart racing"
        draft.region = .ntscJ
        draft.barcode = "0045496420055"

        draft.fill(from: marioKart)

        #expect(draft.title == "MK8 Deluxe")
        #expect(draft.platform == .switch2)
        #expect(draft.genre == "Kart racing")
        #expect(draft.region == .ntscJ)
        #expect(draft.barcode == "0045496420055")
        #expect(draft.developer == "Nintendo EPD")
    }
}

@Suite("Barcode lookup requests")
struct BarcodeLookupServiceTests {
    private func makeService(status: Int, body: Data) -> RemoteBarcodeLookupService {
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            #expect(request.httpMethod == "GET")
            #expect(request.url?.path == "/api/v1/lookup/barcode/0045496420055")
            #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer token")
            return (status, body)
        }
        let storage = InMemorySessionStorage(Fixtures.storedSession(access: "token"))
        return RemoteBarcodeLookupService(api: APIClient(
            baseURL: URL(string: "https://\(host)/api/v1/")!,
            tokens: TokenManager(storage: storage, session: storage.load()),
            session: session
        ))
    }

    @Test func decodesTheFoundGame() async throws {
        let body = Data("""
        {"barcode": "045496420055", "title": "Mario Kart 8 Deluxe", "platform": "SWITCH", "region": null,
         "edition": null, "genre": "Racing", "developer": "Nintendo EPD", "publisher": "Nintendo",
         "releaseYear": 2017, "coverImageUrl": "https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg",
         "sources": ["UPCitemdb", "IGDB"]}
        """.utf8)
        let lookup = try await makeService(status: 200, body: body).lookup(barcode: "0045496420055")
        #expect(lookup == marioKart)
    }

    @Test func anUnknownBarcodeIsNotAnError() async throws {
        let body = Fixtures.errorJSON(status: 404, code: "BARCODE_NOT_FOUND")
        let lookup = try await makeService(status: 404, body: body).lookup(barcode: "0045496420055")
        #expect(lookup == nil)
    }

    @Test func anUnavailableDatabaseIsReported() async throws {
        let body = Fixtures.errorJSON(status: 503, code: "LOOKUP_UNAVAILABLE")
        let error = await #expect(throws: APIError.self) {
            try await makeService(status: 503, body: body).lookup(barcode: "0045496420055")
        }
        #expect(ErrorMessage.message(for: try #require(error)) == "The game database isn't available right now. Try again later.")
    }
}

@MainActor
@Suite("Barcode lookup in the game form")
struct GameFormLookupTests {
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

    @Test func aBarcodeScannedFromTheListIsLookedUpOnce() async throws {
        let service = FakeLookupService()
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository(), scannedBarcode: "0045496420055")

        await model.startInitialLookup(using: service)?.value
        #expect(model.startInitialLookup(using: service) == nil)

        #expect(await service.lookedUp == ["045496420055"])
        #expect(model.draft.barcode == "045496420055")
        #expect(model.draft.title == "Mario Kart 8 Deluxe")
        #expect(model.draft.platform == .switch)
        #expect(model.draft.releaseYear == "2017")
        #expect(model.lookupState == .found(sources: ["UPCitemdb", "IGDB"]))
        #expect(model.duplicate == nil)
        #expect(model.hasChanges)
    }

    @Test func aScanKeepsWhatTheUserAlreadyEntered() async throws {
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository())
        model.draft.title = "MK8"

        await model.scanned("045496420055", using: FakeLookupService())

        #expect(model.draft.title == "MK8")
        #expect(model.draft.developer == "Nintendo EPD")
    }

    @Test func anUnknownBarcodeIsReportedAndKept() async throws {
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository())

        await model.scanned("96385074", using: FakeLookupService())

        #expect(model.lookupState == .notFound)
        #expect(model.draft.barcode == "96385074")
        #expect(model.draft.title.isEmpty)
    }

    @Test func aFailedLookupCanBeRetried() async throws {
        let service = FakeLookupService([marioKart.barcode: .failure(APIError.network(.notConnectedToInternet))])
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository())

        await model.scanned("045496420055", using: service)
        #expect(model.lookupState == .failed(ErrorMessage.network))

        await service.setResult(.success(marioKart), for: marioKart.barcode)
        await model.retryLookup(using: service)

        #expect(model.draft.title == "Mario Kart 8 Deluxe")
        #expect(model.lookupState == .found(sources: ["UPCitemdb", "IGDB"]))
    }

    @Test func warnsWhenTheCollectionAlreadyHasAGameWithTheBarcode() async throws {
        let owned = Fixtures.game("Mario Kart 8 Deluxe", .switch) { $0.barcode = "0045496420055" }
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository([owned]))

        await model.scanned("045496420055", using: FakeLookupService())

        #expect(model.duplicate?.id == owned.id)
    }

    @Test func scanningWhileEditingFillsOnlyTheEmptyFieldsOfThatGame() async throws {
        let game = Fixtures.game("MK8 DX", .switch) { $0.barcode = "045496420055" }
        let model = GameFormViewModel(mode: .edit(game), repository: try await makeRepository([game]))

        await model.scanned("045496420055", using: FakeLookupService())

        #expect(model.duplicate == nil)
        #expect(model.draft.title == "MK8 DX")
        #expect(model.draft.genre == "Racing")
    }

    @Test func aCodeTypedByHandThatIsNotABarcodeIsNotLookedUp() async throws {
        let service = FakeLookupService()
        let model = GameFormViewModel(mode: .create, repository: try await makeRepository())

        await model.scanned("1234", using: service)

        #expect(await service.lookedUp.isEmpty)
        #expect(model.draft.barcode == "1234")
        #expect(model.lookupState == nil)
    }
}
