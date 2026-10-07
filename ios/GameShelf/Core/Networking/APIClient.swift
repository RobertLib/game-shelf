import Foundation

/// Thin JSON client for the Game Shelf REST API.
final class APIClient: Sendable {
    let baseURL: URL
    private let session: URLSession
    private let tokens: TokenManager
    private let encoder = JSONEncoder.api()
    private let decoder = JSONDecoder.api()

    init(baseURL: URL, tokens: TokenManager, session: URLSession = APIClient.makeDefaultSession()) {
        self.baseURL = baseURL
        self.tokens = tokens
        self.session = session
    }

    static func makeDefaultSession() -> URLSession {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.waitsForConnectivity = false
        configuration.httpAdditionalHeaders = ["Accept": "application/json"]
        return URLSession(configuration: configuration)
    }

    /// Performs the call. Authenticated calls that fail with `401` refresh the
    /// token pair once and are retried once.
    func send<Response>(_ endpoint: Endpoint<Response>) async throws -> Response {
        guard endpoint.requiresAuthentication else {
            return try await perform(endpoint, accessToken: nil)
        }
        guard let accessToken = await tokens.accessToken else {
            throw APIError.sessionExpired
        }
        do {
            return try await perform(endpoint, accessToken: accessToken)
        } catch let error as APIError where error.isUnauthorized {
            let renewedToken = try await tokens.accessToken(replacing: accessToken) { [self] refreshToken in
                try await perform(.refresh(RefreshTokenRequest(refreshToken: refreshToken)), accessToken: nil)
            }
            do {
                return try await perform(endpoint, accessToken: renewedToken)
            } catch let error as APIError where error.isUnauthorized {
                await tokens.expire()
                throw APIError.sessionExpired
            }
        }
    }

    private func perform<Response>(_ endpoint: Endpoint<Response>, accessToken: String?) async throws -> Response {
        let request = try makeRequest(for: endpoint, accessToken: accessToken)
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch let error as URLError {
            throw APIError.network(error.code)
        }

        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            let body = try? decoder.decode(ErrorResponse.self, from: data)
            throw APIError.server(
                statusCode: http.statusCode,
                code: body?.code ?? .unknown,
                details: body?.details ?? []
            )
        }

        if let empty = EmptyResponse() as? Response {
            return empty
        }
        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            throw APIError.invalidResponse
        }
    }

    func makeRequest<Response>(for endpoint: Endpoint<Response>, accessToken: String?) throws -> URLRequest {
        guard var components = URLComponents(
            url: baseURL.appendingPathComponent(endpoint.path),
            resolvingAgainstBaseURL: true
        ) else { throw APIError.invalidResponse }
        if !endpoint.queryItems.isEmpty {
            components.queryItems = endpoint.queryItems
            // `+` is legal in a query but servers decode it as a space.
            components.percentEncodedQuery = components.percentEncodedQuery?
                .replacingOccurrences(of: "+", with: "%2B")
        }
        guard let url = components.url else { throw APIError.invalidResponse }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        if let body = endpoint.body {
            request.httpBody = try encoder.encode(body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let accessToken {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        return request
    }
}
