import SwiftUI
import WidgetKit
#if canImport(AlarmKit)
import AlarmKit
import ActivityKit

/// Anzeige beim Schlummern (AlarmKit braucht dafür eine Live-Aktivität): Countdown bis zum erneuten Klingeln.
@available(iOS 26.0, *)
struct WeckerAktivitaet: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AlarmAttributes<WeckerDaten>.self) { kontext in
            WeckerZeile(zustand: kontext.state, gross: true)
                .padding(16)
                .activityBackgroundTint(.black.opacity(0.75))
                .activitySystemActionForegroundColor(.orange)
        } dynamicIsland: { kontext in
            DynamicIsland {
                DynamicIslandExpandedRegion(.center) {
                    WeckerZeile(zustand: kontext.state, gross: true)
                }
            } compactLeading: {
                Image(systemName: "zzz").foregroundStyle(.orange)
            } compactTrailing: {
                WeckerRest(zustand: kontext.state)
                    .frame(maxWidth: 52)
            } minimal: {
                Image(systemName: "alarm").foregroundStyle(.orange)
            }
        }
    }
}

@available(iOS 26.0, *)
private struct WeckerRest: View {
    let zustand: AlarmPresentationState

    var body: some View {
        switch zustand.mode {
        case .countdown(let c):
            Text(timerInterval: Date()...max(Date(), c.fireDate), countsDown: true)
                .monospacedDigit()
                .foregroundStyle(.orange)
        default:
            Image(systemName: "alarm").foregroundStyle(.orange)
        }
    }
}

@available(iOS 26.0, *)
private struct WeckerZeile: View {
    let zustand: AlarmPresentationState
    let gross: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "zzz")
                .font(.title2)
                .foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 2) {
                Text("Schlummern").font(.headline).foregroundStyle(.white)
                Text("Der Wecker klingelt gleich nochmal").font(.caption).foregroundStyle(.white.opacity(0.75))
            }
            Spacer()
            WeckerRest(zustand: zustand)
                .font(.title2.weight(.semibold))
        }
    }
}
#endif
