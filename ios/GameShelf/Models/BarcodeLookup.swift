import Foundation

/// `BarcodeLookup` from the API contract: details of the game with a barcode, used to prefill the form.
struct BarcodeLookup: Decodable, Hashable, Sendable {
    var barcode: String
    var title: String
    var platform: Platform?
    var region: Region?
    var edition: String?
    var genre: String?
    var developer: String?
    var publisher: String?
    var releaseYear: Int?
    var coverImageUrl: String?
    /// Databases the details come from, shown as attribution.
    var sources: [String]
}

/// EAN / UPC codes printed on game boxes.
enum Barcode {
    static func isValid(_ code: String) -> Bool {
        !code.isBlank && Validation.barcode(code) == nil
    }

    /// Drops the zeros that pad a UPC-A (12 digits) to EAN-13 or GTIN-14, so that every form of one
    /// code is equal – the camera reads UPC-A as EAN-13 with a leading zero. Same rule as the API.
    static func normalized(_ code: String) -> String {
        var normalized = Substring(code.trimmingCharacters(in: .whitespacesAndNewlines))
        while normalized.count > 12, normalized.first == "0" {
            normalized = normalized.dropFirst()
        }
        return String(normalized)
    }

    static func sameProduct(_ a: String, _ b: String) -> Bool {
        normalized(a) == normalized(b)
    }
}
