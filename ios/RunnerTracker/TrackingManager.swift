//
//  TrackingManager.swift
//  RunnerTracker
//
//  Core tracking engine — GPS + Network
//  Handles location updates and sends pings to server
//

import Foundation
import CoreLocation
import Combine

class TrackingManager: NSObject, ObservableObject {
    @Published var status: TrackingStatus = .idle
    @Published var lastPing: String = ""
    @Published var lastLocation: LocationInfo?
    @Published var isTracking: Bool = false
    
    private let locationManager = CLLocationManager()
    private var serverUrl: String = ""
    private var bibNumber: String = ""
    private var lastPingTime: TimeInterval = 0
    private let pingInterval: TimeInterval = 20.0
    
    enum TrackingStatus {
        case idle
        case acquiring
        case tracking
        case error(String)
    }
    
    struct LocationInfo: Codable {
        let lat: Double
        let lng: Double
        let alt: Double
        let accuracy: Double
        let speed: Double
        let timestamp: Double  // Time the GPS fix was taken
    }
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 5.0 // Update every 5 meters
        
        // CRITICAL: Allows Core Location to continue updating in background
        locationManager.allowsBackgroundLocationUpdates = true
    }
    
    // MARK: - Public API
    
    func startTracking(bib: String, url: String) {
        guard !bib.isEmpty && !url.isEmpty else { return }
        
        // Basic URL validation
        guard let _ = URL(string: url) else {
            self.status = .error("Invalid URL format. Please check the server URL.")
            return
        }
        
        // Validate URL uses HTTPS
        guard url.hasPrefix("https://") else {
            self.status = .error("Server URL must use HTTPS. Add https:// to the beginning.")
            return
        }
        
        self.bibNumber = bib
        self.serverUrl = fixUrl(url)
        
        print("Starting tracking for \(bibNumber) at \(serverUrl)")
        
        let authStatus = locationManager.authorizationStatus
        
        switch authStatus {
        case .notDetermined:
            isTracking = true
            self.status = .acquiring
            locationManager.requestAlwaysAuthorization()
            
        case .authorizedAlways:
            isTracking = true
            self.status = .acquiring
            locationManager.startUpdatingLocation()
            
        case .authorizedWhenInUse:
            self.status = .error("For race tracking, please allow 'Always' location access in Settings.")
            isTracking = false
            
        case .denied, .restricted:
            self.status = .error("Location permission denied. Please enable Always location access in Settings > Privacy > Location Services.")
            isTracking = false
            
        @unknown default:
            self.status = .error("Unknown authorization status.")
            isTracking = false
        }
    }
    
    func stopTracking() {
        isTracking = false
        self.status = .idle
        lastPing = ""
        lastPingTime = 0
        locationManager.stopUpdatingLocation()
    }
    
    // MARK: - Private
    
    private func fixUrl(url: String) -> String {
        var u = url.trimmingCharacters(in: .whitespacesAndNewlines)
        // Remove trailing slash so we don't get double slashes
        while u.hasSuffix("/") {
            u = String(u.dropLast())
        }
        // Add https if missing
        if !u.hasPrefix("http://") && !u.hasPrefix("https://") {
            u = "https://" + u
            print("⚠️ Added https:// to URL")
        }
        return u
    }
    
    private func sendPing() {
        guard let loc = lastLocation else { return }
        
        let pingData: [String: Any] = [
            "name": bibNumber,
            "lat": loc.lat,
            "lng": loc.lng,
            "alt": loc.alt,
            "acc": loc.accuracy,
            "speed": loc.speed,
            "ts": loc.timestamp
        ]
        
        guard let url = URL(string: "\(serverUrl)/api/ping") else {
            self.status = .error("Invalid server URL")
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 10
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: pingData)
        } catch {
            print("Error serializing ping data: \(error)")
            return
        }
        
        let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("Ping error: \(error.localizedDescription)")
                    self?.status = .error("Network error — retrying on next GPS fix")
                    return
                }
                
                // Check HTTP status code
                if let httpResponse = response as? HTTPURLResponse {
                    if httpResponse.statusCode != 200 {
                        print("Ping HTTP \(httpResponse.statusCode)")
                        self?.status = .error("Server returned HTTP \(httpResponse.statusCode) — check server is running")
                        return
                    }
                }
                
                if let data = data,
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let online = json["runners_online"] as? Int {
                    self?.status = .tracking
                    self?.lastPing = "✅ \(online) online"
                } else {
                    print("Unexpected server response")
                }
            }
        }
        task.resume()
    }
    
    // MARK: - Location Callbacks
    
    private func updateStatus(location: CLLocation) {
        lastPing = "📍 \(location.coordinate.latitude, specifier: "%.4f") / \(location.coordinate.longitude, specifier: "%.4f")"
        
        lastLocation = LocationInfo(
            lat: location.coordinate.latitude,
            lng: location.coordinate.longitude,
            alt: location.altitude,
            accuracy: location.horizontalAccuracy,
            speed: location.speed >= 0 ? location.speed : 0,
            timestamp: location.timestamp.timeIntervalSince1970
        )
    }
}

// MARK: - CLLocationManagerDelegate

extension TrackingManager: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization authStatus: CLAuthorizationStatus) {
        switch authStatus {
        case .authorizedAlways:
            // Good — we have the permission we need for background tracking
            if isTracking {
                locationManager.startUpdatingLocation()
                self.status = .acquiring
            }
            
        case .authorizedWhenInUse:
            // Not good enough for race tracking — user needs to upgrade in Settings
            self.status = .error("Race tracking requires 'Always' location access. Please update in Settings.")
            isTracking = false
            
        case .denied, .restricted:
            self.status = .error("Location access denied. Enable in Settings > Privacy > Location Services.")
            isTracking = false
            
        case .notDetermined:
            // User hasn't made a choice yet — waiting for the dialog
            break
            
        @unknown default:
            break
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        // Reject stale locations (older than 60 seconds)
        if location.timestamp.timeIntervalSinceNow < -60 {
            print("Stale location rejected (\(location.timestamp.timeIntervalSinceNow) seconds old)")
            return
        }
        
        // Reject inaccurate locations (> 200m accuracy)
        // Raised from 100m for wilderness race environment
        if location.horizontalAccuracy < 0 || location.horizontalAccuracy > 200 {
            return
        }
        
        updateStatus(location: location)
        
        // Enforce 20-second minimum between pings
        guard isTracking else { return }
        
        let now = Date().timeIntervalSince1970
        guard now - lastPingTime >= pingInterval else {
            return // Too soon, wait
        }
        
        lastPingTime = now
        sendPing()
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if let locErr = error as? CLError {
            switch locErr.code {
            case .locationUnknown:
                self.status = .acquiring
            case .denied:
                self.status = .error("Location access denied")
            default:
                break
            }
        }
    }
}
