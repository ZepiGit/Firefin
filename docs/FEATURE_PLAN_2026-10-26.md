# Umsetzungsplan Features (2026-10-26)

Grundlage: `docs/FEATURE_AUDIT_2026-10-26.md` (Punkte 1–5 der Kurzfassung),
die Jellyfin-OpenAPI-Spezifikationen 10.8.13, 10.9.11, 10.10.7, 10.11.0 und
stable 12.3.0 (lokal geprüft am 2026-10-26) und ein Plan-Review durch
`gpt-6.1-sol` (Runde 1, eingearbeitet). Routen ohne Vermerk sind in allen
geprüften Versionen vorhanden. "Laufzeit-Entfernung" einer Route ist aus der
Spezifikation allein nicht beweisbar (10.9 bedient versteckte Routen weiter);
daher bleibt überall ein 404-Rückfall.

Annahmen, weil die Fragen aus dem Feature-Audit offen sind: Autoplay der
nächsten Folge ist standardmäßig an; "Noch dabei?" fragt nach drei
automatischen Wechseln; Segment-Überspringen fragt per Button; keine
Versions-UI auf der Detailseite; Sortierung wird nicht je Bibliothek
gespeichert; `Outro`-Segmente lösen in diesem Plan nichts aus.

Reihenfolge der Umsetzung: 0 → 1 → 4 → 5b → 5c → 5d → 2 → 3 → 5a. Jeder
Schritt: Unit-Test, `testDebugUnitTest` + `lintDebug` grün, eigener Commit,
EN/DE-Strings synchron.

## 0. Pflicht: Jellyfin-Routen ab 10.9 und 12 (S–M)

Befund: Firefin nutzt `Users/{id}/Items`, `Users/{id}/Views`,
`Users/{id}/Items/Resume`, `Users/{id}/Items/Latest`, `Users/{id}/Items/{id}`,
`Users/{id}/FavoriteItems/{id}`, `Users/{id}/PlayedItems/{id}` und
`DELETE Videos/ActiveEncodings`. Ab 10.9 sind sie als veraltet aus der
Spezifikation verschwunden, in 12.3.0 fehlen sie ganz (Laufzeitverhalten
unverifiziert). Zusätzlich sendet `SessionReporter.cleanupTranscode` heute
`LiveStreams/Close` mit JSON-Body; alle Spezifikationen verlangen
`POST LiveStreams/Close?liveStreamId=` ohne Body.

| Alt (10.8) | Neu (ab 10.9) |
|---|---|
| `Users/{u}/Views` | `UserViews?userId=` |
| `Users/{u}/Items/Resume` | `UserItems/Resume?userId=` |
| `Users/{u}/Items/Latest` | `Items/Latest?userId=` |
| `Users/{u}/Items` | `Items?userId=` |
| `Users/{u}/Items/{id}` | `Items/{id}?userId=` |
| `Users/{u}/FavoriteItems/{id}` | `UserFavoriteItems/{id}?userId=` |
| `Users/{u}/PlayedItems/{id}` | `UserPlayedItems/{id}?userId=` |

Umsetzung in `JellyfinClient`:

- `ServerRoutes` je normalisierter Server-URL (inklusive Basispfad), nicht je
  Prozess: der Client überlebt Logout und Login mit anderem Server.
  Zustand: `modern`, `legacy` oder `unknown`.
- Bestimmung: einmal `GET System/Info/Public` (ohne Auth). `Version`
  `10.x` mit `x < 9` → `legacy`; sonst `modern`; `Version` fehlt oder Probe
  scheitert → `unknown`.
- Pfadbildung an einer Stelle: `userPath(modern: String, legacy: String)`.
  Bei `unknown` oder `modern` geht der moderne Pfad raus; antwortet der
  Server definitiv 404, wird einmal der alte Pfad versucht; gelingt er, wird
  `legacy` für diesen Server gespeichert. 401 und Abbruch bleiben wie heute.
- `DELETE Videos/ActiveEncodings` nur bei Major < 12 oder `unknown`; der
  bestehende 404-Schlucker bleibt. `LiveStreams/Close` als Query-Parameter,
  unabhängig vom Encodings-Gate.

Tests: `JellyfinClientTest` mit `System/Info/Public`-Fixture für 10.8.13
(alte Pfade), 10.11.0 (neue), 12.3.0 (neue, kein `ActiveEncodings`) und
"Probe scheitert, moderner Pfad 404 → alter Pfad, danach gemerkt";
`SessionReporterContractTest` auf `liveStreamId` als Query umstellen;
bestehende Pfad-Asserts (`Api22FlowTest`, `children`) auf die neuen Pfade.

## 1. Direkt spielbare Quelle bevorzugen (S–M)

Ist: `playbackInfo` liest bei gesetzter Sprachvorgabe zuerst die Metadaten,
pinnt die **erste** `MediaSource` als `MediaSourceId` und verhandelt dann;
andere Versionen sind damit raus. Ohne Vorgabe nimmt es die erste Quelle,
die irgendetwas kann. `DirectStreamUrl` existiert in keiner Spezifikation;
Remux kommt wie Transcode über `TranscodingUrl`.

Änderung:

1. PlaybackInfo **ohne** `MediaSourceId` und ohne Indizes (außer der Aufrufer
   pinnt eine Quelle).
2. Quelle wählen: explizit gepinnt → diese; sonst erste mit
   `SupportsDirectPlay`; sonst erste mit `TranscodingUrl`; Quellen ohne
   nutzbare URL (nur Flags) werden übersprungen; keine → bisheriger Fehler.
3. Sind Sprachvorgaben gesetzt, Indizes aus den `MediaStreams` **dieser**
   Quelle bestimmen; weichen sie von `DefaultAudioStreamIndex` /
   `DefaultSubtitleStreamIndex` ab oder ist eine Untertitel-Lieferung
   nötig, einmal PlaybackInfo mit `MediaSourceId` + Indizes nachverhandeln
   (wie heute maximal zwei Requests).
4. `DirectStreamUrl`-Zweig entfernen; `playMethod` = `DirectPlay` ohne
   `TranscodingUrl`, sonst `DirectStream` wenn `SupportsDirectStream &&
   !SupportsDirectPlay`, sonst `Transcode`. `isTranscode`/`isHls` unverändert
   aus der URL.

Tests: zwei Quellen (erste nur Transcode, zweite Direct Play) → zweite;
Remux über `TranscodingUrl`; Quelle nur mit Flags → übersprungen;
explizite Pinnung; Sprachvorgabe mit abweichendem Index → zweiter Request
trägt `MediaSourceId` der gewählten Quelle. Bestehenden
`DirectStreamUrl`-Test ersetzen.

## 2. Nächste Folge + "Noch dabei?" (M–L)

- Daten: `MediaItem.seriesId` (`SeriesId`). `DetailActivity` gibt sie als
  Extra an `PlayerActivity`.
- Suche: nach dem Start einmal
  `GET Shows/{seriesId}/Episodes?userId=&startItemId={itemId}&limit=2&isMissing=false&fields=Overview`;
  gültig nur, wenn das erste Element die laufende Folge ist, das zweite ist
  die nächste. Antwort gehört zum Versuch (`attempt`-Zähler); späte
  Antworten werden verworfen.
- Versuchs-Modell in `PlayerActivity`: jeder Wechsel/Retry ist ein
  `attempt` mit eigenem `ended`-Latch (ENDED nur einmal verarbeiten),
  eigenem Countdown-Job, eigenem Segment-Job (Punkt 3) und eigener
  `SessionReporter`-Session. Der Reporter, den ENDED oder ein Fehler über
  `session.finish()` beendet, wird in `pendingTerminal` gehalten;
  `switchTo` wartet `pendingTerminal ?: session.finish()` ab, bevor es
  verhandelt (genau ein Stop je Versuch, Reihenfolge Stop → Playing).
- `STATE_ENDED`: ohne nächste Folge `finish()` wie heute. Mit nächster
  Folge: Overlay unten rechts ("Nächste Folge · S01E05 · Name", Buttons
  "Jetzt abspielen (10)" mit Fokus und "Schließen"). Einstellung
  `autoPlayNext` an → Countdown 10 s, eine Textänderung je Sekunde; aus →
  kein Countdown. Countdown wird in `onPause` abgebrochen (kein Wechsel im
  Hintergrund) und in `onResume` nicht neu gestartet; der Nutzer wählt.
- "Noch dabei?": `autoAdvances` zählt nur erfolgreiche automatische
  Wechsel; jeder Tastendruck setzt auf 0. Ab 3 statt Countdown ein Overlay
  "Noch dabei?" mit "Weiter" (Fokus) und "Beenden", ohne Timeout.
- Tasten: ist ein Overlay sichtbar, gehen alle Tasten zuerst an das Overlay
  (D-Pad navigiert dessen Buttons, CENTER aktiviert, BACK = "Schließen"
  bzw. "Beenden" und verbraucht das zugehörige ACTION_UP wie `consumedBack`).
  Erst danach greifen die Controller-Kurzbefehle.
- `switchTo(next)`: beendet den Versuch (s. o.), `playbackInfo(next.id)`
  ohne Quellen-Pinnung und mit Indizes `null` (Sprachvorgaben greifen),
  Media3-Overrides löschen (`clearOverrides`), `itemId`, `sourceId`, Titel,
  Tracks, `isTranscode/isHls`, `PlaybackTimeline` neu, `prepare` auf
  `next.resumeTicks` (0 bei ungesehen), neue Session, neue Suche der
  übernächsten Folge, Segmente neu laden. Retry/Spurwechsel behalten wie
  heute Quelle, Indizes und Pausenzustand; Kontoprüfung bleibt.
- Einstellung `StoredPreferences.autoPlayNext: Boolean = true` in
  `PreferenceStore` (lesen, schreiben, `serializeStored`) und
  `SettingsActivity` Gruppe Playback. Strings EN/DE.
- Tests: reine Logik `NextUpGate` (Countdown-Entscheid, Zähler, Reset,
  Latch), `JellyfinClientTest` für Pfad und Gültigkeitsregel des ersten
  Elements, `PlaybackSessionTrackerTest` um "Stop vor nächstem Playing"
  erweitern, `PreferenceStore`-Roundtrip mit Map-basierten SharedPreferences.
  Emulator: Fixture liefert `Shows/{id}/Episodes`, aber kein abspielbares
  Medium; der Wechsel selbst bleibt dort ungeprüft.

## 3. Intro/Rückblick überspringen (M)

- Route `GET MediaSegments/{itemId}` (ab 10.10; 404 → leer). Felder `Type`
  (`Intro`, `Recap`, `Preview`, `Commercial`, `Outro`, `Unknown`),
  `StartTicks`, `EndTicks`; ungültige Grenzen (Ende ≤ Start) verwerfen.
- `JellyfinClient.mediaSegments(itemId)`; Laden je Versuch nach `prepare`,
  späte Antworten verworfen. Nur `Intro`, `Recap`, `Preview`, `Commercial`
  zeigen einen Button; `Outro` wird in diesem Plan ignoriert.
- Takt: eigener 1-s-Job je Versuch, nur bei vorhandenen Segmenten,
  getrennt vom 10-s-Reporter. Position über `actualPositionMs` (absolut).
  Reine Logik `SegmentCue.active(segments, absMs, dismissed)`.
- Sprung in **absoluten** Koordinaten: `seekAbsolute(absMs)` =
  `seekTo(absMs - transcodeOffsetMs)`; der TS-Neustart in `seekTo` nutzt
  `engine.prepare(rebuilt, 0, subtitleUrl, subtitleMime)` statt
  `MediaItem.fromUri`, damit externe Untertitel und `engine.currentUrl`
  erhalten bleiben (heute ein Fehler im Seek-Pfad).
- UI: Button "Intro überspringen" / "Rückblick überspringen" /
  "Vorschau überspringen" / "Werbung überspringen" unten rechts; erscheint
  beim Eintritt, verschwindet am Segmentende oder nach Sprung. Abgelehnt
  (BACK bei sichtbarem Button) → Schlüssel `itemId:start` in `dismissed`;
  bleibt bei Retry/Spurwechsel, wird bei Folgenwechsel geleert.
- Tasten: Button sichtbar und Controller verborgen → CENTER springt, BACK
  lehnt ab (mit verbrauchtem ACTION_UP); Reihenfolge wie in Punkt 2 vor den
  Controller-Kurzbefehlen.
- Tests: `SegmentCue`, Parser mit Fixture, 404-Fall, Grenzen; Koordinaten-
  Umrechnung als reine Funktion mit Test.

## 4. Kontextmenü auf Karten (S–M)

- `MediaCardAdapter`: optionaler Callback `onMenu: ((MediaItem) -> Unit)?`;
  nur Jellyfin-Adapter setzen ihn (Seerr-Karten mit synthetischen IDs
  bekommen keinen). Auf der Karte `setOnKeyListener` für `KEYCODE_MENU`,
  ACTION_DOWN, `repeatCount == 0`.
- `ui/CardMenu.kt`: `entries(item, modernRoutes): List<Entry>` (reine
  Funktion, getestet) und `show(activity, item, onChanged)`. Einträge:
  gesehen/ungesehen (`setPlayed`); Favorit (`setFavorite`); "Aus
  Weiterschauen entfernen" nur bei `resumeTicks > 0` **und** modernen
  Routen: `POST UserItems/{itemId}/UserData?userId=` mit
  `{"PlaybackPositionTicks": 0}` (ab 10.9; `MarkUnplayed` würde PlayCount,
  LastPlayedDate und Played mitlöschen); auf 10.8 ausgeblendet. "Zur Serie"
  bei Episoden mit `seriesId`.
- Nach der Aktion lädt der Helfer das Item neu (`client.item(id)`) und
  meldet `onChanged(item)`. Besitzer aktualisieren ihre Listen per ID:
  Home ersetzt das Item in den gecachten Latest-Reihen und ruft
  `loadHome(refreshOnly = true)` (Resume/Next-up neu, Fokus-Fallback);
  Bibliothek ersetzt im Adapter und entfernt Items, die Filter/Favoriten-
  Grid nicht mehr erfüllen (`total` mitführen); Suche ersetzt in `local`;
  Detail-Kinder ersetzen im Adapter.
- Busy-Guard wie `toggleUserData`. Strings EN/DE. Emulator: MENU auf API 22
  prüfen (Druck, Halten).

## 5. Bündel

### 5a Quick Connect (M)

- Routen: `GET QuickConnect/Enabled`; `QuickConnect/Initiate` (GET bei
  `legacy`, sonst POST; bei `unknown` POST und bei 404/405 GET);
  `GET QuickConnect/Connect?secret=` (`Authenticated`, `Secret`, `Code`);
  `POST Users/AuthenticateWithQuickConnect` `{"Secret": …}`.
- Versuch: `JellyfinClient.quickConnect(server)` arbeitet auf einem an die
  Adresse gebundenen Transport-Snapshot (nicht auf dem persistierten
  Server), liefert `code` per Callback und wartet im Aufruf bis
  `Authenticated` (Polling 5 s, Abbruch nach 5 min → Fehler), speichert dann
  die Session wie `login()`. Das `Secret` bleibt im Speicher des Versuchs.
- `LoginActivity`: Button "Mit Quick Connect anmelden" immer sichtbar.
  Ablauf: Adresse normalisieren → http-Bestätigung → `Enabled` prüfen
  (false → Fehlertext) → Code-Panel (36 sp, Hinweis, "Abbrechen" mit
  Fokus). Eigener `attemptJob`; Abbrechen, BACK oder Adressänderung
  brechen nur den Versuch ab; `onDestroy` den Scope. Späte Antworten nach
  Abbruch werden verworfen. Kein Seerr-Auto-Bridge; Prompt bleibt.
- Tests: MockWebServer Initiate → Connect false → true → Authenticate →
  Session; 401/404/Timeout/Abbruch; Secret nie in SharedPreferences.

### 5b Startseite: Favoriten-Reihe, Reihenfolge, Ausschlüsse (S–M)

- `HomeRow` bekommt `kind` (`resume`, `nextUp`, `favorites`, `latest`);
  Favoriten-Reihe: `items(filters = "IsFavorite", includeTypes =
  "Movie,Series", sortBy = "DateCreated", sortOrder = "Descending",
  limit = 12)`, Titel `R.string.favorites`, Titelklick öffnet das Favoriten-
  Grid (eigene Aktion, nicht über eine Bibliotheks-ID).
- Reihenfolge: `HomeSections.order(stored, rows)` (reine Funktion): Reihen
  nach `homeSectionOrder`, unbekannte Schlüssel ignoriert, fehlende
  weggelassen. Vorgabe bei fehlendem Wert `resume,nextUp,favorites,latest`;
  ein bereits gespeicherter alter Wert (`resume,nextUp,latest`) bleibt und
  zeigt keine Favoriten, bis ein Preset gewählt wird (Opt-in, keine
  Migration). Latest-Gruppe wird vor der Ordnung gecacht; Ordnung und
  Favoriten-Nachladen gelten in beiden Ladepfaden (`refreshOnly` lädt
  Resume, Next-up und Favoriten neu).
- `SettingsActivity` Gruppe Interface: "Startseite" → Listendialog
  "Weiterschauen zuerst", "Neu zuerst", "Favoriten zuerst", "Ohne
  Favoriten".
- Ausschlüsse: im vollen Laden `GET Users/Me` parallel zu den Views;
  `Configuration.LatestItemsExcludes` filtert die Latest-Reihen. Fehler →
  keine Filterung.
- Tests: `HomeSections.order` (Reihenfolge, Weglassen, alter Wert),
  `PreferenceStore`-Roundtrip.

### 5c Bibliothek: Filter und Genre (S–M)

- `JellyfinClient.items(filters: String? = null, genreIds: String? = null)`;
  `favorites`-Flag wird `filters = "IsFavorite"`. `genres(parentId,
  includeTypes)` über `GET Genres?parentId=&userId=&includeItemTypes=&sortBy=SortName`.
- `LibraryActivity`: Header-Buttons "Filter" (Alle → Ungesehen → Gesehen →
  Favoriten) und "Genre" (Listendialog "Alle" + Genres, beim ersten Öffnen
  geladen); beide setzen die Seite zurück; Titelzeile zeigt Filter und
  Genre. Im Favoriten-Grid ausgeblendet.
- Tests: Pfad-Asserts für `Filters=` und `GenreIds=`, Genre-Parser.

### 5d Untertitelgröße und Sprachen (S)

- `StoredPreferences.subtitleSize: Int = 0` (0 normal, 1 groß, 2 sehr
  groß) in `PreferenceStore` und `serializeStored`. `PlayerActivity`:
  `playerView.subtitleView?.setFixedTextSize(COMPLEX_UNIT_SP, 20/26/32)`,
  `setStyle(CaptionStyleCompat(weiß, halbtransparent schwarz, …))`,
  `setApplyEmbeddedStyles(false)` (Media3 1.8.1 `SubtitleView`). Wirkt auf
  Textspuren; eingebrannte bleiben.
- Sprachen: `preferences/Languages.kt` mit Codes `de, en, fr, es, it, nl,
  pt, ja` und expliziten Aliasen (bibliografisch/terminologisch: `ger/deu`,
  `fre/fra`, `dut/nld`, …); `StreamSelection.index` vergleicht den
  Sprachteil (Region abgeschnitten) über Aliasliste plus
  `Locale(code).isO3Language` mit `MissingResourceException`-Fang.
  `LocalizationParityTest` prüft zusätzlich, dass die EN/DE-Arrays
  genau `Languages.CODES.size` Einträge haben.
- Tests: `StreamSelection` für `fra`/`fre`, `pt-BR`, unbekannter Code.

## Nicht in diesem Plan

Versions-Button auf der Detailseite, Automatik-Sprung und `Outro`-Kopplung
bei Segmenten, Server-Sprachvorgaben (`Users/Me`), Sortierung je Bibliothek
merken, Buchstaben-Sprung, Benutzerwechsel, Seerr-Erweiterungen, abspielbare
Medien-Fixtures für den Emulator.

## Prüfung

Nach jedem Schritt `./gradlew --no-daemon :app:testDebugUnitTest
:app:lintDebug`; am Ende der vollständige Verifikationslauf mit beiden APKs
und dem Verifier. Emulator (API 22, ohne KVM) gegen den Fixture-Server:
Kontextmenü (MENU-Taste), Bibliotheksfilter und Genre, Startseiten-
Reihenfolge und Favoriten-Reihe, Overlay "Nächste Folge" nur bis zum
Overlay (kein abspielbares Medium im Fixture); Quick Connect und Segmente
per JVM-Test und Fixture-Antworten.
