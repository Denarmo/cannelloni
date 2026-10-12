package dev.cannelloni.scorza.di

import android.app.Activity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import dev.cannelloni.scorza.R
import dev.cannelloni.scorza.input.InputTesterController
import dev.cannelloni.scorza.ui.viewmodel.InputTesterViewModel
import dev.cannelloni.ui.components.OsdController

@Module
@InstallIn(ActivityComponent::class)
object InputModule {

    @Provides @ActivityScoped
    fun provideOsdController(): OsdController = OsdController()

    @Provides @ActivityScoped
    fun provideInputTesterController(
        activity: Activity,
        viewModel: InputTesterViewModel,
        portRouter: dev.cannelloni.scorza.input.runtime.PortRouter,
        activeMappingHolder: dev.cannelloni.scorza.input.runtime.ActiveMappingHolder,
    ): InputTesterController = InputTesterController(
        viewModel = viewModel,
        portRouter = portRouter,
        activeMappingHolder = activeMappingHolder,
        unknownDeviceName = activity.getString(R.string.input_tester_device_unknown),
        keyboardDeviceName = activity.getString(R.string.input_tester_device_keyboard),
    )
}
