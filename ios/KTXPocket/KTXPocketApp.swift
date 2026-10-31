import SwiftUI

@main
struct KTXPocketApp: App {
    @State private var controller = TrialController()

    var body: some Scene {
        WindowGroup { ContentView(controller: controller) }
    }
}
