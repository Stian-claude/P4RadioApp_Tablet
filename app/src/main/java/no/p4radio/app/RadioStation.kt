package no.p4radio.app

data class RadioStation(
    val id: String,
    val name: String,
    val searchName: String,
)

val P4_STATIONS = listOf(
    RadioStation("p4",   "P4",           "P4"),
    RadioStation("p5",   "P5 Hits",      "P5 Hits"),
    RadioStation("p6",   "P6 Rock",      "P6 Rock"),
    RadioStation("p7",   "P7 Klem",      "P7 Klem"),
    RadioStation("p8",   "P8 Jazz",      "P8 Jazz"),
    RadioStation("gold", "Golden Girls", "Golden Girls Radio"),
)
