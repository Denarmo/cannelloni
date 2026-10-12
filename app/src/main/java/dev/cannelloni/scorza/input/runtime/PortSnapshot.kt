package dev.cannelloni.scorza.input.runtime

import dev.cannelloni.scorza.input.AnalogRole
import dev.cannelloni.scorza.input.CanonicalButton

data class PortSnapshot(
    val pressed: Set<CanonicalButton>,
    val analog: Map<AnalogRole, Float>,
)
