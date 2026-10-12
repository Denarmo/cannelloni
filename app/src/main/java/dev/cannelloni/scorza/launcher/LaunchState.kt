package dev.cannelloni.scorza.launcher

import dev.cannelloni.scorza.model.Rom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchState @Inject constructor() {
    @Volatile var launching: Boolean = false
    @Volatile var lastLaunched: Rom? = null
}
