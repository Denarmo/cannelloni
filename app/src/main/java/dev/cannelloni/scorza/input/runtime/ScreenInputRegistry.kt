package dev.cannelloni.scorza.input.runtime

import dev.cannelloni.scorza.input.ScreenInputHandler
import dev.cannelloni.scorza.input.screen.EmptyScreenInputHandler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenInputRegistry @Inject constructor() {
    private val stack = mutableListOf<ScreenInputHandler>()

    val top: ScreenInputHandler
        get() = stack.lastOrNull() ?: EmptyScreenInputHandler

    fun push(handler: ScreenInputHandler) {
        stack += handler
    }

    fun pop(handler: ScreenInputHandler) {
        val idx = stack.lastIndexOf(handler)
        if (idx >= 0) stack.removeAt(idx)
    }
}
