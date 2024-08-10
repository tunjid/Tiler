//
//  ContentView.swift
//  tiler
//
//  Created by adetunji_dahunsi on 8/9/24.
//
import common
import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack {
            ComposeView()
                    // Compose has own keyboard handler
                    .ignoresSafeArea(edges: .bottom)
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = Main_iosKt.MainViewController()
        controller.overrideUserInterfaceStyle = .light
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

#Preview {
    ContentView()
}
