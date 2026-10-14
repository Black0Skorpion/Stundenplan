import ActivityKit
import WidgetKit
import SwiftUI

/// Darstellung der Live-Aktivität: Sperrbildschirm-Banner und Dynamic Island
struct VorlesungLiveAktivitaet: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: VorlesungAttribute.self) { context in
            LiveBanner(s: context.state)
                .activityBackgroundTint(Color(hex: 0x1C1C1E))
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "stundenplan://lageplan?termin=\(Int(context.state.start.timeIntervalSince1970))"))
        } dynamicIsland: { context in
            let s = context.state
            let farbe = Color(hex: s.farbe)
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
                        LiveTimer(s: s).font(.headline).monospacedDigit()
                        Text(Date() < s.start ? "bis Beginn" : "bis Ende").font(.caption).foregroundStyle(.secondary)
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
                LiveTimer(s: s)
                    .font(.caption.weight(.semibold))
                    .monospacedDigit()
                    .frame(maxWidth: 52)
            } minimal: {
                Text(String(s.kurz.prefix(2)))
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(farbe)
            }
            .widgetURL(URL(string: "stundenplan://lageplan?termin=\(Int(s.start.timeIntervalSince1970))"))
            .keylineTint(farbe)
        }
    }
}

/// Countdown bis Beginn bzw. Ende (läuft von selbst weiter)
struct LiveTimer: View {
    let s: VorlesungAttribute.ContentState

    var body: some View {
        let jetzt = Date()
        if jetzt < s.start {
            Text(timerInterval: jetzt...s.start, countsDown: true)
        } else {
            Text(timerInterval: jetzt...max(jetzt, s.ende), countsDown: true)
        }
    }
}

struct LiveBanner: View {
    let s: VorlesungAttribute.ContentState

    var body: some View {
        let farbe = Color(hex: s.farbe)
        let laufend = Date() >= s.start
        HStack(alignment: .center, spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(farbe)
                .frame(width: 6)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 4) {
                    Text(laufend ? "JETZT · NOCH" : "BEGINNT IN")
                    LiveTimer(s: s).monospacedDigit()
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
        }
        .padding(14)
    }
}
