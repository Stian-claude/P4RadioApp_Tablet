# P4 Radio App

Android-app for P4-radiostasjoner og Spotify, designet for bruk med telefonen liggende (landscape). Viser en stor digital klokke og gir enkel kontroll over avspilling.

## Funksjoner

- **Radio** — P4, P5 Hits, P6 Rock, P7 Klem, NRK P1, P2 og P3 via live-streams
- **Spotify** — kobler til Spotify-appen og spiller en fast spilleliste via App Remote SDK
- **Spillelistepanel** — flyout fra venstre kant i landscape Spotify-modus med alle sanger; trykk for å spille
- Digital klokke med dag og dato (Playfair Display-font)
- Automatisk fullskjerm / immersive mode
- Skjermen holdes på mens appen er aktiv
- Foreground service med medienotifikasjon (radio)
- Automatisk pause av Spotify ved rotasjon til portrett
- Vises over låseskjerm og slår på skjermen automatisk ved oppstart
- App-snarveier: "Start P4" og "Stopp radio" (langt trykk på ikonet)
- Samsung Kjøring-modus: start via "Åpne Radio" i Modi og rutiner

## Krav

- Android 8.0 (API 26) eller nyere
- Spotify-appen installert for Spotify-modus

## Bygg

```bash
./gradlew assembleRelease
```

Signer med:
```bash
java -jar apksigner.jar sign \
  --ks p4radio.keystore \
  --ks-pass pass:<passord> \
  --out P4Radio-vX.Y.apk \
  app-release-unsigned.apk
```

## Spotify-oppsett

App Remote SDK krever at appen er registrert i [Spotify Developer Dashboard](https://developer.spotify.com/dashboard):

| Felt | Verdi |
|------|-------|
| Package name | `no.radioapp.player` |
| Redirect URI | `no.radioapp.player://callback` |

> **Merk:** `CLIENT_SECRET` i `SpotifyController.kt` er kun egnet for privat/personlig bruk. Ikke gjør repoet offentlig med hemmeligheten synlig.

## Samsung Kjøring-modus

For å starte appen automatisk når du kjører:

1. **Modi og rutiner → Kjøring → Andre handlinger → Åpne Radio** (starter P4 automatisk)
2. For stopp: lag en **Rutine** med trigger "Bluetooth kobler fra [bil]" → handling "Tving stopp: Radio"

## Teknisk stack

- Kotlin + Jetpack Compose (Material 3)
- ExoPlayer (Media3) for radiostreaming
- Spotify App Remote SDK 0.8.0 + Web API som fallback
- OkHttp for HTTP
- RadioBrowser API for stream-URL-oppslag
- Foreground Service for medienotifikasjon
