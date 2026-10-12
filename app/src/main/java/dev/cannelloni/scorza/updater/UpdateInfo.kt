package dev.cannelloni.scorza.updater

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val tag: String,
    val apk: String,
    val changelog: String
) {
    val downloadUrl: String
        get() = "https://github.com/Denarmo/cannelloni/releases/download/$tag/$apk"
}
