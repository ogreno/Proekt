import SwiftUI
import Shared

@main
struct ProektApp: App {
    var body: some Scene {
        WindowGroup {
            MusicView()
                .ignoresSafeArea()
        }
    }
}

struct MusicView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        #if DEBUG
        let screenshots = ProcessInfo.processInfo.arguments.contains("--screenshots")
        #else
        let screenshots = false
        #endif
        return MainViewControllerKt.MainViewController(screenshotMode: screenshots)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
    }
}

