package dev.cannelloni.scorza.input.screen

import dagger.hilt.android.scopes.ActivityScoped
import dev.cannelloni.scorza.input.InputTesterController
import dev.cannelloni.scorza.input.ScreenInputHandler
import dev.cannelloni.scorza.navigation.NavigationController
import javax.inject.Inject

@ActivityScoped
class InputTesterInputHandler @Inject constructor(
    private val nav: NavigationController,
    private val controller: InputTesterController,
) : ScreenInputHandler {
    override fun onBack() = nav.pop()
}
