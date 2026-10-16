#!/usr/bin/env python3
"""Erstellt den Suchkatalog für die App: alle Veranstaltungen aus dem öffentlichen
PULS-Vorlesungsverzeichnis (ohne Anmeldung) mit Titel, Nummer und Modul.

Ausgabe: puls-<semester>.json (lesbar) und puls-<semester>.deflate (komprimiert für die App).
Läuft wöchentlich auf GitHub (Workflow „PULS-Katalog“), fragt PULS bewusst langsam ab.
"""
import html, json, re, sys, time, urllib.request, zlib, datetime

BASIS = "https://puls.uni-potsdam.de/qisserver/rds"
UA = {"User-Agent": "Stundenplan-App Katalog (github.com/Black0Skorpion/Stundenplan)"}
PAUSE = 0.5


def semester_code(d=None):
    d = d or datetime.date.today()
    if d.month >= 9: return f"{d.year}2"
    if d.month <= 2: return f"{d.year - 1}2"
    return f"{d.year}1"


def hol(url):
    for versuch in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40) as r:
                t = r.read().decode("utf-8", "ignore")
            time.sleep(PAUSE)
            return t
        except Exception as e:
            print("  Fehler", e, url[-80:], file=sys.stderr)
            time.sleep(5 * (versuch + 1))
    return ""


def text(s):
    s = re.sub(r"<[^>]+>", "", s)
    return " ".join(html.unescape(s).split())


def eintraege(seite, pfad):
    tiefe = 2 if not pfad else pfad.count("|") + 2
    ordner, veranst = [], []
    for href, inhalt in re.findall(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', seite, re.S):
        href = html.unescape(href)
        name = text(inhalt)
        m = re.search(r"root1\d{5}=([0-9|%7C]+)", href)
        if "state=wtree" in href and "P.vx=kurz" in href and m:
            p = m.group(1).replace("%7C", "|")
            if p.count("|") + 1 == tiefe and (not pfad or p.startswith(pfad + "|")) and name:
                ordner.append((name, p))
        elif "publishid=" in href and name:
            m2 = re.search(r"publishid=(\d+)", href)
            if m2: veranst.append((m2.group(1), name))
    return ordner, veranst


def main():
    sem = sys.argv[1] if len(sys.argv) > 1 else semester_code()
    url = lambda p: f"{BASIS}?state=wtree&search=1&trex=step&root1{sem}={(p or '0').replace('|', '%7C')}&P.vx=kurz"
    katalog = {}   # publishid -> [titel, set(kontexte)]
    stapel = [("", [])]
    gesehen = set()
    seiten = 0
    while stapel:
        pfad, namen = stapel.pop()
        if pfad in gesehen: continue
        gesehen.add(pfad)
        seite = hol(url(pfad))
        seiten += 1
        ordner, veranst = eintraege(seite, pfad)
        for pid, titel in veranst:
            k = katalog.setdefault(pid, [titel, set()])
            # Kontext: die letzten zwei Ebenen (z. B. Studiengang · Modul)
            if namen: k[1].add(" · ".join(namen[-2:]))
        for name, p in reversed(ordner):
            stapel.append((p, namen + [name]))
        if seiten % 100 == 0:
            print(f"{seiten} Seiten, {len(katalog)} Veranstaltungen, {len(stapel)} offen", file=sys.stderr)
    daten = {
        "semester": sem,
        "stand": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="minutes"),
        "veranstaltungen": [[pid, t, sorted(k)[:4]] for pid, (t, k) in sorted(katalog.items(), key=lambda x: x[1][0].lower())],
    }
    roh = json.dumps(daten, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    open(f"puls-{sem}.json", "wb").write(roh)
    c = zlib.compressobj(9, zlib.DEFLATED, -15)   # roh-DEFLATE: lässt sich auf iOS direkt entpacken
    open(f"puls-{sem}.deflate", "wb").write(c.compress(roh) + c.flush())
    print(f"Fertig: {seiten} Seiten, {len(katalog)} Veranstaltungen, {len(roh)} Bytes", file=sys.stderr)


if __name__ == "__main__":
    main()
