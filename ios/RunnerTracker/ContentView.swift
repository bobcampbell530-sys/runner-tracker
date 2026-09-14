//
//  ContentView.swift
//  RunnerTracker
//
//  Main UI — Bib number, server URL, start/stop buttons
//

import SwiftUI
import AVFoundation

struct ContentView: View {
    @StateObject private var tracker = TrackingManager()
    @State private var bibNumber: String = ""
    @State private var serverUrl: String = ""
    @State private var showQRScanner = false
    @State private var showAlert = false
    @State private var alertMessage = ""
    @EnvironmentObject var deepLinkHandler: DeepLinkHandler
    @AppStorage("savedBib") private var savedBib: String = ""
    @AppStorage("savedUrl") private var savedUrl: String = ""
    
    var body: some View {
        VStack(spacing: 20) {
            // Title
            Text("🏃 Runner Tracker")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)
                .padding(.top, 48)
            
            Text("Background GPS tracking")
                .font(.system(size: 14))
                .foregroundColor(.gray)
            
            Divider().opacity(0.3)
            
            // Bib Number
            VStack(alignment: .leading, spacing: 8) {
                Text("Bib Number")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                
                TextField("e.g. 142", text: $bibNumber)
                    .keyboardType(.numberPad)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color(.systemGray5))
                    .cornerRadius(10)
                    .foregroundColor(.white)
            }
            .padding(.horizontal)
            
            // Server URL
            VStack(alignment: .leading, spacing: 8) {
                Text("Server URL")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                
                HStack(spacing: 10) {
                    TextField("https://your-name.trycloudflare.com", text: $serverUrl)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color(.systemGray5))
                        .cornerRadius(10)
                        .foregroundColor(.white)
                    
                    Button(action: {
                        showQRScanner = true
                    }) {
                        Image(systemName: "qrcode.viewfinder")
                            .font(.title2)
                            .foregroundColor(.white)
                            .frame(width: 50, height: 50)
                            .background(Color.gray.opacity(0.3))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
            
            Divider().opacity(0.3)
            
            // Status
            Text(tracker.statusText)
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(tracker.statusColor)
            
            Text(tracker.lastPing)
                .font(.system(size: 14))
                .foregroundColor(.gray)
            
            Divider().opacity(0.3)
            
            // Buttons
            if tracker.isTracking {
                Button(action: {
                    tracker.stopTracking()
                }) {
                    Text("STOP TRACKING")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.red)
                        .cornerRadius(12)
                }
            } else {
                Button(action: {
                    startTracking()
                }) {
                    Text("START TRACKING")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .cornerRadius(12)
                }
                .disabled(bibNumber.isEmpty || serverUrl.isEmpty)
            }
            
            Spacer()
            
            // Instructions
            Text("Once tracking starts, you can lock your phone and put it in your pocket. Keep Location Services enabled.")
                .font(.system(size: 12))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.bottom, 32)
        }
        .background(Color.black)
        .onAppear {
            // Restore saved preferences
            if !savedBib.isEmpty {
                bibNumber = savedBib
            }
            if !savedUrl.isEmpty {
                serverUrl = savedUrl
            }
        }
        .onChange(of: bibNumber) { newValue in
            savedBib = newValue
        }
        .onChange(of: serverUrl) { newValue in
            savedUrl = newValue
        }
        .onChange(of: deepLinkHandler.serverUrl) { newUrl in
            if let url = newUrl {
                serverUrl = url
                if let bib = deepLinkHandler.bib {
                    bibNumber = bib
                }
                alertMessage = "QR code scanned! URL and bib filled in."
                showAlert = true
            }
        }
        .alert(isPresented: $showAlert) {
            Alert(title: Text("QR Code"), message: Text(alertMessage), dismissButton: .default(Text("OK")))
        }
        .sheet(isPresented: $showQRScanner) {
            QRScannerView(onScan: handleQRCode)
        }
    }
    
    private func handleQRCode(_ result: String) {
        // Parse result: "URL:url|BIB:bib"
        let parts = result.components(separatedBy: "|")
        for part in parts {
            if part.hasPrefix("URL:") {
                serverUrl = String(part.dropFirst(4))
            } else if part.hasPrefix("BIB:") {
                bibNumber = String(part.dropFirst(4))
            }
        }
        alertMessage = "QR code scanned! URL and bib filled in."
        showAlert = true
    }
    
    private func startTracking() {
        tracker.startTracking(bib: bibNumber, url: serverUrl)
    }
}

// MARK: - TrackingManager extensions for UI

extension TrackingManager {
    var statusText: String {
        switch status {
        case .idle:
            return "Ready"
        case .acquiring:
            return "📡 Acquiring GPS…"
        case .tracking:
            return "📍 Tracking \(bibNumber)"
        case .error(let message):
            return "⚠ \(message)"
        }
    }
    
    var statusColor: Color {
        switch status {
        case .idle:
            return .gray
        case .acquiring:
            return .orange
        case .tracking:
            return .green
        case .error:
            return .red
        }
    }
}

// MARK: - Preview

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(DeepLinkHandler())
    }
}
