# P4 Radio App — Funksjonell spesifikasjon

## Oversikt

P4 Radio er en Android-app designet primært for bruk i **landscape-modus** (telefonen liggende). Appen kombinerer norsk radiosteraming og Spotify-avspilling i ett enkelt grensesnitt dominert av en stor digital klokke.

---

## Skjermlayout

### Landscape-modus (primær)

```
┌─────────────────────────────────────────────────────┐
│ [Radio ]          09:15                              │
│ [Spotify]   Onsdag 13. Mai 2026                      │
│                                                      │
│                                                      │
│              Sangnavn / Stasjon                      │
│           [|◀]   [▶/⏸]   [▶|]                       │
└─────────────────────────────────────────────────────┘
```

- **Venstre kant:** Radio- og Spotify-knapper (aktiv = lys grønn, inaktiv = mørk grønn)
- **Midten:** Klokke øverst, innhold og kontroller nederst
- Hele skjermen er klikbar areal for knapper

### Portrett-modus (sekundær)

- Viser kun radio-visning (ikke Spotify-kontroller)
- Klokke og kontroller er sentrert samlet midt på skjermen
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

- Startes ved play, stoppes ved pause/switch til Spotify/app lukkes
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
        Lyktes? → koble til, abonner på spillerstatus, start spilleliste
        Feilet? → Web API-fallback:
                    refresh token → finn aktiv enhet
                    Enhet funnet? → start spilleliste direkte
                    Ingen enhet?  → åpne Spotify-appen til spillelisten automatisk
                                    vis "Starter Spotify..." med spinner
```

### Første gangs innlogging (OAuth)

1. Browser åpnes til Spotify-autorisasjon
2. Bruker godkjenner → redirect til `no.radioapp.player://callback?code=...`
3. `onNewIntent()` i MainActivity fanger opp koden
4. Bytter authorization code mot access + refresh token
5. Refresh token lagres i SharedPreferences (`"spotify"` / `"refresh_token"`)
6. App Remote SDK kobler til og starter spilleliste

### Spilleliste

- Fast spilleliste: `spotify:playlist:2kBChSTA8UEmfnbHycHFh9`
- Avspilling håndteres av Spotify-appen (App Remote SDK)

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
| Ingen aktiv enhet (NO_DEVICE) | Knapp: "Start spillelisten i Spotify" |
| Token utgått | Automatisk refresh via refresh_token |
| App Remote timeout (>4 sek) | Fallback til Web API |
| Nettverksfeil | Feilmelding vises, "Prøv igjen"-knapp |

---

## Tekniske begrensninger (Android 16 / One UI 8.5)

- Spotify App Remote SDK `showAuthView(true)` henger på Android 16 pga. bakgrunns-aktivitetsbegrensninger
- Løsning: `showAuthView(false)` med 4-sekunders timeout → Web API-fallback
- App Remote fungerer stille hvis Spotify allerede kjører i bakgrunnen

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
