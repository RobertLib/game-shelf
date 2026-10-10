import Foundation
import Testing
@testable import GameShelf

@Suite("Background time for started work")
struct AppBackgroundTaskTests {
    private struct Failure: Error, Equatable {}

    @Test func returnsTheResultOfTheWork() async {
        let value = await AppBackgroundTask.run(named: "Test") { 42 }
        #expect(value == 42)
    }

    @Test func passesFailuresOn() async {
        await #expect(throws: Failure()) {
            try await AppBackgroundTask.run(named: "Test") { throw Failure() }
        }
    }

    @Test @MainActor func runsTheWorkInTheCallersIsolation() async {
        // The work touches the caller's state without crossing to another actor.
        var runs = 0
        await AppBackgroundTask.run(named: "Test") { runs += 1 }
        #expect(runs == 1)
    }
}
