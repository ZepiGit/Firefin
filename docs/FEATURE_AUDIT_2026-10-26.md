# Feature-Audit Moonfin → Firefin (2026-10-26)

Frage: Welche Funktionen von Moonfin Core / Moonfin FireTV32 lassen sich mit
dem vorhandenen nativen Code und der AFTT-Hardware (Fire OS 5, API 22, ARMv7,
~1 GB RAM, D-Pad) in Firefin umsetzen?

Grundlage: Code-Lesen von `app/src/main/java/zepigit/firefin/app/**` auf
`main` @ `b4f51b0`, die Docs in `docs/` und die Legacy-Flutter-Quellen aus
`git archive 086af52 lib packages` (Moonfin FireTV32, Basis Moonfin Core 1.1.0).
Das Moonfin-Core-Repo im Netz und die Jellyfin-API-Doku wurden **nicht**
abgefragt; Routen ohne Beleg im Legacy-Code sind als "unverifiziert" markiert.
Kein AFTT-Gerät verfügbar: alle Aussagen zu CPU, RAM und Decoder sind
Erwartungen, nicht Messungen.

Aufwand: S < 1 Tag, M 1–3 Tage, L > 3 Tage.

## 1. Kurzfassung

Reihenfolge = Empfehlung für die nächsten Schritte.

1. **Nächste Folge + "Noch dabei?"** (M): `PlayerActivity` beendet sich heute am
   Ende (`endPlayback()` → `finish()`); Serien-Binge ist der häufigste TV-Fall.
2. **Versionswahl / direkt abspielbare Quelle bevorzugen** (S):
   `JellyfinClient.playbackInfo` nimmt die *erste* Quelle, die irgendetwas kann;
   bei 4K+1080p-Versionen wird so unnötig transkodiert.
3. **Intro/Abspann überspringen über `MediaSegments`** (M): ein Request pro
   Wiedergabe, eine Positionsprüfung pro Sekunde, ein Button.
4. **Kontextmenü auf Karten (MENU-Taste)** (S): "Aus Weiterschauen entfernen",
   gesehen/ungesehen, Favorit, "Zur Serie". Baut nur auf vorhandenen Routen auf.
5. **Quick Connect** (M): Anmeldung ohne Passwort-Tippen per D-Pad; kein neues
   Geheimnis wird gespeichert.
6. **Startseite**: Favoriten-Reihe, das schon gespeicherte
   `homeSectionOrder` endlich verdrahten, Server-`LatestItemsExcludes`
   respektieren (S).
7. **Bibliothek**: Filter (ungesehen/gesehen/Favoriten), Genre-Auswahl,
   Sortierrichtung, Sortierung je Bibliothek merken (S–M).
8. **Untertitel/Audio**: Schriftgröße und Hintergrund, mehr Sprachen,
   Server-Spracheinstellungen als Vorgabe (S).
9. **Session-Reporting ergänzen** (S): Stream-Indizes in Progress/Stopped,
   `Sessions/Capabilities/Full`.
10. **Kapitel-Liste im Player und Suche nach Typ gruppiert** (je S).

## 2. Tabelle aller Kandidaten

Spalte "Firefin": ja / teilweise / nein. "Basis" = vorhandene Klassen, auf
denen die Umsetzung aufsetzt.

| Feature | Was Moonfin tut (Beleg) | Firefin | Jellyfin-/Moonbase-Routen | Basis | AFTT-Fit (CPU, RAM, D-Pad, 10-Fuß) | Aufwand | Risiko | Empfehlung |
|---|---|---|---|---|---|---|---|---|
| Nächste Folge (Up Next) | Overlay mit Countdown am Ende, Verhalten `extended`, `minimal` oder `disabled`, Timeout (`next_up_overlay.dart`, `video_player_screen.dart:_checkNextUp`) | nein; nur *Continue*-Button auf der Serie (`DetailActivity.showNextEpisode`) | `GET Shows/{seriesId}/Episodes?UserId=&StartItemId=&Limit=2` (Parameter `StartItemId` unverifiziert) oder vorhandenes `Shows/NextUp?SeriesId=` | `PlayerActivity.renegotiate`, `JellyfinClient.nextUp` und `playbackInfo`, `SessionReporter` | gut: Text-Overlay, ein Player bleibt (kein zweiter Decoder) | M | mittel (Session-Wechsel im selben Player) | **umsetzen** – Kernfall Serie, Code für Quellwechsel existiert |
| "Noch dabei?" | Dialog nach 2/3/5/8 Folgen bzw. 1–4 h (`StillWatchingBehavior`) | nein | keine | wie oben | gut | S (nach Up Next) | niedrig | **umsetzen** – verhindert Dauer-Transcode bei schlafendem Zuschauer |
| Versionen (MediaSources) | Versionsauswahl auf Detail (`detail-action-version`) | teilweise: `playbackInfo(mediaSourceId)` vorhanden, keine UI, Auswahl nimmt erste Quelle | `Users/{id}/Items/{id}` (`MediaSources[].Id/Name`), `Items/{id}/PlaybackInfo` | `JellyfinClient.playbackInfo`, `DetailActivity` | sehr gut: Direct Play der 1080p-Version statt Server-Transcode | S | niedrig | **umsetzen** – direkt spielbare Quelle zuerst wählen |
| Intro/Abspann/Rückblick überspringen | `MediaSegmentService`: `skip`, `askToSkip` oder nichts je Segmenttyp, Mindestdauer 1 s/3 s, Button-Overlay (`skip_segment_overlay.dart`) | nein | `GET MediaSegments/{itemId}` (Beleg: Legacy-Paket `server_jellyfin`), Jellyfin ≥ 10.10 + Segment-Provider (z. B. Intro-Skipper-Plugin) | `PlayerActivity` (10-s-Loop, `seekTo`, `actualPositionMs`) | gut: 1 Vergleich je Sekunde, ein Button | M | mittel (TS-Transcode-Offset, Serverversion) | **umsetzen** |
| Kapitel | Kapitel-Dialog im Player (`_showChapters`) | nein | `Chapters` (Feld am Item, ggf. `Fields=Chapters`, unverifiziert); Kapitelbilder `Items/{id}/Images/Chapter/{i}` | `PlayerActivity` Top-Bar, `MediaItem` | gut ohne Bilder (nur Text) | S | niedrig | **umsetzen** (ohne Kapitelbilder) |
| Trailer lokal | über Detail (`item_detail_screen.dart`) | nein | `Users/{userId}/Items/{id}/LocalTrailers` bzw. `Items/{id}/LocalTrailers` (unverifiziert), Feld `LocalTrailerCount` | `DetailActivity.startPlayback` | gut (normaler Player) | S | niedrig | später – nur wenn lokale Trailer vorhanden |
| Trailer YouTube | `YouTubeStreamResolver`: Piped → Invidious → Innertube, fremde Hosts | nein | keine Jellyfin-Route; Drittanbieter | – | schlecht: Fremd-TLS auf Fire OS 5, VP9/AV1, fragile Scraper | M–L | hoch | nicht sinnvoll |
| Wiedergabegeschwindigkeit | Speed-Button (`_buildSpeedButton`) | nein; Media3-Settings-Popup bewusst ausgeblendet (`PlayerActivity.kt:112`) | keine (rein clientseitig; `PlaybackRate` im Report unverifiziert) | `PlaybackEngine.player.setPlaybackSpeed` | mittel: Sonic-Time-Stretch auf Cortex-A7, HLS-Transcode kann bei > 1,25× nicht nachkommen (unverifiziert) | S | mittel | später |
| Bildformat/Zoom | Fit/Crop/Stretch (`player_zoom_mode`) | nein | keine | `PlayerView.resizeMode` | gut | S | niedrig | später – billig, seltener Bedarf |
| Untertitel-Stil | Größe, Farbe, Kontur, Hintergrund, Position (`subtitles_text_size` u. a.) | nein (Media3-Standard / System-Captioning) | keine | `PlayerView.subtitleView` (`setFixedTextSize`, `setStyle(CaptionStyleCompat)`, `setBottomPaddingFraction`) | gut; gilt nur für clientseitig gerenderte Text-Untertitel | S | niedrig | **umsetzen** (Größe + Hintergrund) |
| Audio-/Untertitel-Vorgaben je Nutzer | `pref_audio_language`, `pref_subtitle_language`, `subtitles_default_to_none` | teilweise: lokal je Konto (`StoredPreferences`), nur `de` und `en`, Aliasliste ebenso (`StreamSelection.index`) | `Users/Me` → `Configuration.AudioLanguagePreference`, `SubtitleLanguagePreference`, `SubtitleMode`, `PlayDefaultAudioTrack` | `PreferenceStore`, `StreamSelection`, `JellyfinClient.playbackInfo`, `SettingsActivity` | gut | S | niedrig | **umsetzen** – mehr Sprachen, Server-Wert als Vorgabe |
| Quick Connect | Code-Anzeige + Polling im Login (`login_screen.dart`, `quick_connect_state.dart`) | nein (ausdrücklich ausgelassen, `FIREFIN_MIGRATION.md`) | `QuickConnect/Initiate`, `QuickConnect/Connect?Secret=`, `Users/AuthenticateWithQuickConnect` (Beleg: Legacy); `QuickConnect/Enabled` unverifiziert | `LoginActivity`, `JellyfinClient.login` (Session-Speicherung), `ServerTransport` | sehr gut: kein Tippen per D-Pad | M | niedrig–mittel (Seerr-Auto-Bridge braucht Passwort → Fallback-Prompt) | **umsetzen** |
| Quick Connect autorisieren (anderes Gerät) | `QuickConnect/Authorize` | nein | `POST QuickConnect/Authorize?Code=` | `SettingsActivity` | mittel (Code tippen) | S | niedrig | später |
| Server wechseln / mehrere Server | Serverliste, Discovery, Multi-Server-Bibliotheken (`multi_server_repository.dart`, `server_select_screen.dart`) | nein; ein Konto in `SessionStore`, Server/Name werden nach Logout vorbelegt | `System/Info/Public`; Discovery per UDP 7359 (unverifiziert) | `SessionStore`, `ServerTransport.snapshot` (bereits kontowechselsicher), kontogehashte `PreferenceStore`/Bild-Caches | gut, aber selten gebraucht | M | mittel (mehrere Tokens im Klartext-SharedPreferences) | später |
| Benutzer wechseln | Nutzerliste mit Bildern (`Users/Public`), Auto-Login-Optionen | nein | `GET Users/Public`, dann Passwort oder Quick Connect | `LoginActivity`, `SessionStore` | gut mit Quick Connect | M | mittel (Token-Liste) | später – nach Quick Connect |
| Favoriten-Reihe auf Home | Home-Abschnitte, Favoriten-Screen | teilweise: Favoriten nur als Grid (`LibraryActivity favorites=true`) | `Users/{id}/Items?Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series` | `JellyfinClient.items`, `HomeActivity.loadHome`, `HomeRow` | gut (+1 Request, 12 Poster) | S | niedrig | **umsetzen** |
| Reihenfolge der Home-Abschnitte | `home_sections_config` (11 Typen, an oder aus, Reihenfolge) | nein; `StoredPreferences.homeSectionOrder` wird gespeichert, aber von `HomeActivity` nie gelesen | keine | `StoredPreferences`, `PreferenceStore`, `HomeActivity`, `SettingsActivity` | gut | S | niedrig | **umsetzen** |
| "Neu hinzugefügt" je Bibliothek | Latest-Reihen, `recentlyReleased` | teilweise: eine Latest-Reihe je Bibliothek der Typen movies, tvshows und mixed, Server-Reihenfolge der Views, Ausschlüsse ignoriert | `Users/Me` → `Configuration.LatestItemsExcludes`, `MyMediaExcludes`, `OrderedViews` (wirken auf `Users/{id}/Views` vermutlich schon, unverifiziert) | `HomeActivity.loadHome` | gut: spart Requests | S | niedrig | **umsetzen** (Ausschlüsse beachten) |
| Genres | Genre-Grid je Bibliothek, "Alle Genres" (`library_genres_screen.dart`, `all_genres_screen.dart`) | nein | `Genres?ParentId=&UserId=&IncludeItemTypes=`, `Items?GenreIds=` | `LibraryActivity`, `JellyfinClient.items` | gut als Listendialog, ohne Genre-Kachelbilder | S–M | niedrig | **umsetzen** (Dialog, keine Kacheln) |
| Filter | gesehen oder ungesehen, Favorit, Serienstatus, Buchstabe je Bibliothek gespeichert (`library_browse_view_model.dart`) | nein | `Filters=IsPlayed\|IsUnplayed\|IsFavorite`, `SeriesStatus=Continuing\|Ended`, `NameStartsWith=` | `LibraryActivity`, `JellyfinClient.items` | gut | S | niedrig | **umsetzen** (gesehen/Favorit); Buchstaben-Sprung später |
| Sortierung | Sortierfeld + Richtung je Bibliothek gespeichert | teilweise: 4 Optionen zyklisch, feste Richtung, nicht gespeichert | `SortBy=SortName\|DateCreated\|ProductionYear\|CommunityRating\|DatePlayed\|Runtime\|Random`, `SortOrder` | `LibraryActivity.sortOptions`, `PreferenceStore` | gut | S | niedrig | **umsetzen** |
| Sammlungen (BoxSets) | `collection_screen.dart` | ja: BoxSets im Film-Grid, Detail zeigt Kinder (`DetailActivity.showChildren`) | `Items?ParentId={boxSetId}` | vorhanden | gut | – | – | vorhanden; nur Suche ergänzen |
| Weiterschauen verwalten | kein Eintrag gefunden (grep nach `HideFromResume` und `removeFromResume` leer) | nein | `DELETE Users/{id}/PlayedItems/{itemId}` setzt Position zurück (serverseitiges Verhalten unverifiziert); alternativ `POST UserItems/{itemId}/UserData` mit `PlaybackPositionTicks: 0` (Jellyfin ≥ 10.9, unverifiziert) | `JellyfinClient.setPlayed`, `MediaCardAdapter`, `HomeActivity` | gut: MENU-Taste der Fire-TV-Fernbedienung | S | niedrig | **umsetzen** (als Kontextmenü) |
| Kontextmenü auf Karten | Track-/Aktionsdialoge, Overflow auf Detail | nein | vorhandene Favorit/Gesehen-Routen | `MediaCardAdapter`, `DetailActivity.toggleUserData` | gut | S | niedrig | **umsetzen** |
| Live TV | Kanäle, EPG, Aufnahmen, Timer, Live-Player (Legacy `lib › ui › screens › livetv`) | nein | `LiveTv/Channels?UserId=&AddCurrentProgram=true`, `PlaybackInfo` mit `AutoOpenLiveStream=true`, `LiveStreams/Close` (bereits in `SessionReporter`) | `PlaybackEngine`, `SessionReporter` (LiveStream-Cleanup vorhanden), `LibraryActivity`-Grid | Kanalliste: gut; EPG-Raster: schlecht (2D-Liste, viele Views) | M (nur Kanalliste) / L (EPG, DVR) | mittel | später – nur Kanalliste, falls genutzt (Frage 2) |
| Musik | Alben/Künstler/Songs, Queue, Lyrics, Hintergrund-Audio (`audio_player_screen.dart`, `audio_service_firetv32`) | nein (Musikbibliotheken ausgeblendet) | `Artists`, `Items?IncludeItemTypes=MusicAlbum\|Audio`, `Audio/{id}/Lyrics`, `Items/{id}/InstantMix` | `PlaybackEngine` | mittel: braucht Foreground-Service + MediaSession, neues Navigationsmodell | L | mittel | nicht sinnvoll (siehe 4) |
| Fotos | Foto-Player/Slideshow (`photo_player_screen.dart`) | nein | `Items?IncludeItemTypes=Photo,PhotoAlbum`, Bild serverseitig skaliert `Items/{id}/Images/Primary?maxWidth=1920` | `ImageLoader` (Bucket bis 960, RGB_565) | mittel: ein 1920er-Bild ≈ 4 MB RGB_565; Slideshow statisch möglich | M | mittel | später – geringe Priorität |
| Bücher/Comics | PDF/EPUB/CBZ/CBR/7z-Reader (`book_reader_screen.dart`, `pdfrx_firetv32`, `rar_fork`, `archive_extract`) | nein | `Items/{id}/Download` | – | schlecht | L | hoch | nicht sinnvoll (siehe 4) |
| Seerr Personen | Personenseite + Filmografie (`seerr_person_screen.dart`) | nein | via Moonbase, Routen siehe Anhang A1 | `SeerrClient.api` (generisch), `SeerrDetailActivity`, `SeerrActivity`-Grid | gut | M | niedrig | später |
| Seerr weitere Kategorien | upcoming und top, Genre-Slider, Studios, Networks, Empfehlungen/Ähnliche (`seerr_row_config.dart`) | teilweise: trending, movie, tv | Routen siehe Anhang A2 | `SeerrClient.page`, `SeerrActivity` Kategorie-Dialog | gut | S | niedrig | später – billig, einfach in den Kategorie-Dialog |
| Seerr 4K | `is4k` im Request | nein (`is4k:false` fest, `SeerrModels.kt:46`) | Request-Route mit `is4k:true`, siehe Anhang A3 | `SeerrModels.requestBody` | AFTT spielt max. 1080p | S | niedrig | nicht sinnvoll |
| Seerr Admin (Freigeben/Ablehnen) | `approveRequest` und `declineRequest` (`seerr_repository.dart:514`) | nein | Routen siehe Anhang A4; Weiterleitung von POST durch Moonbase unverifiziert | `SeerrClient`, "Meine Anfragen"-Liste | gut (zwei Buttons) | M | mittel (Berechtigungen, Proxy) | später – Frage 4 |
| Fernsteuerung anderer Geräte | Remote-Dialog mit Befehlen aus `SupportedCommands`, "Abspielen auf" (`remote_control_dialog.dart`, `remote_play_to_session_dialog.dart`) | teilweise: Play/Pause, Stop (`RemoteActivity`) | `Sessions/{id}/Playing/{Seek\|NextTrack\|PreviousTrack\|Pause\|Unpause}`, `Sessions/{id}/Command`, `POST Sessions/{id}/Playing?ItemIds=&PlayCommand=PlayNow` | `RemoteActivity`, `JellyfinClient.sendCommand` | gut | S | niedrig | später – Nutzen auf dem TV gering |
| Firefin fernsteuerbar | WebSocket + Capabilities, Befehle Play/Playstate/GeneralCommand (`socket_handler.dart`, `session_repository.dart`) | nein | `Sessions/Capabilities/Full`, WebSocket-Route siehe Anhang A5 (Auth per Header unverifiziert; Token in URL widerspricht `SECURITY.md`) | OkHttp-WebSocket (in OkHttp 4.12 enthalten), `PlayerActivity` | mittel: Dauerverbindung im Vordergrund | M–L | mittel | später |
| Bildschirmschoner | nur Einstellungen (`screensaver_settings_screen.dart`); keine Implementierung im Legacy-`lib` gefunden (grep) | System-Schoner: `keepScreenOn` nur bei aktiver Wiedergabe | – | – | eigener Schoner = zusätzliche Decodes | – | – | nicht sinnvoll (Fire-OS-Schoner genügt) |
| Theme/Hintergrund | App-Themes, Fokusfarbe, Blur, Media Bar, Kartengröße, saisonale Effekte, Uhr (`user_preferences.dart`) | teilweise: Backdrop an oder aus | – | `SettingsActivity`, Tokens in `values/` | Blur/Media Bar/Effekte: schlecht; Uhr: gut | S (Uhr) | niedrig | Uhr im Kopf später; Rest nicht sinnvoll |
| Gesehen-Markierung auf Karten | `pref_watched_indicator_behavior`, ungesehene Anzahl | teilweise: "✓"/"▶" im Kartentext, Fortschrittsbalken (`Adapters.kt:54-60`) | `UserData.UnplayedItemCount` | `MediaItem`, `MediaCardAdapter` | gut | S | niedrig | später – Zähler für Serien/Staffeln |
| Bewertungen | MDBList/TMDB-Zusatzbewertungen mit eigenem API-Key, Badges | nein | Server-Felder `CommunityRating`, `CriticRating`, `OfficialRating` | `MediaItem.from`, `DetailActivity.meta` | gut (Text) | S | niedrig | Serverwerte später; MDBList/TMDB nicht sinnvoll |
| Darsteller & "Mehr davon" | Cast & Crew, More Like This auf Detail | nein | `People` am Item, `Items?PersonIds=`, `Items/{id}/Similar?UserId=&Limit=` | `DetailActivity`, `MediaCardAdapter`, `LibraryActivity` | mittel: weitere Bildreihe; Detail soll in einen Screen passen (`e2a1a1e`) | M | niedrig | später |
| Suche | Gruppen je Typ (Filme, Serien, Episoden, Sammlungen, Personen, …) (`search_view_model.dart:63-76`) | teilweise: eine Jellyfin-Reihe (Movie, Series, Episode) + Seerr-Reihe | `Users/{id}/Items?searchTerm=&IncludeItemTypes=Movie,Series,Episode,BoxSet` | `SearchActivity.SearchRowsAdapter` | gut | S | niedrig | **umsetzen** (clientseitig gruppieren, ein Request) |
| Session-Reporting | Capabilities, Stream-Indizes im Report | teilweise: Playing/Progress/Stopped mit Position, Pause, PlayMethod; Cleanup `ActiveEncodings`/`LiveStreams` | `Sessions/Playing*` mit `AudioStreamIndex`, `SubtitleStreamIndex`; `Sessions/Capabilities/Full` | `SessionReporter`, `PlayerActivity` | gut | S | niedrig | **umsetzen** |
| Sprunglänge / Rückspulen nach Pause | `skipBackLength`, `skipForwardLength`, `unpauseRewindDuration` | nein (fest 10 s) | keine | `PlayerActivity.SEEK_MS` | gut | S | niedrig | später |
| Playlists / Shuffle | Playlist-Dialog, Shuffle-Optionen | teilweise: "Zufall" global (`randomItem`) | `Playlists/{id}/Items`, `SortBy=Random` | braucht Queue aus Up Next | gut | M | niedrig | später – nach Up Next |
| SyncPlay | nur Einstellungen + Toolbar-Button mit leerem `onPressed: () {}` (`top_toolbar.dart:466`) | nein | `SyncPlay/*` + WebSocket | – | schlecht | L | hoch | nicht sinnvoll |
| DLNA / Cast / AirPlay | `dlna_provider.dart`, `google_cast_provider.dart`, `airplay_provider.dart` | nein | – | – | schlecht | L | hoch | nicht sinnvoll |
| Downloads/Offline | Download-Service, Offline-DB, Sync (`download_service.dart`, `sync_service.dart`) | nein | `Items/{id}/Download` | – | schlecht | L | hoch | nicht sinnvoll |
| Admin-Suite | Nutzer, Bibliotheken, Plugins, Tasks, Logs, Backups (Legacy `lib › ui › screens › admin`) | nein | `System/*`, `Library/VirtualFolders`, `Plugins`, `Users/New` … | – | schlecht (Formulare per D-Pad) | L | hoch | nicht sinnvoll |
| Plugin-Sync (Moonbase-Einstellungen) | `plugin_sync_service.dart` | nein; Typen vorhanden (`serializeStored`), nicht verdrahtet | Moonbase-Settings-Route (unverifiziert) | `EffectiveDevicePreferences` | gut | M | mittel | später |
| Elternschutz / PIN | lokale App-PIN (`user_pin_hash`) | nein; Server-Altersfreigaben wirken serverseitig | – | `PreferenceStore` | gut | S–M | niedrig | später – Frage 3 |
| Beenden bestätigen | `confirm_exit` | nein | – | `HomeActivity.onBackPressed` | gut | S | niedrig | später |
| Theme-Musik | `themeMusicEnabled`, `Items/{id}/ThemeMedia` | nein | `Items/{id}/ThemeMedia` | – | schlecht (zweiter Player auf Detail) | M | mittel | nicht sinnvoll |
| Trickplay-Vorschaubilder | `trick_play_enabled` | nein | Trickplay-Kacheln (Jellyfin ≥ 10.9, Route unverifiziert) | `ImageLoader` | schlecht: Kachelbilder mehrere MB während Wiedergabe | M | mittel | nicht sinnvoll |
| Bildwiederholrate umschalten | `refresh_rate_switching_behavior` | nein | – | – | `Display.Mode` erst ab API 23 | – | – | nicht sinnvoll |
| Emby / Emby Connect | `server_emby`, `emby_connect_service.dart` | nein | – | – | – | L | – | nicht sinnvoll (Firefin = Jellyfin) |

Routen-Anhang (Seerr-Routen gehen immer über Moonbase, Präfix `{prefix}/Api/`):

- A1 Seerr Personen: `person/{id}` und `person/{id}/combined_credits`.
- A2 Seerr Kategorien: `discover/movies/upcoming`, `discover/tv/upcoming`,
  `discover/movies/top`, `movie/{id}/recommendations`, `tv/{id}/similar`.
- A3 Seerr 4K: `POST request` mit `is4k:true`.
- A4 Seerr Admin: `POST request/{id}/approve` und `POST request/{id}/decline`.
- A5 Fernsteuerbarkeit: Jellyfin-WebSocket `/socket`.

## 3. Details je empfohlener Funktion

### 3.1 Nächste Folge + "Noch dabei?"

- **Routen:** beim Start einer Episode einmal
  `GET Shows/{seriesId}/Episodes?UserId={u}&StartItemId={itemId}&Limit=2&Fields=…`
  (zweites Element = nächste Folge; `StartItemId` unverifiziert). Fallback:
  vorhandenes `nextUp(seriesId)` nach dem Stopped-Report – dort Wettlauf mit
  dem asynchronen Stopped, daher nur zweite Wahl.
- **Klassen:** `MediaItem` braucht `seriesId` (`SeriesId` parsen);
  `PlayerActivity` bekommt die Serien-ID per Extra; `renegotiate` wird auf eine
  neue `itemId` verallgemeinert (setzt `itemId`, Titel, Tracks, neue
  `SessionReporter`-Session, Indizes `null` → Sprachvorgaben greifen in
  `playbackInfo`).
- **UI:** Overlay unten rechts in `activity_player.xml`: Titel der nächsten
  Folge (`S01E05 · Name`), Button "Jetzt abspielen (10)" mit Fokus, Button
  "Schließen". Countdown per Coroutine, 1 Text-Update/s. Kein Bild.
- **Auslöser:** `STATE_ENDED` (statt `finish()`), oder früher beim Start eines
  `Outro`-Segments, falls 3.3 umgesetzt ist (wie Moonfin `inOutro`).
- **D-Pad:** OK = abspielen; BACK oder "Schließen" = Player verlassen wie
  heute; keine Eingabe = Autoplay nach 10 s.
- **"Noch dabei?":** Zähler automatischer Wechsel ohne Tasteneingabe;
  bei 3 (einstellbar 2/3/5/aus) statt Autoplay ein Dialog "Weiter schauen?"
  mit Ja/Nein. In `SettingsActivity` Gruppe *Playback*: "Nächste Folge
  automatisch" und "Noch-dabei-Abfrage".
- **RAM/CPU:** derselbe ExoPlayer und dieselbe Surface. Nicht eine zweite
  `PlayerActivity` starten: die alte würde erst nach `onCreate` der neuen
  zerstört, zwei Decoder-Instanzen auf AFTT gleichzeitig (Grenze
  unverifiziert).

### 3.2 Versionswahl / direkt spielbare Quelle

- **Ist:** `JellyfinClient.playbackInfo` wählt `firstOrNull { DirectPlay ||
  DirectStream || Transcoding || TranscodingUrl }` – praktisch immer die erste
  Quelle.
- **Änderung:** Reihenfolge `SupportsDirectPlay` → `SupportsDirectStream` →
  Transcode; bei explizitem `mediaSourceId` unverändert.
- **UI (optional, S):** Button "Version" auf `DetailActivity`, nur wenn
  `MediaSources.length > 1`; Dialog mit `MediaSources[].Name`; Auswahl geht als
  `mediaSourceId` an `startPlayback`.
- **Nutzen auf AFTT:** 1080p-H.264-Version direkt statt 4K-HEVC-Transcode;
  entlastet den Server, bessere Bildqualität bei 4 Mbit/s.
- **Test:** JVM-Test mit PlaybackInfo-Fixture (zwei Quellen, zweite direkt
  spielbar).

### 3.3 Intro/Abspann überspringen

- **Route:** `GET MediaSegments/{itemId}` (Beleg Legacy). Antwort: Segmente
  mit `Type` (Intro, Outro, Recap, Preview, Commercial), `StartTicks`,
  `EndTicks`. 404/leer = keine Funktion, kein Fehler.
- **Voraussetzung:** Jellyfin ≥ 10.10 und ein Segment-Provider (z. B.
  Intro-Skipper-Plugin oder Kapitel-basiert); sonst leer.
- **Klassen:** `JellyfinClient.mediaSegments(itemId)`; `PlayerActivity`: nach
  `prepare` laden; 1-s-Loop **nur wenn Segmente vorhanden**, Vergleich mit
  `actualPositionMs` (berücksichtigt den TS-Transcode-Offset). Keine
  `PlayerMessage`-Positionen: die werden beim TS-Neustart ungültig.
- **UI:** Button "Intro überspringen" unten rechts, erscheint beim Eintritt,
  verschwindet am Segmentende. Mindestdauer wie Moonfin (3 s für Fragen, 1 s
  für automatisch).
- **D-Pad:** solange der Button sichtbar und der Controller verborgen ist,
  löst CENTER den Sprung aus (heute: Controller zeigen); sonst unverändert.
  Sprung über vorhandenes `seekTo` (bei TS-Transcode Neustart am Ziel).
- **Einstellung:** je Typ Fragen / Automatisch / Aus; Vorgabe Fragen für
  Intro/Recap, Aus für Outro (dort greift Up Next).

### 3.4 Kontextmenü auf Karten

- **Taste:** MENU (≡) auf einer fokussierten Karte in Home, Bibliothek, Suche,
  Detail-Kindern. Umsetzung im `MediaCardAdapter` per `setOnKeyListener`
  (`KEYCODE_MENU`) → Callback `onMenu(item)`.
- **Einträge (AlertDialog-Liste):** "Aus Weiterschauen entfernen" (nur bei
  `resumeTicks > 0`), "Als gesehen/ungesehen markieren", "Favorit
  hinzufügen/entfernen", "Zur Serie" (Episoden), "Von vorn abspielen".
- **Routen:** vorhandene `setPlayed`/`setFavorite`. "Aus Weiterschauen
  entfernen" = `DELETE Users/{id}/PlayedItems/{itemId}` (setzt laut
  Erwartung Position und Zähler zurück – unverifiziert); Alternative
  `POST UserItems/{itemId}/UserData` mit `PlaybackPositionTicks: 0`
  (Jellyfin ≥ 10.9, unverifiziert). Danach `loadHome(refreshOnly = true)`;
  der Fokus-Fallback aus `showRows` greift, wenn die Reihe verschwindet.
- **Busy-Guard:** wie `DetailActivity.toggleUserData` ein Request zur Zeit.

### 3.5 Quick Connect

- **Routen (Beleg Legacy):** `POST QuickConnect/Initiate` → `Secret`, `Code`;
  Polling `GET QuickConnect/Connect?Secret=` bis `Authenticated: true`;
  `POST Users/AuthenticateWithQuickConnect` mit `{ "Secret": … }` → gleiche
  Antwort wie `AuthenticateByName`. Verfügbarkeit: `GET QuickConnect/Enabled`
  (unverifiziert); HTTP-Methode von `Initiate` je Serverversion unverifiziert.
- **Klassen:** `JellyfinClient.loginQuickConnect(server)` teilt den
  Session-Teil mit `login()` (Token, User, `transport.update`). Kein Seerr-
  Auto-Bridge (kein Passwort) → `SeerrActivity`-Fallback-Prompt bleibt.
- **UI:** in `LoginActivity` Button "Mit Quick Connect anmelden" unter dem
  Passwortfeld; danach großer Code (Typo `display` 36 sp) und Hinweis
  "In der Jellyfin-App oder im Web unter Benutzer → Quick Connect eingeben".
  Button "Abbrechen" mit Fokus.
- **D-Pad:** nur Server-Adresse muss getippt werden.
- **Grenzen:** Polling alle 5 s, Abbruch nach 5 min oder beim Verlassen
  (`scope` in `onDestroy`). `Secret` nur im Speicher. http-Bestätigung und
  Origin-Bindung gelten unverändert (`SECURITY.md`).

### 3.6 Startseite: Favoriten, Abschnittsreihenfolge, Ausschlüsse

- **Favoriten-Reihe:** `items(favorites = true, includeTypes =
  "Movie,Series", sortBy = "DateCreated", sortOrder = "Descending", limit = 12)`
  als weitere `HomeRow` (Poster). `items()` liefert kein `Overview` → Vorschau
  zeigt keinen Text; akzeptabel oder Feld nur hier ergänzen.
- **Reihenfolge:** `HomeActivity.loadHome` baut die Reihen nach
  `stored.homeSectionOrder` (`resume`, `nextUp`, `favorites`, `latest`) und
  lässt fehlende Einträge weg. `SettingsActivity` Gruppe *Interface*:
  "Startseite" → Mehrfachauswahl (an/aus) und "Nach oben schieben" als
  Listendialog. Der Typ ist bereits Teil von `serializeStored`.
- **Ausschlüsse:** `GET Users/Me` (einmal pro vollem Laden, im
  `coroutineScope` parallel zu `views()`), `Configuration.LatestItemsExcludes`
  aus `mediaViews` filtern. Spart je ausgeschlossener Bibliothek einen
  Request und ein Poster-Set.

### 3.7 Bibliothek: Filter, Genre, Sortierung

- **Routen:** `Users/{id}/Items?…&Filters=IsUnplayed|IsPlayed|IsFavorite`,
  `&GenreIds=`; Genre-Liste `Genres?ParentId={lib}&UserId={u}&IncludeItemTypes=`.
- **Klassen:** `JellyfinClient.items` um `filters` und `genreIds` erweitern
  (das vorhandene `favorites`-Flag wird ein Filterwert). `LibraryActivity`:
  zweiter Header-Button "Filter" (zyklisch Alle → Ungesehen → Gesehen →
  Favoriten), dritter "Genre" (Listendialog, "Alle" zuerst). Sortierung um
  Richtung und `DatePlayed`/`Random` erweitern; Auswahl je Bibliothek in
  `PreferenceStore` (kontogehashter Schlüssel `librarySort:<id>`).
- **Titelzeile:** `library_title` zeigt Filter und Genre mit an.
- **D-Pad:** Buttons im 72-dp-Header, UP aus der obersten Grid-Zeile;
  `TvRecyclerView` hält LINKS/RECHTS im Grid.
- **Speicher:** keine Genre-Kachelbilder (Moonfin `genre_grid_card.dart`),
  nur Text.

### 3.8 Untertitel-Stil und Sprachvorgaben

- **Stil:** zwei Einstellungen "Untertitelgröße" (Klein/Normal/Groß/Sehr groß)
  und "Untertitel-Hintergrund" (Aus/Halbtransparent/Schwarz). In
  `PlayerActivity.onCreate`: `playerView.subtitleView?.setFixedTextSize(…)`,
  `setStyle(CaptionStyleCompat(…))`, `setApplyEmbeddedStyles(false)`. Wirkt
  auf SRT/VTT und eingebettete Text-Untertitel; vom Server eingebrannte
  (PGS/ASS-Transcode, `DeviceProfile` listet nur srt/vtt extern) bleiben
  unverändert – im Einstellungstext sagen.
- **Sprachen:** `LANGUAGES` in `SettingsActivity` um gängige Codes erweitern;
  `StreamSelection.index` statt fester de/en-Aliase über
  `Locale(code).isO3Language` vergleichen (Plattform-API, API 21).
- **Server-Vorgabe:** Eintrag "Wie auf dem Server" (leerer Wert) liest
  `Users/Me` → `AudioLanguagePreference`, `SubtitleLanguagePreference`,
  `SubtitleMode` (`None` = aus, `Always`/`Default`/`Smart` = Sprache). Forced-
  Untertitel (`SubtitleMode=OnlyForced`, `IsForced` am Stream) sind heute nicht
  abgebildet; als eigener kleiner Schritt.

### 3.9 Session-Reporting

- `SessionReporter.send`: `AudioStreamIndex` und `SubtitleStreamIndex` in
  Playing/Progress/Stopped (Werte liegen in `PlayerActivity.audioIndex/
  subtitleIndex`; bei Wechsel entsteht ohnehin eine neue Session).
- Nach Login und App-Start einmal `POST Sessions/Capabilities/Full` mit
  `PlayableMediaTypes: ["Video"]`, `SupportsMediaControl: false`,
  `SupportedCommands: []`. Damit führt das Dashboard Firefin korrekt; erst mit
  Fernsteuerbarkeit (später) auf `true`.
- Ob Jellyfins "Audio-/Untertitelauswahl merken" die gemeldeten Indizes nutzt,
  ist unverifiziert.

### 3.10 Kapitel und Suche

- **Kapitel:** `Chapters` (Name, `StartPositionTicks`) am Item; ggf.
  `Fields=Chapters` an `item()` (unverifiziert, ob Standardfeld). Per Extra wie
  `EXTRA_TRACKS` an `PlayerActivity`; Top-Bar-Button "Kapitel" nur bei
  vorhandenen Kapiteln → Listendialog "00:12:30 · Name" → `seekTo`. Keine
  Kapitelbilder.
- **Suche:** ein Request `items(searchTerm, includeTypes =
  "Movie,Series,Episode,BoxSet")`, clientseitig nach `type` in Reihen
  (Filme, Serien, Episoden, Sammlungen) aufteilen; `SearchRowsAdapter` von
  zwei festen auf N Reihen umbauen. Personen erst mit "Darsteller" (später).

## 4. Bewusst nicht umsetzen

| Funktion | Grund (AFTT-bezogen) |
|---|---|
| Musik | Braucht Foreground-Service, MediaSession, Queue und eigene Navigation (L); Fire TV am Fernseher ist kein typischer Musikplayer; Docs schließen es aus (`FIREFIN_TECHNICAL_OVERVIEW.md`, Known boundaries). |
| Bücher/Comics | EPUB braucht WebView (altes Chromium auf Fire OS 5), CBR/7z native Archiv-Libs (Legacy: `rar_fork`, `archive_extract`), PDF-Seiten als Vollbild-Bitmaps belasten 1 GB RAM; Lesen per D-Pad aus Sofadistanz ist schlecht; APK-Größe. |
| Live-TV-EPG und DVR | 2D-Programmraster mit vielen Views und häufigem Neuzeichnen; Timer-/Aufnahme-Formulare per D-Pad. Eine reine Kanalliste bleibt "später". |
| YouTube-Trailer | Drittanbieter-Scraper (Piped/Invidious/Innertube) mit wechselnden Hosts und Zertifikaten; Fire OS 5 Trust-Store; VP9/AV1 nicht dekodierbar; widerspricht der Origin-Bindung. |
| SyncPlay | Auch in Moonfin FireTV32 nur ein Button ohne Funktion; braucht WebSocket, Zeitabgleich, Geschwindigkeitskorrektur – teuer auf Cortex-A7. |
| DLNA, Google Cast, AirPlay, PiP | Kein GMS, Multicast-Discovery, PiP ab API 26; in `COMPATIBILITY.md` bewusst deaktiviert. |
| Downloads/Offline | Stick hängt dauerhaft am Netz; ~5 GB freier Speicher (unverifiziert); DB + Sync = L. |
| Admin-Suite | Formulare und Texteingabe per D-Pad; Jellyfin-Web ist dafür da. |
| Eigener Bildschirmschoner | Fire-OS-Schoner greift bereits (`keepScreenOn` nur bei Wiedergabe); ein Artwork-Schoner decodiert dauernd Bilder. |
| Theme-Musik | Zweiter Player/Decoder auf der Detailseite. |
| Media Bar, Blur, Fokus-Vergrößerung, saisonale Effekte, Animationen | GPU-Last; in `EffectiveDevicePreferences` absichtlich fest aus. |
| Trickplay-Vorschau | Kachelbilder mehrere MB im Heap neben 16-MB-Puffer und Bild-Caches. |
| MDBList/TMDB-Zusatzbewertungen | Neuer API-Schlüssel müsste gespeichert werden; externe Hosts; Serverwerte reichen. |
| Seerr 4K | AFTT spielt max. 1080p (`DeviceProfile`). |
| Bildwiederholraten-Umschaltung | `Display.Mode` erst ab API 23. |
| Emby / Emby Connect | Firefin ist Jellyfin-only. |

## 5. Offene Fragen an den Besitzer

1. Welche Jellyfin-Version läuft, und ist ein Segment-Provider (z. B.
   Intro-Skipper) installiert? Entscheidet über 3.3 und über die neueren
   Routen (`UserFavoriteItems`, `UserItems/{id}/UserData`), die der Legacy-
   Client schon nutzte, während Firefin die älteren `Users/{id}/…`-Routen nutzt.
2. Wird Live TV genutzt? Wenn ja: reicht eine Kanalliste mit "läuft jetzt"?
3. Mehrere Personen am selben Stick (Benutzerwechsel, ggf. App-PIN) oder
   mehrere Server?
4. Hat das Haupt-Konto Seerr-Adminrechte, und sollen Anfragen am TV
   freigegeben werden? Leitet Moonbase POST auf `request/{id}/approve` weiter?
5. Gibt es Titel mit mehreren Versionen (4K + 1080p)? Bestimmt die Priorität
   von 3.2-UI; die Quellenreihenfolge ist unabhängig davon sinnvoll.
6. Soll "Nächste Folge" standardmäßig automatisch starten (Moonfin-Vorgabe:
   an) und nach wie vielen Folgen "Noch dabei?" fragen?
7. Steht ein AFTT für Messungen bereit (Wiedergabegeschwindigkeit,
   Untertitel-Rendering, AC-3/Dolby-Passthrough, Decoder-Instanzen)?
