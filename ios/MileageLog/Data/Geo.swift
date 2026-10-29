import Foundation
import CoreLocation

enum Geo {
    static func meters(_ aLat: Double, _ aLng: Double, _ bLat: Double, _ bLng: Double) -> Double {
        let r = 6_371_000.0
        let dLa = (bLat - aLat) * .pi / 180, dLo = (bLng - aLng) * .pi / 180
        let h = sin(dLa / 2) * sin(dLa / 2) + cos(aLat * .pi / 180) * cos(bLat * .pi / 180) * sin(dLo / 2) * sin(dLo / 2)
        return 2 * r * asin(min(1, sqrt(h)))
    }

    static func coordLabel(_ lat: Double, _ lng: Double) -> String { String(format: "%.4f, %.4f", lat, lng) }

    static func looksLikeCoords(_ s: String) -> Bool {
        s.range(of: #"^\s*-?\d+\.\d+,\s*-?\d+\.\d+\s*$"#, options: .regularExpression) != nil
    }

    static func savedPlace(_ lat: Double?, _ lng: Double?, in places: [Place]) -> Place? {
        guard let lat, let lng else { return nil }
        return places
            .map { ($0, meters(lat, lng, $0.lat, $0.lng)) }
            .filter { $0.1 < 200 }
            .min { $0.1 < $1.1 }?.0
    }

    /// A saved place name, else a short street address, else coordinates.
    static func label(_ lat: Double?, _ lng: Double?, places: [Place]) async -> String {
        guard let lat, let lng else { return "" }
        if let p = savedPlace(lat, lng, in: places) { return p.name }
        let geocoder = CLGeocoder()
        if let mark = try? await geocoder.reverseGeocodeLocation(CLLocation(latitude: lat, longitude: lng)).first {
            let street = [mark.subThoroughfare, mark.thoroughfare].compactMap { $0 }.joined(separator: " ")
            let town = mark.locality ?? mark.subAdministrativeArea
            let parts = [street.isEmpty ? nil : street, town].compactMap { $0 }
            if !parts.isEmpty { return parts.joined(separator: ", ") }
            if let name = mark.name { return name }
        }
        return coordLabel(lat, lng)
    }
}
