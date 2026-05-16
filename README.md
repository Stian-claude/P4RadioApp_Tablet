# P4 Radio App

Android-app for P4-radiostasjoner og Spotify, designet for bruk med telefonen liggende (landscape). Viser en stor digital klokke og gir enkel kontroll over avspilling.

## Funksjoner

- **Radio** — P4, P5 Hits, P6 Rock, P7 Klem, NRK P1, P2 og P3 via live-streams
- **Spotify** — kobler til Spotify-appen og spiller en fast spilleliste via App Remote SDK
- **Spillelistepanel** — flyout fra venstre kant i landscape Spotify-modus med alle sanger; trykk for å spille
- Digital klokke med dag og dato (Playfair Display-font)
- Automatisk fullskjerm / immersive mode
- Skjermen holdes på mens appen er aktiv
- Foreground service med mediekontroller i notification-feltet (radio)
- Automatisk pause av Spotify ved rotasjon til portrett

## Krav

- Android 8.0 (API 26) eller nyere
- Spotify-appen installert for Spotify-modus

## Bygg

```bash
./gradlew assembleDebug
```

Signert release-APK bygges automatisk via GitHub Actions ved push til `main`.  
APK lastes opp som artifact under **Actions → siste kjøring → Artifacts**.

## Spotify-oppsett

App Remote SDK krever at appen er registrert i [Spotify Developer Dashboard](https://developer.spotify.com/dashboard):

| Felt | Verdi |
|------|-------|
| Package name | `no.radioapp.player` |
| SHA-1 (debug) | `68:8C:EE:FE:2C:9B:91:B5:78:E7:46:D2:BA:DA:C1:A6:FB:95:2E:B4` |
| Redirect URI | `no.radioapp.player://callback` |

> **Merk:** `CLIENT_SECRET` i `SpotifyController.kt` er kun egnet for privat/personlig bruk. Ikke gjør repoet offentlig med hemmeligheten synlig.

## Teknisk stack

- Kotlin + Jetpack Compose (Material 3)
- ExoPlayer (Media3) for radiostreaming
- Spotify App Remote SDK 0.8.0 + Web API som fallback
- OkHttp for HTTP
- RadioBrowser API for stream-URL-oppslag
- Foreground Service for medienotifikasjon
