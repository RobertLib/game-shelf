import Foundation

/// Intercepts requests of a `URLSession` and answers them from an async handler.
///
/// Handlers are registered per host, so tests running in parallel can each use
/// their own unique host without sharing state.
final class StubURLProtocol: URLProtocol {
    typealias Handler = @Sendable (URLRequest) async throws -> (Int, Data)

    private static let registry = HandlerRegistry()

    /// Creates a session whose requests to `host` are answered by `handler`.
    static func session(host: String, handler: @escaping Handler) -> URLSession {
        registry.set(handler, for: host)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: configuration)
    }

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host.map { registry.handler(for: $0) != nil } ?? false
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let url = request.url, let host = url.host, let handler = Self.registry.handler(for: host) else {
            client?.urlProtocol(self, didFailWithError: URLError(.unsupportedURL))
            return
        }
        // URLProtocol is not Sendable; the loading system guarantees `client` stays valid until we finish.
        let protocolBox = UncheckedSendableBox(self)
        let request = request.withBodyFromStream()
        Task {
            let stub = protocolBox.value
            do {
                let (statusCode, data) = try await handler(request)
                let response = HTTPURLResponse(
                    url: url, statusCode: statusCode, httpVersion: "HTTP/1.1",
                    headerFields: ["Content-Type": "application/json"]
                )!
                stub.client?.urlProtocol(stub, didReceive: response, cacheStoragePolicy: .notAllowed)
                stub.client?.urlProtocol(stub, didLoad: data)
                stub.client?.urlProtocolDidFinishLoading(stub)
            } catch {
                stub.client?.urlProtocol(stub, didFailWithError: error)
            }
        }
    }

    override func stopLoading() {}
}

private final class HandlerRegistry: @unchecked Sendable {
    private let lock = NSLock()
    private var handlers: [String: StubURLProtocol.Handler] = [:]

    func set(_ handler: @escaping StubURLProtocol.Handler, for host: String) {
        lock.withLock { handlers[host] = handler }
    }

    func handler(for host: String) -> StubURLProtocol.Handler? {
        lock.withLock { handlers[host] }
    }
}

private struct UncheckedSendableBox<Value>: @unchecked Sendable {
    let value: Value
    init(_ value: Value) { self.value = value }
}

private extension URLRequest {
    /// URLSession hands bodies to protocols as a stream; materialize it for assertions.
    func withBodyFromStream() -> URLRequest {
        guard httpBody == nil, let stream = httpBodyStream else { return self }
        var request = self
        var data = Data()
        stream.open()
        defer { stream.close() }
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            data.append(buffer, count: count)
        }
        request.httpBody = data
        return request
    }
}

/// Unique host per test so registered handlers never collide.
func uniqueTestHost() -> String {
    "\(UUID().uuidString.lowercased()).test"
}
