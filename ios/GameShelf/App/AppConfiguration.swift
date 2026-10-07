import Foundation

enum AppConfiguration {
    /// Base URL of the REST API, from the `API_BASE_URL` build setting (via Info.plist).
    static let apiBaseURL: URL = {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "GSAPIBaseURL") as? String,
              let url = URL(string: value), url.scheme != nil
        else {
            preconditionFailure("GSAPIBaseURL is missing in Info.plist; set the API_BASE_URL build setting.")
        }
        // Debug builds on a physical device take the host from DEV_API_HOST (ios/Config/Local.xcconfig).
        guard url.host()?.isEmpty == false else {
            preconditionFailure("GSAPIBaseURL \(value) has no host; run `make ios-device-host` to point device builds at this Mac.")
        }
        return url
    }()

    static var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }
}
