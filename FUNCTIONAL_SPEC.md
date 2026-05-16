# P4 Radio App — Funksjonell spesifikasjon

## Oversikt

P4 Radio er en Android-app designet primært for bruk i **landscape-modus** (telefonen liggende). Appen kombinerer norsk radiostreaming og Spotify-avspilling i ett enkelt grensesnitt dominert av en stor digital klokke.

---

## Skjermlayout

### Landscape-modus (primær)

```
┌─────────────────────────────────────────────────────┐
│ [♫]       09:15                      [ Radio  ]     │
│           Onsdag 13. Mai 2026        [ Spotify]     │
│                                                      │
│           Sangnavn / Stasjon                         │
│        [|◀]   [▶/⏸]   [▶|]                          │
└─────────────────────────────────────────────────────┘
```

- **Venstre kant (Spotify):** Stor tab-knapp (♫) som åpner spillelistepanel
- **Høyre kant:** Radio- og Spotify-knapper (aktiv = lys grønn, inaktiv = mørk grønn)
- **Midten:** Klokke øverst, innhold og kontroller nederst

#### Spillelistepanel (flyout)

```
┌─────────────────┬────────────────────────────────────┐
│ Road trip     X │                                    │
│─────────────────│                                    │
│ ▶ Dancing On    │        09:15                       │
│   Calum Scott   │  Onsdag 13. Mai 2026               │
│ 2 Blinding L... │                                    │
│   The Weeknd    │   Dancing On  /  Calum Scott       │
│ 3 ...           │   [|◀]  [▶/⏸]  [▶|]               │
└─────────────────┴────────────────────────────────────┘
```

- Glir inn fra venstre med animasjon
- Viser sangnummer, tittel og artist
- Aktiv sang markert med grønn ▶ og fet skrift
- Trykk på sang → starter avspilling og lukker panelet
- Henter sangliste via Spotify Web API (`/me/playlists` → `tracks.href`)

### Portrett-modus (sekundær)

- Viser kun radio-visning (ikke Spotify-kontroller)
- Klokke og kontroller er sentrert midt på skjermen
- Spotify pauses automatisk ved overgang til portrett

---

## Radio-modus

### Stasjoner

| ID | Visningsnavn | Søkenavn (RadioBrowser API) |
|----|-------------|----------------------------|
| p4 | P4 | P4 |
| p5 | P5 Hits | P5 Hits |
| p6 | P6 Rock | P6 Rock |
| p7 | P7 Klem | P7 Klem |
| nrkp1 | NRK P1 | NRK P1 |
| nrkp2 | NRK P2 | NRK P2 |
| nrkp3 | NRK P3 | NRK P3 |

**Default stasjon:** P4 (første i listen)

### Oppstart

1. Appen henter stream-URL for alle stasjoner parallelt via [RadioBrowser API](https://api.radio-browser.info/) (timeout 9 sek)
2. Mens henting pågår vises spinner med "Henter stasjoner..."
3. P4 velges automatisk og avspilling starter

### Kontroller

- **◀◀ / ▶▶** — forrige/neste stasjon (syklisk)
- **▶/⏸** — play/pause. Ved pause stoppes foreground service. Ved play gjenopptas eller restartes stream
- Buffering vises som spinner i play-knappen

### Foreground Service

- Startes ved play, stoppes ved pause / switch til Spotify / app lukkes
- Viser stasjonsnavn i notification
- Holder skjermen på mens appen er i forgrunnen (`FLAG_KEEP_SCREEN_ON`)

---

## Spotify-modus

### Tilkoblingsflyt

```
Bruker trykker [Spotify]
        │
        ▼
Finnes refresh_token?
   Nei → vis "Logg inn med Spotify"-knapp
   Ja  → prøv App Remote SDK (showAuthView=false, timeout 4 sek)
              │
        Lyktes? → koble til, abonner på spillerstatus, gjenoppta/start spilleliste
        Feilet? → Web API-fallback:
                    refresh token → finn aktiv enhet
                    Enhet funnet? → start/gjenoppta avspilling via Web API
                    Ingen enhet?  → vis feilmelding med knapp for manuell åpning
```

### Gjenopptak etter Radio-modus

App Remote holdes **ikke** frakoblet ved bytte til Radio. Ved retur til Spotify:
- App Remote fortsatt tilkoblet → `resume()` (ingen Spotify-åpning)
- App Remote frakoblet → ny tilkobling → `play(lastKnownTrackUri)` for å starte fra riktig sang

### Første gangs innlogging (OAuth)

1. Browser åpnes til Spotify-autorisasjon
2. Bruker godkjenner → redirect til `no.radioapp.player://callback?code=...`
3. `onNewIntent()` i MainActivity fanger opp koden
4. Bytter authorization code mot access + refresh token
5. Refresh token lagres i SharedPreferences (`"spotify"` / `"refresh_token"`)
6. App Remote SDK kobler til og starter spilleliste

### Spilleliste

- Fast spilleliste: `spotify:playlist:2kBChSTA8UEmfnbHycHFh9` ("Road trip")
- Avspilling håndteres av Spotify-appen (App Remote SDK)
- Sangliste vises i flyout-panel (venstre kant i landscape)
- Hentes via Spotify Web API; bruker `item`-feltet (Spotify API 2024-format)

### Kontroller (Spotify)

- **◀◀** — forrige sang (`skipPrevious`)
- **▶/⏸** — play/pause. Bruker App Remote hvis tilkoblet, ellers Web API
- **▶▶** — neste sang (`skipNext`)
- Sangtittel og artist vises løpende (sanntid via `subscribeToPlayerState`)

### Auto-pause

| Trigger | Handling |
|---------|----------|
| Bytte til Radio-modus | Spotify pauses umiddelbart |
| Rotasjon til portrett | Spotify pauses umiddelbart |

### Feilhåndtering

| Feil | Håndtering |
|------|------------|
| Ingen aktiv enhet (`NO_DEVICE`) | Knapp: "Start spillelisten i Spotify" |
| Token utgått | Automatisk refresh via refresh_token |
| App Remote timeout (>4 sek) | Fallback til Web API |
| Nettverksfeil | Feilmelding vises, "Prøv igjen"-knapp |

---

## Skjerm- og låseskjerm-håndtering

Appen er konfigurert for kjørebruk:

| Funksjon | Implementasjon |
|----------|----------------|
| Slår på skjermen ved oppstart | `android:turnScreenOn="true"` + `setTurnScreenOn(true)` |
| Vises over låseskjerm | `android:showWhenLocked="true"` + `setShowWhenLocked(true)` |
| Låser opp automatisk (uten PIN) | `KeyguardManager.requestDismissKeyguard()` |
| Holder skjermen på | `FLAG_KEEP_SCREEN_ON` i `onResume` |

---

## App-snarveier

Tilgjengelig ved langt trykk på app-ikonet:

| Snarvei | Handling |
|---------|----------|
| Start P4 | Åpner appen og starter P4 |
| Stopp radio | Stopper avspilling og lukker appen |

Stopp-kommandoen kan også sendes via broadcast (`no.radioapp.player.ACTION_STOP`) — brukes av Samsung Kjøring-modus-rutiner.

---

## Samsung Kjøring-modus

### Start (automatisk)
Modi og rutiner → Kjøring → Andre handlinger → **Åpne Radio**

Appen starter P4 automatisk, slår på skjermen og vises over låseskjermen.

### Stopp (via Rutine)
Rutiner → + → Trigger: Bluetooth kobler fra [bil] → Handling: Tving stopp: Radio

---

## Tillatelser

| Tillatelse | Bruk |
|------------|------|
| INTERNET | Radiostreaming, Spotify API |
| WAKE_LOCK | Hindre CPU-sleep under avspilling |
| FOREGROUND_SERVICE | Bakgrunnsavspilling radio |
| FOREGROUND_SERVICE_MEDIA_PLAYBACK | Krav for media foreground service |
| POST_NOTIFICATIONS | Medienotifikasjon (Android 13+) |

---

## Tekniske begrensninger (Android 16 / One UI 8.5)

- Spotify App Remote SDK `showAuthView(true)` henger på Android 16 pga. bakgrunns-aktivitetsbegrensninger
- Løsning: `showAuthView(false)` med 4-sekunders timeout → Web API-fallback
- App Remote fungerer stille hvis Spotify allerede kjører i bakgrunnen
- `play()` via App Remote kan ta Spotify til forgrunnen — bruker `resume()` der det er mulig

---

## Versjonsoversikt

| Versjon | Endringer |
|---------|-----------|
| 1.x | Radio med P4-stasjoner, klokke, fullskjerm |
| 2.x | Spotify-integrasjon via Web API, OAuth |
| 3.0 | App Remote SDK, layout-ombygging |
| 3.1 | App Remote timeout + Web API-fallback |
| 3.2 | Auto-pause på portrett, mørkere knapp |
| 3.3 | Radio-layout matcher Spotify, umiddelbar tilstandssynk |
| 3.4 | NRK P1/P2/P3 lagt til, Spotify pauses ved Radio-bytte |
| 3.5 | Portrett-layout sentrert, større kontroller |
| 3.6 | Større kontroller i landscape |
| 3.7 | Spotify spillelistepanel (flyout) med sangliste og klikk-for-spill |
| 3.8 | Flyout til venstre, knapper til høyre (50% større), Spotify API `item`-felt, portrett-pause fix |
| 3.9 | Skjerm-opplåsing: slår på skjerm og viser over låseskjerm ved oppstart |
| 3.10 | Fikset Spotify-app åpning ved modusskifte (bruker `resume()` i stedet for `play()`) |
| 3.11 | Kodeopprydding: fjernet dødkode (`fetchTracksViaClientCredentials`, `awaitingReturn`, `needsTracksReauth`), `ERROR_NO_DEVICE`-konstant, ExoPlayer re-setup kun ved IDLE/ENDED |
