//
//  RunnerTrackerApp.swift
//  RunnerTracker
//
//  Runner Tracker — open source race GPS tracking
//

import SwiftUI

@main
struct RunnerTrackerApp: App {
    @StateObject private var deepLinkHandler = DeepLinkHandler()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(deepLinkHandler)
                .onOpenURL { url in
                    deepLinkHandler.handleDeepLink(url)
                }
        }
    }
}

// MARK: - Deep Link Handler

class DeepLinkHandler: ObservableObject {
    @Published var serverUrl: String?
    @Published var bib: String?
    
    func handleDeepLink(_ url: URL) {
        if url.scheme == "runnertracker" {
            let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            
            if let urlParam = components?.queryItems?.first(where: { $0.name == "url" })?.value,
               let bibParam = components?.queryItems?.first(where: { $0.name == "bib" })?.value {
                self.serverUrl = urlParam
                self.bib = bibParam
                print("Deep link received: URL=\(urlParam), BIB=\(bibParam)")
            }
        }
    }
}
