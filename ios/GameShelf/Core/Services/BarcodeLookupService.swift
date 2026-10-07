import Foundation
import SwiftUI

/// Finds games by the barcode on their box. Unlike the collection, it needs a connection.
protocol BarcodeLookupService: Sendable {
    /// `GET lookup/barcode/{barcode}`; `nil` when no database knows the code.
    func lookup(barcode: String) async throws -> BarcodeLookup?
}

struct RemoteBarcodeLookupService: BarcodeLookupService {
    let api: APIClient

    func lookup(barcode: String) async throws -> BarcodeLookup? {
        do {
            return try await api.send(.barcodeLookup(barcode))
        } catch let error as APIError where error.code == .barcodeNotFound {
            return nil
        }
    }
}

/// Used where no service is provided (previews): every lookup fails as if offline.
struct OfflineBarcodeLookupService: BarcodeLookupService {
    func lookup(barcode: String) async throws -> BarcodeLookup? {
        throw APIError.network(.notConnectedToInternet)
    }
}

private struct BarcodeLookupServiceKey: EnvironmentKey {
    static let defaultValue: any BarcodeLookupService = OfflineBarcodeLookupService()
}

extension EnvironmentValues {
    var barcodeLookup: any BarcodeLookupService {
        get { self[BarcodeLookupServiceKey.self] }
        set { self[BarcodeLookupServiceKey.self] = newValue }
    }
}
