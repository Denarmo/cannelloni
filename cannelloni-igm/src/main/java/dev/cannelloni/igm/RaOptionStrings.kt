package dev.cannelloni.igm

data class RaOptionStrings(
    val rootTitle: String = "Settings",
    val on: String = "On",
    val off: String = "Off",
    val restartHint: String = "Applies on relaunch",
    val savePlatform: String = "Save for Platform",
    val saveGame: String = "Save for this game",
    val dontSave: String = "Discard",
    val nativeMenu: String = "RetroArch Menu",
    val categoryTitles: Map<String, String> = mapOf(
        "video" to "Video",
        "audio" to "Audio",
        "latency" to "Latency",
        "speed" to "Speed & Rewind",
        "osd" to "On-Screen Display",
    ),
    // Labels for host-local toggles (keys prefixed "cannelloni_") shown in a category.
    val localToggleLabels: Map<String, String> = mapOf(
        "cannelloni_osd_reset" to "Reset OSD",
    ),
)
