package no.p4radio.app

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val description: String = ""
)

// Stream-URLer fra Bauer Media Norway / SharpStream CDN
// Oppdater her hvis en stasjon ikke kobler til
val P4_STATIONS = listOf(
    RadioStation("p4",   "P4",       "https://live-bauerno.sharp-stream.com/P4AAC_NO_128",  "Norges musikkanal"),
    RadioStation("p5",   "P5 Hits",  "https://live-bauerno.sharp-stream.com/P5AAC_NO_128",  "Hits hele døgnet"),
    RadioStation("p6",   "P6 Rock",  "https://live-bauerno.sharp-stream.com/P6AAC_NO_128",  "Rock non-stop"),
    RadioStation("p7",   "P7 Klem",  "https://live-bauerno.sharp-stream.com/P7AAC_NO_128",  "Varme og gode låter"),
    RadioStation("p8",   "P8 Jazz",  "https://live-bauerno.sharp-stream.com/P8AAC_NO_128",  "Smooth jazz"),
    RadioStation("gold", "Golden Girls", "https://live-bauerno.sharp-stream.com/GOLDENAAC_NO_128", "Gull fra 60/70/80-tallet"),
)
