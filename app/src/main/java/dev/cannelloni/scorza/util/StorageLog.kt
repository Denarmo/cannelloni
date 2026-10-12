package dev.cannelloni.scorza.util

object StorageLog {
    private val log = RotatingLogFile("storage.log", 256L * 1024, "yyyy-MM-dd HH:mm:ss") { LoggingPrefs.storage }

    fun init(cannelloniRoot: String) = log.init(cannelloniRoot)

    fun startRun(label: String) = log.write("=== $label @ ${log.timestamp()} ===")

    fun write(message: String) = log.write(message)
}
