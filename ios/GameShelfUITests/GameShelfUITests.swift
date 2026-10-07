import XCTest

/// End-to-end walkthrough against the running API (see README). Every step attaches a
/// screenshot to the test result. Requires the demo account `demo@example.com` (`make seed`);
/// tests that change data work on throwaway accounts they create and delete themselves.
@MainActor
final class GameShelfUITests: XCTestCase {
    private static let demoEmail = "demo@example.com"
    private static let demoPassword = "demo-collector"
    private nonisolated static let apiBaseURL = URL(string: "http://localhost:3000/api/v1/")!

    // MARK: Auth

    func testLoginAndRegistrationValidation() throws {
        continueAfterFailure = false
        let app = launch(signedOutWith: [])
        let login = app.buttons["Sign in"]
        XCTAssertTrue(login.waitForExistence(timeout: 10))
        snapshot("01-login")

        login.tap()
        XCTAssertTrue(app.text("Enter your email.").waitForExistence(timeout: 2))
        XCTAssertTrue(app.text("Enter your password.").exists)
        snapshot("02-login-validation")

        type(Self.demoEmail, into: app.textFields["login.email"])
        type("wrong-password-1", into: app.secureTextFields["login.password"])
        login.tap()
        XCTAssertTrue(app.text("Incorrect email or password.").waitForExistence(timeout: 10))
        snapshot("03-login-wrong-password")

        app.buttons["Don't have an account? Create one"].tap()
        let create = app.buttons["Create account"]
        XCTAssertTrue(create.waitForExistence(timeout: 5))
        dismissSavePasswordPrompt(in: app)
        type("not-an-email", into: app.textFields["register.email"])
        type("short", into: app.secureTextFields["register.password"])
        type("different", into: app.secureTextFields["register.confirmation"])
        create.tap()
        XCTAssertTrue(app.text("Enter a valid email address.").waitForExistence(timeout: 2))
        XCTAssertTrue(app.text("Password must be 8–128 characters.").exists)
        XCTAssertTrue(app.text("Passwords don't match.").exists)
        snapshot("04-register-validation")

        app.navigationBars.buttons.firstMatch.tap()
        let password = app.secureTextFields["login.password"]
        XCTAssertTrue(password.waitForExistence(timeout: 5))
        password.tap()
        password.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 20) + Self.demoPassword)
        login.tap()
        XCTAssertTrue(app.staticTexts["42 games"].waitForExistence(timeout: 10))
        snapshot("05-signed-in")
    }

    // MARK: Browsing

    func testBrowseSearchSortFilterAndDetail() throws {
        continueAfterFailure = false
        let app = launch(signedInAs: Self.demoEmail, password: Self.demoPassword)
        XCTAssertTrue(app.staticTexts["42 games"].waitForExistence(timeout: 10))
        snapshot("10-list")

        // Infinite scroll: the last title lives on page 2.
        for _ in 0..<12 where !app.text("Wii Sports").exists {
            app.swipeUp(velocity: .fast)
        }
        XCTAssertTrue(app.text("Wii Sports").waitForExistence(timeout: 10))
        snapshot("11-list-page-2")
        for _ in 0..<12 where !app.searchFields.firstMatch.isHittable {
            app.swipeDown(velocity: .fast)
        }

        let search = app.searchFields.firstMatch
        search.tap()
        search.typeText("zelda")
        XCTAssertTrue(app.staticTexts["3 games"].waitForExistence(timeout: 5))
        snapshot("12-search")
        search.typeText(" xyz")
        XCTAssertTrue(app.text("No games match your filters").waitForExistence(timeout: 5))
        snapshot("13-no-results")
        app.buttons["Reset filters"].tap()
        XCTAssertTrue(app.staticTexts["42 games"].waitForExistence(timeout: 5))
        for label in ["Close", "Cancel"] where app.navigationBars.buttons[label].exists {
            app.navigationBars.buttons[label].tap()
            break
        }

        app.buttons["Sort"].tap()
        let byValue = app.buttons["Estimated value"]
        XCTAssertTrue(byValue.waitForExistence(timeout: 2))
        snapshot("14-sort-menu")
        byValue.tap()
        XCTAssertTrue(app.text("Panzer Dragoon Saga").waitForExistence(timeout: 5))
        snapshot("15-sorted-by-value")

        app.buttons["Filters"].tap()
        let apply = app.buttons["Apply"]
        XCTAssertTrue(apply.waitForExistence(timeout: 5))
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'SNES'")).firstMatch.tap()
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Nintendo 64'")).firstMatch.tap()
        snapshot("16-filters")
        let form = app.collectionViews["filters.form"]
        XCTAssertTrue(form.waitForExistence(timeout: 5))
        form.swipeUp()
        form.swipeUp()
        snapshot("17-filters-expanded")
        form.swipeUp()
        form.swipeUp()
        snapshot("18-filters-ranges")
        toggle(app.switches["Favorites only"].firstMatch, in: form)
        snapshot("19-filters-favorites")
        apply.tap()
        XCTAssertTrue(app.staticTexts["3 games"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Favorites"].waitForExistence(timeout: 5), "active filter chip")
        XCTAssertTrue(app.buttons["Nintendo 64"].exists)
        snapshot("20-list-filtered")
        app.buttons["Nintendo 64"].tap()
        XCTAssertTrue(app.staticTexts["1 game"].waitForExistence(timeout: 5))
        snapshot("20b-chip-removed")

        app.text("Super Mario World").tap()
        XCTAssertTrue(app.buttons["Edit"].waitForExistence(timeout: 5))
        snapshot("21-detail")
        app.swipeUp()
        snapshot("22-detail-bottom")

        app.buttons["Edit"].tap()
        let title = app.textFields["gameForm.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        snapshot("23-edit-form")
        app.swipeUp()
        snapshot("24-edit-form-collector")
        app.swipeUp()
        snapshot("25-edit-form-purchase")
        app.swipeUp()
        snapshot("26-edit-form-play")
        app.swipeDown()
        app.swipeDown()
        app.swipeDown()
        title.tap()
        title.typeText(" (edited)")
        app.buttons["Cancel"].tap()
        let discard = app.buttons["Discard changes"]
        XCTAssertTrue(discard.waitForExistence(timeout: 2))
        snapshot("27-discard-dialog")
        discard.tap()
        XCTAssertTrue(app.text("Super Mario World").waitForExistence(timeout: 5))

        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Clear all"].tap()
        XCTAssertTrue(app.staticTexts["42 games"].waitForExistence(timeout: 5))
        app.buttons["Profile & settings"].tap()
        XCTAssertTrue(app.buttons["Sign out"].waitForExistence(timeout: 5))
        snapshot("28-profile")
    }

    // MARK: Editing

    func testCreateFavoriteEditAndDeleteGame() async throws {
        continueAfterFailure = false
        let account = try await registerThrowawayAccount()
        let app = launch(signedInAs: account.email, password: account.password)
        let add = app.buttons["Add game"]
        XCTAssertTrue(add.waitForExistence(timeout: 10))
        add.tap()

        let save = app.buttons["Save"]
        XCTAssertTrue(save.waitForExistence(timeout: 5))
        snapshot("30-new-game")
        save.tap()
        XCTAssertTrue(app.text("Enter a title.").waitForExistence(timeout: 2))
        XCTAssertTrue(app.text("Choose a platform.").exists)
        snapshot("31-new-game-validation")

        type("UI Test Game", into: app.textFields["gameForm.title"])
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Platform'")).firstMatch.tap()
        let platformSearch = app.searchFields["Search platforms"]
        XCTAssertTrue(platformSearch.waitForExistence(timeout: 5))
        snapshot("32-platform-picker")
        platformSearch.tap()
        platformSearch.typeText("Dreamcast")
        app.buttons["Dreamcast"].firstMatch.tap()

        dismissKeyboard(in: app)
        let price = app.textFields["gameForm.purchasePrice"]
        for _ in 0..<6 where !price.isHittable {
            app.swipeUp()
        }
        type("499.90", into: price)
        snapshot("33-new-game-filled")
        save.tap()

        XCTAssertTrue(add.waitForExistence(timeout: 10))
        let search = app.searchFields.firstMatch
        search.tap()
        search.typeText("UI Test")
        XCTAssertTrue(app.text("UI Test Game").waitForExistence(timeout: 10))
        snapshot("34-list-after-create")
        app.text("UI Test Game").tap()
        XCTAssertTrue(app.buttons["Edit"].waitForExistence(timeout: 5))
        snapshot("35-created-detail")
        scrollUntilHittable(app.text("499.90"), in: app)
        snapshot("35b-created-detail-price")
        app.swipeDown()

        app.buttons["Add to favorites"].tap()
        XCTAssertTrue(app.buttons["Remove from favorites"].waitForExistence(timeout: 5))
        snapshot("36-favorite")

        app.buttons["Edit"].tap()
        let title = app.textFields["gameForm.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        title.tap()
        title.typeText(" II")
        app.buttons["Save"].tap()
        XCTAssertTrue(app.text("UI Test Game II").waitForExistence(timeout: 10))
        snapshot("37-edited-detail")

        let deleteButton = app.buttons["Delete game"].firstMatch
        scrollUntilHittable(deleteButton, in: app)
        deleteButton.tap()
        let confirm = app.sheets.buttons["Delete game"].exists ? app.sheets.buttons["Delete game"] : app.buttons["Delete game"].firstMatch
        snapshot("38-delete-confirmation")
        confirm.tap()
        XCTAssertTrue(app.text("No games match your filters").waitForExistence(timeout: 10))
        XCTAssertFalse(app.text("UI Test Game II").exists)
        snapshot("39-list-after-delete")
    }

    // MARK: Account

    func testRegisterChangePasswordAndDeleteAccount() throws {
        continueAfterFailure = false
        let email = "ios-ui-\(Int(Date().timeIntervalSince1970))@example.com"
        let app = launch(signedOutWith: [])
        app.buttons["Don't have an account? Create one"].tap()
        type(email, into: app.textFields["register.email"])
        type("UI Tester", into: app.textFields["register.displayName"])
        type("secret-12345", into: app.secureTextFields["register.password"])
        type("secret-12345", into: app.secureTextFields["register.confirmation"])
        app.buttons["Create account"].tap()

        XCTAssertTrue(app.buttons["Add your first game"].waitForExistence(timeout: 10))
        snapshot("40-empty-collection")

        app.buttons["Profile & settings"].tap()
        XCTAssertTrue(app.text(email).waitForExistence(timeout: 5))
        snapshot("41-profile")

        app.buttons["Change password"].tap()
        type("secret-12345", into: app.secureTextFields["Current password"])
        type("secret-67890", into: app.secureTextFields["New password"])
        type("secret-67890", into: app.secureTextFields["Confirm new password"])
        app.buttons["Change password"].tap()
        XCTAssertTrue(app.alerts["Password changed"].waitForExistence(timeout: 10))
        snapshot("42-password-changed")
        app.alerts.buttons["OK"].tap()

        let deleteLink = app.buttons["Delete account"]
        XCTAssertTrue(deleteLink.waitForExistence(timeout: 5))
        deleteLink.tap()
        type("secret-67890", into: app.secureTextFields["Password"])
        snapshot("43-delete-account")
        app.buttons["Delete account and collection"].tap()
        let confirm = app.buttons["Delete account"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 2))
        snapshot("44-delete-account-confirmation")
        confirm.tap()
        XCTAssertTrue(app.buttons["Sign in"].waitForExistence(timeout: 10))
        snapshot("45-after-account-deletion")
    }

    // MARK: Locale

    /// The UI stays English while numbers follow the device locale (here German grouping).
    func testNumbersFollowTheDeviceLocale() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = [
            "-uiTestingResetSession", "-AppleLanguages", "(en)", "-AppleLocale", "de_DE",
            "-uiTestingAutoLogin", Self.demoEmail, Self.demoPassword,
        ]
        app.launch()
        XCTAssertTrue(app.staticTexts["42 games"].waitForExistence(timeout: 10))
        // Banjo-Kazooie is worth 1500 CZK in the demo collection.
        XCTAssertTrue(app.text("1.500").waitForExistence(timeout: 5))
        XCTAssertFalse(app.text("1,500").exists)
        snapshot("50-german-locale")
    }

    // MARK: Helpers

    /// Registers an account through the API and deletes it again when the test ends.
    private func registerThrowawayAccount() async throws -> (email: String, password: String) {
        let email = "ios-ui-\(UUID().uuidString.prefix(8).lowercased())@example.com"
        let password = "secret-12345"
        let data = try await Self.apiRequest("POST", "auth/register", body: ["email": email, "password": password])
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let accessToken = try XCTUnwrap(json?["accessToken"] as? String)
        addTeardownBlock {
            _ = try? await Self.apiRequest("DELETE", "auth/me", body: ["password": password], token: accessToken)
        }
        return (email, password)
    }

    @discardableResult
    private nonisolated static func apiRequest(
        _ method: String,
        _ path: String,
        body: [String: String],
        token: String? = nil
    ) async throws -> Data {
        var request = URLRequest(url: apiBaseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw URLError(.badServerResponse, userInfo: [NSLocalizedDescriptionKey: "\(method) \(path) → \(status)"])
        }
        return data
    }

    /// Launches signed out with an English language and US formatting, independent of the device settings.
    private func launch(signedOutWith extraArguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTestingResetSession", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + extraArguments
        app.launch()
        return app
    }

    private func launch(signedInAs email: String, password: String) -> XCUIApplication {
        launch(signedOutWith: ["-uiTestingAutoLogin", email, password])
    }

    private func type(_ text: String, into element: XCUIElement) {
        XCTAssertTrue(element.waitForExistence(timeout: 5), "Missing field \(element)")
        element.tap()
        // A tap during a navigation transition can be swallowed; retry once.
        if !XCUIApplication().keyboards.firstMatch.waitForExistence(timeout: 2) {
            element.tap()
        }
        element.typeText(text)
    }

    /// Form switches span the whole row; only the control on the trailing edge toggles.
    private func toggle(_ element: XCUIElement, in container: XCUIElement) {
        scrollUntilHittable(element, in: container)
        element.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
    }

    /// Swipe distances vary, so search both directions for a lazily rendered element.
    private func scrollUntilHittable(_ element: XCUIElement, in container: XCUIElement) {
        for _ in 0..<5 where !(element.exists && element.isHittable) {
            container.swipeUp(velocity: .slow)
        }
        for _ in 0..<10 where !(element.exists && element.isHittable) {
            container.swipeDown(velocity: .slow)
        }
        XCTAssertTrue(element.exists && element.isHittable, "Could not scroll to \(element)")
    }

    /// After a submitted login form iOS may offer to save the password. The system sheet follows
    /// the device language, so dismiss it by position: its first button is "Not Now".
    private func dismissSavePasswordPrompt(in app: XCUIApplication) {
        let prompt = app.sheets.firstMatch
        guard prompt.waitForExistence(timeout: 1.5), prompt.buttons.count >= 2 else { return }
        prompt.buttons.element(boundBy: 0).tap()
    }

    private func dismissKeyboard(in app: XCUIApplication) {
        let done = app.buttons["Done"]
        if done.exists {
            done.tap()
        }
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

private extension XCUIApplication {
    /// Static text whose accessibility label contains `value` (rows and field errors combine labels).
    func text(_ value: String) -> XCUIElement {
        staticTexts.matching(NSPredicate(format: "label CONTAINS %@", value)).firstMatch
    }
}
