package dev.cannelloni.scorza.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cannelloni.scorza.config.PlatformConfig
import dev.cannelloni.scorza.launcher.ApkLauncher
import dev.cannelloni.scorza.launcher.DelfinoLauncher
import dev.cannelloni.scorza.launcher.EmuLauncher
import dev.cannelloni.scorza.launcher.InstalledCoreService
import dev.cannelloni.scorza.launcher.LaunchManager
import dev.cannelloni.scorza.launcher.LaunchState
import dev.cannelloni.scorza.launcher.RetroArchLauncher
import dev.cannelloni.scorza.settings.SettingsRepository
import dev.cannelloni.scorza.util.AtomicRename
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LaunchModule {

    @Provides @Singleton
    fun provideRetroArchLauncher(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
    ): RetroArchLauncher = RetroArchLauncher(context) { settings.retroArchPackage }

    @Provides @Singleton
    fun provideDelfinoLauncher(
        @ApplicationContext context: Context,
    ): DelfinoLauncher = DelfinoLauncher(context)

    @Provides @Singleton
    fun provideLaunchManager(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        platformConfig: PlatformConfig,
        retroArchLauncher: RetroArchLauncher,
        emuLauncher: EmuLauncher,
        apkLauncher: ApkLauncher,
        delfinoLauncher: DelfinoLauncher,
        launchState: LaunchState,
        activeMappingHolder: dev.cannelloni.scorza.input.runtime.ActiveMappingHolder,
        atomicRename: AtomicRename,
        installedCoreService: InstalledCoreService,
        gameOverrides: dev.cannelloni.scorza.db.GameOverrideStore,
    ): LaunchManager = LaunchManager(
        context, settings, platformConfig,
        retroArchLauncher, emuLauncher, apkLauncher, delfinoLauncher, launchState, activeMappingHolder,
        atomicRename, installedCoreService, gameOverrides
    )
}
