package dev.cannelloni.scorza.model

import dev.cannelloni.scorza.util.ScanLog

enum class CollectionType {
    STANDARD,
    FAVORITES;

    companion object {
        fun from(value: String): CollectionType =
            entries.firstOrNull { it.name == value } ?: run {
                ScanLog.write("WARN unknown collection_type '$value', defaulting to STANDARD")
                STANDARD
            }
    }
}