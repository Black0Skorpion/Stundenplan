import WidgetKit
import SwiftUI

@main
struct StundenplanWidgets: WidgetBundle {
    var body: some Widget {
        StundenplanWidget()
        SperrWidget()
        MensaWidget()
        AbfahrtenWidget()
        VorlesungLiveAktivitaet()
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            WeckerAktivitaet()
        }
        #endif
    }
}
