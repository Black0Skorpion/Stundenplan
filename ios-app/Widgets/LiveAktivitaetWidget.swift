import ActivityKit
import WidgetKit
import SwiftUI

/// Darstellung der Live-Aktivität: Sperrbildschirm-Banner und Dynamic Island.
/// Startet zum Beginn der Veranstaltung: Ende und Fortschritt. Zum Ende wird sie „veraltet“ – iOS zeichnet sie
/// dann neu (auch ohne App) und sie zeigt nur noch „vorbei“ mit ✕ zum Schließen.
struct VorlesungLiveAktivitaet: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: VorlesungAttribute.self) { context in
            LiveBanner(s: context.state, laufend: laeuft(context), vorbei: vorbei(context),
                       schluessel: context.attributes.schluessel)
                .activityBackgroundTint(Color(hex: 0x1C1C1E))
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "stundenplan://lageplan?termin=\(Int(context.state.start.timeIntervalSince1970))"))
        } dynamicIsland: { context in
            let s = context.state
            let farbe = Color(hex: s.farbe)
            let laufend = laeuft(context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.kurz).font(.headline).foregroundStyle(farbe)
                        Text(s.typ).font(.caption).foregroundStyle(.secondary)
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        if laufend {
                            Text("bis \(Zeit.uhrzeit(s.ende))").font(.headline).monospacedDigit()
                            Text("läuft").font(.caption).foregroundStyle(.secondary)
                        } else {
                            Text(timerInterval: Date()...max(Date(), s.start), countsDown: true)
                                .font(.headline).monospacedDigit()
                            Text("bis Beginn").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Label(s.raum, systemImage: "mappin.and.ellipse")
                            Spacer()
                            Text("\(Zeit.uhrzeit(s.start))–\(Zeit.uhrzeit(s.ende))")
                        }
                        .font(.subheadline.weight(.semibold))
                        ProgressView(timerInterval: s.start...s.ende, countsDown: false) {
                            EmptyView()
                        } currentValueLabel: {
                            EmptyView()
                        }
                        .tint(farbe)
                        if let d = s.danach {
                            Text("Danach: \(d)").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                Text(s.kurz)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(farbe)
            } compactTrailing: {
                Group {
                    if laufend {
                        // Während der Vorlesung: Raum statt Countdown
                        Text(LiveRaum.kurz(s.raum))
                    } else {
                        Text(timerInterval: Date()...max(Date(), s.start), countsDown: true)
                            .monospacedDigit()
                    }
                }
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: 56)
            } minimal: {
                Text(String(s.kurz.prefix(2)))
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(farbe)
            }
            .widgetURL(URL(string: "stundenplan://lageplan?termin=\(Int(s.start.timeIntervalSince1970))"))
            .keylineTint(farbe)
        }
    }

    /// Läuft die Vorlesung schon?
    private func laeuft(_ context: ActivityViewContext<VorlesungAttribute>) -> Bool {
        Date() >= context.state.start
    }

    /// Vorbei? (Veraltet = Ende erreicht, siehe LiveAktivitaet.swift)
    private func vorbei(_ context: ActivityViewContext<VorlesungAttribute>) -> Bool {
        context.isStale || Date() >= context.state.ende
    }
}

enum LiveRaum {
    /// "2.70.0.10" → "70/0.10"
    static func kurz(_ raum: String) -> String {
        guard let r = RaumTeile(raum) else { return raum }
        return "\(r.haus)/\(r.rest)"
    }
}

struct LiveBanner: View {
    let s: VorlesungAttribute.ContentState
    let laufend: Bool
    var vorbei = false
    var schluessel = ""

    /// ✕: schließt die Live-Aktivität direkt, ohne die App zu öffnen
    private var schliessen: some View {
        Button(intent: LiveAktivitaetSchliessen(schluessel: schluessel)) {
            Image(systemName: "xmark")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.8))
                .frame(width: 26, height: 26)
                .background(Circle().fill(.white.opacity(0.15)))
        }
        .buttonStyle(.plain)
    }

    var body: some View {
        if vorbei {
            HStack(spacing: 10) {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(Color(hex: s.farbe))
                Text("\(s.kurz) ist vorbei").font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                if let d = s.danach {
                    Text("· \(d)").font(.caption).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                }
                Spacer(minLength: 4)
                schliessen
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        } else {
            laufendAnsicht
        }
    }

    private var laufendAnsicht: some View {
        let farbe = Color(hex: s.farbe)
        return HStack(alignment: .center, spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(farbe)
                .frame(width: 6)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 4) {
                    if laufend {
                        Text("JETZT · BIS \(Zeit.uhrzeit(s.ende))")
                    } else {
                        Text("BEGINNT IN")
                        Text(timerInterval: Date()...max(Date(), s.start), countsDown: true).monospacedDigit()
                    }
                }
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.75))
                Text(s.titel)
                    .font(.headline)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text("\(s.typ) · \(s.raum) · \(Zeit.uhrzeit(s.start))–\(Zeit.uhrzeit(s.ende))")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(1)
                ProgressView(timerInterval: s.start...s.ende, countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .tint(farbe)
                if let d = s.danach {
                    Text("Danach: \(d)")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                        .lineLimit(1)
                }
            }
            VStack {
                schliessen
                Spacer(minLength: 0)
            }
        }
        .padding(14)
    }
}
