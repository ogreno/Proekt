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
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
    }
}

