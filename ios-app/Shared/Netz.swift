import Foundation

/// Robustes Laden aus dem Netz (Mensa, Abfahrten).
/// - bis zu 3 Versuche mit kurzer Pause (die freien Schnittstellen haken gelegentlich)
/// - läuft losgelöst von der Ansicht: Wechselt man den Reiter, wird die Anfrage nicht abgebrochen
enum Netz {
    static let sitzung: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 10
        c.timeoutIntervalForResource = 15
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpAdditionalHeaders = ["Accept": "application/json", "User-Agent": "Stundenplan-App (iOS)"]
        return URLSession(configuration: c)
    }()

    /// Widgets haben nur wenig Zeit zum Laden → dort weniger Versuche
    static var imWidget: Bool { Bundle.main.bundlePath.hasSuffix(".appex") }

    /// Daten und HTTP-Status, oder nil wenn alle Versuche scheitern. 404 gilt als gültige Antwort.
    static func laden(_ url: URL, versuche: Int? = nil) async -> (daten: Data, status: Int)? {
        let versuche = versuche ?? (imWidget ? 2 : 3)
        await Task.detached(priority: .userInitiated) { () -> (daten: Data, status: Int)? in
            for i in 0..<versuche {
                if let (d, antwort) = try? await sitzung.data(from: url) {
                    let status = (antwort as? HTTPURLResponse)?.statusCode ?? 200
                    if status < 400 || status == 404 { return (d, status) }
                }
                if i < versuche - 1 {
                    try? await Task.sleep(nanoseconds: UInt64(Double(i + 1) * 1_500_000_000))
                }
            }
            return nil
        }.value
    }
}
