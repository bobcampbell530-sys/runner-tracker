//
//  QRScannerView.swift
//  RunnerTracker
//
//  QR code scanner using AVFoundation
//

import SwiftUI
import AVFoundation

struct QRScannerView: View {
    @Environment(\.dismiss) var dismiss
    let onScan: (String) -> Void
    @StateObject private var scanner = QRScannerCoordinator()
    
    var body: some View {
        NavigationView {
            ZStack {
                // Camera preview
                AVCaptureSessionPreviewView(session: scanner.session)
                    .edgesIgnoringSafeArea(.all)
                
                // Scanning overlay
                VStack {
                    Spacer()
                    
                    // Scanning frame
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.green, lineWidth: 3)
                        .frame(width: 250, height: 250)
                    
                    Text("Point camera at the QR code")
                        .foregroundColor(.white)
                        .font(.headline)
                        .padding(.top, 16)
                    
                    Text("On the map page")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.subheadline)
                        .padding(.top, 4)
                    
                    Spacer()
                }
                
                // Cancel button
                VStack {
                    HStack {
                        Spacer()
                        Button(action: {
                            dismiss()
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title)
                                .foregroundColor(.white)
                                .shadow(radius: 3)
                        }
                        .padding()
                    }
                    Spacer()
                }
            }
            .navigationBarHidden(true)
            .onAppear {
                scanner.onScan = { [weak self] result in
                    self?.dismiss()
                    self?.onScan(result)
                }
                scanner.startScanning()
            }
            .onDisappear {
                scanner.stopScanning()
            }
            .alert(item: $scanner.errorMessage) { message in
                Alert(title: Text("QR Scan Failed"), message: Text(message), dismissButton: .default(Text("OK")))
            }
        }
    }
}

// MARK: - Scanner Coordinator (manages AVCaptureSession lifecycle)

class QRScannerCoordinator: ObservableObject {
    let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "scanner.session")
    private var isScanning = false
    
    @Published var errorMessage: String?
    var onScan: ((String) -> Void)?
    
    func startScanning() {
        guard !isScanning else { return }
        isScanning = true
        
        // Check camera access
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCaptureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                if granted {
                    self?.setupCaptureSession()
                } else {
                    self?.setCameraError("Camera permission denied. Enable in Settings.")
                }
            }
        default:
            setCameraError("Camera access unavailable. Please enable camera access in Settings.")
        }
    }
    
    func stopScanning() {
        sessionQueue.async {
            self.session.stopRunning()
            self.isScanning = false
        }
    }
    
    private func setupCaptureSession() {
        session.sessionPreset = .high
        
        guard let backCamera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: backCamera),
              session.canAddInput(input) else {
            setCameraError("Camera setup failed. Please restart the app.")
            return
        }
        
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            setCameraError("Camera configuration failed.")
            return
        }
        
        session.addInput(input)
        session.addOutput(output)
        
        // Process QR codes
        output.setMetadataObjectsHandler { [weak self] objects in
            for object in objects.objects {
                guard let code = object as? AVMetadataMachineReadableCodeObject,
                      let string = code.stringValue else { continue }
                
                self?.handleQRCode(string)
            }
        }
        
        sessionQueue.async {
            self.session.startRunning()
        }
    }
    
    private func setCameraError(_ message: String) {
        DispatchQueue.main.async { [weak self] in
            self?.errorMessage = message
        }
    }
    
    private func handleQRCode(_ code: String) {
        stopScanning()
        
        var serverUrl = ""
        var bib = ""
        
        if code.hasPrefix("runnertracker://") {
            if let url = URL(string: code),
               let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
               let urlParam = components.queryItems?.first(where: { $0.name == "url" })?.value,
               let bibParam = components.queryItems?.first(where: { $0.name == "bib" })?.value {
                serverUrl = urlParam
                bib = bibParam
            }
        }
        // Require runnertracker:// scheme with explicit bib parameter
        // Plain HTTP/HTTPS QR codes are rejected to prevent accidental wrong-bib tracking
        
        if serverUrl.isEmpty || bib.isEmpty {
            DispatchQueue.main.async { [weak self] in
                self?.errorMessage = "QR code must include both server URL and bib number."
            }
            return
        }
        
        // Validate URL is HTTPS
        let urlComponents = URLComponents(string: serverUrl)
        if urlComponents?.scheme != "https" {
            DispatchQueue.main.async { [weak self] in
                self?.errorMessage = "Server URL must use HTTPS for secure tracking."
            }
            return
        }
        
        let result = "URL:\(serverUrl)|BIB:\(bib)"
        
        // Call back on main thread
        DispatchQueue.main.async { [weak self] in
            self?.onScan?(result)
        }
    }
}

// MARK: - AVCaptureSession Preview View

struct AVCaptureSessionPreviewView: UIViewRepresentable {
    let session: AVCaptureSession
    
    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        context.coordinator.layer = layer
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.layer.frame = uiView.bounds
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    class Coordinator {
        var layer: AVCaptureVideoPreviewLayer!
    }
}
