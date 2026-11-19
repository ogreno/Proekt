import XCTest

final class ScreenTour: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        app.launchArguments = ["--screenshots"]
        app.launch()
        XCTAssertTrue(element("home-tracks").waitForExistence(timeout: 60))
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        app.terminate()
    }

    func testAllScreens() throws {
        capture("01-home")

        tap("home-tracks")
        XCTAssertTrue(element("track-menu-1").waitForExistence(timeout: 15))
        capture("02-tracks")

        search("Demo")
        capture("03-track-search")
        tap("clear-search")
        tap("track-menu-1")
        tap("edit-track")
        XCTAssertTrue(app.textFields.firstMatch.waitForExistence(timeout: 10))
        capture("04-edit-track")
        tapText("Отмена")
        tap("go-back")

        tap("home-favorites")
        XCTAssertTrue(element("track-menu-1").waitForExistence(timeout: 15))
        capture("05-favorites")
        search("Demo")
        capture("06-favorites-search")
        tap("go-back")

        tap("home-playlists")
        XCTAssertTrue(element("playlist-1").waitForExistence(timeout: 15))
        capture("07-playlists")
        search("Mix")
        capture("08-playlist-search")
        tap("clear-search")
        tap("create-playlist")
        XCTAssertTrue(app.textFields.firstMatch.waitForExistence(timeout: 10))
        capture("09-create-playlist")
        tapText("Отмена")

        tap("playlist-1")
        XCTAssertTrue(element("add-playlist-track").waitForExistence(timeout: 15))
        capture("10-playlist-details")
        tap("add-playlist-track")
        XCTAssertTrue(app.staticTexts["Вечерний ритм"].firstMatch.waitForExistence(timeout: 10))
        capture("11-add-track")
        tapText("Закрыть")

        tap("open-player")
        XCTAssertTrue(element("open-queue").waitForExistence(timeout: 15))
        capture("12-player")
        tap("open-queue")
        XCTAssertTrue(app.staticTexts["Очередь воспроизведения"].waitForExistence(timeout: 10))
        capture("13-queue")
        tapText("Готово")
        tap("open-timer")
        XCTAssertTrue(app.staticTexts["Через 15 мин"].waitForExistence(timeout: 10))
        capture("14-sleep-timer")
        tapText("Закрыть")

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(element("open-queue").waitForExistence(timeout: 10))
        capture("15-player-landscape")
        tap("go-back")
        capture("16-playlist-landscape")
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func tap(_ identifier: String) {
        let control = element(identifier)
        XCTAssertTrue(control.waitForExistence(timeout: 15), "Missing control: " + identifier)
        control.tap()
    }

    private func tapText(_ text: String) {
        let button = app.buttons[text].firstMatch
        if button.waitForExistence(timeout: 3) {
            button.tap()
        } else {
            let label = app.staticTexts[text].firstMatch
            XCTAssertTrue(label.waitForExistence(timeout: 10), "Missing text: " + text)
            label.tap()
        }
    }

    private func search(_ text: String) {
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText(text)
    }

    private func capture(_ name: String) {
        // Даём экрану завершить переход и отрисовку перед снимком.
        Thread.sleep(forTimeInterval: 1.5)
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)

        // PNG также сохраняется в контейнере тестового приложения для GitHub.
        let folder = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("ProektScreenshots", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try screenshot.pngRepresentation.write(to: folder.appendingPathComponent(name + ".png"))
        } catch {
            XCTFail("Could not save screenshot: " + error.localizedDescription)
        }
    }
}

