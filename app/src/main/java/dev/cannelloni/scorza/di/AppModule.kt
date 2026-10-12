package dev.cannelloni.scorza.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.cannelloni.scorza.settings.GlobalOverridesManager
import dev.cannelloni.scorza.settings.SettingsRepository
import dev.cannelloni.scorza.util.ArtworkLookup
import dev.cannelloni.scorza.util.AtomicRename
import dev.cannelloni.scorza.util.RomDirectoryWalker
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideGlobalOverridesManager(settings: SettingsRepository): GlobalOverridesManager =
        GlobalOverridesManager { settings.sdCardRoot }

    @Provides @Singleton
    fun provideAtomicRename(settings: SettingsRepository, walker: RomDirectoryWalker, artwork: ArtworkLookup): AtomicRename =
        AtomicRename(File(settings.sdCardRoot), walker, artwork)
}
