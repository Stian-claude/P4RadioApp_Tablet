package no.p4radio.app

data class RadioStation(
    val id: String,
    val name: String,
    val searchName: String,
)

val P4_STATIONS = listOf(
    RadioStation("p4",    "P4",      "P4"),
    RadioStation("p5",    "P5 Hits", "P5 Hits"),
    RadioStation("p6",    "P6 Rock", "P6 Rock"),
    RadioStation("p7",    "P7 Klem", "P7 Klem"),
    RadioStation("nrkp1", "NRK P1",  "NRK P1"),
    RadioStation("nrkp2", "NRK P2",  "NRK P2"),
    RadioStation("nrkp3", "NRK P3",  "NRK P3"),
)
