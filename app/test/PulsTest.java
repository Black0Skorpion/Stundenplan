import de.stundenplan.widget.Puls;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Test für Puls.java (Android): lädt echte PULS-Seiten, Ausgabe im gleichen Format wie ios-app/Tests/PulsTest.swift. */
public class PulsTest {
    static String laden(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Stundenplan-Test (github.com/Black0Skorpion/Stundenplan)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            in.transferTo(b);
            Thread.sleep(1500);
            return b.toString(StandardCharsets.UTF_8);
        }
    }

    static String opt(String s) { return s == null ? "" : s; }

    public static void main(String[] a) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY);
        f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        String sem = Puls.semesterCode(System.currentTimeMillis());
        System.out.println("Semester: " + sem + " " + Puls.semesterName(sem));
        int fehler = 0;
        for (String id : new String[]{"123991", "124000", "124001", "123976", "123977", "122607", "122492"}) {
            Puls.Veranstaltung v = Puls.veranstaltung(laden(Puls.veranstaltungURL(id)), id);
            if (v == null) { System.out.println("FEHLER: " + id + " nicht lesbar"); fehler++; continue; }
            System.out.println("\n== " + v.titel + " [" + v.art + ", " + v.semester + "] Gruppen: " + v.gruppen.size()
                    + " Moodle: " + (v.moodle != null) + " Frist: " + (v.belegfrist == null ? "-" : v.belegfrist));
            for (Puls.Regel r : v.gemeinsam()) {
                String wann = r.rhythmus.toLowerCase().contains("einzel") && r.beginn != null ? r.beginn : r.tag;
                String kurz = r.art + " · " + wann + " " + r.von + "–" + r.bis + (r.raum.isEmpty() ? "" : " · " + r.raeume());
                System.out.println("   gemeinsam: " + kurz + " " + r.rhythmus + " " + opt(r.beginn) + "–" + opt(r.ende) + " Ausfälle: " + r.ausfaelle);
            }
            for (Puls.Gruppe g : v.gruppen) {
                List<String> k = new ArrayList<>();
                for (Puls.Slot s : Puls.slots(v.eigenes(g))) k.add(s.kurz());
                System.out.println("   Gruppe " + g.nummer + ": " + String.join(" | ", k));
            }
            List<Puls.EinzelTermin> t = Puls.termine(v, v.gruppen.isEmpty() ? null : v.gruppen.get(0).nummer, null);
            List<String> erste = new ArrayList<>();
            for (int i = 0; i < Math.min(4, t.size()); i++) erste.add(f.format(t.get(i).start) + " " + t.get(i).art + " " + t.get(i).raum);
            System.out.println("   Termine Gruppe 1: " + t.size() + ", erste: " + String.join("; ", erste));
            if (t.isEmpty()) fehler++;
        }
        String pfad = "277989|279834|282154|278938|277613|280402";
        List<Puls.Eintrag> e = Puls.verzeichnis(laden(Puls.verzeichnisURL(pfad, sem)), pfad);
        List<String> n = new ArrayList<>();
        for (int i = 0; i < Math.min(6, e.size()); i++) n.add(e.get(i).name);
        System.out.println("\nVerzeichnis " + pfad + ": " + e.size() + " Einträge: " + String.join(" | ", n));
        List<Puls.Eintrag> oben = Puls.verzeichnis(laden(Puls.verzeichnisURL("", sem)), "");
        n.clear();
        for (int i = 0; i < Math.min(4, oben.size()); i++) n.add(oben.get(i).name);
        System.out.println("Oberste Ebene: " + oben.size() + ": " + String.join(" | ", n));
        String modul = pfad + "|277160";
        List<Puls.Eintrag> m = Puls.verzeichnis(laden(Puls.verzeichnisURL(modul, sem)), modul);
        n.clear();
        for (Puls.Eintrag x : m) n.add(x.name + " (" + (x.ordner() ? "o" + x.pfad : "v" + x.publishid) + ")");
        System.out.println("Modul GdP: " + String.join(" | ", n));
        if (e.isEmpty() || oben.isEmpty() || m.isEmpty()) fehler++;
        System.out.println("\nFehler: " + fehler);
        System.exit(fehler == 0 ? 0 : 1);
    }
}
