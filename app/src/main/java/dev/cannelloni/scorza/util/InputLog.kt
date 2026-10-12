package dev.cannelloni.scorza.util

object InputLog {
    private val log = RotatingLogFile("input.log", 512L * 1024, "HH:mm:ss.SSS") { LoggingPrefs.input }

    fun init(cannelloniRoot: String) = log.init(cannelloniRoot)

    fun write(message: String) = log.write(message)
}
