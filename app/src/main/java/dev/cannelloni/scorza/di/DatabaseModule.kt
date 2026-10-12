package dev.cannelloni.scorza.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cannelloni.scorza.db.AppsRepository
import dev.cannelloni.scorza.db.CannelloniDatabase
import dev.cannelloni.scorza.db.CollectionsRepository
import dev.cannelloni.scorza.db.GameOverrideStore
import dev.cannelloni.scorza.db.RecentlyPlayedRepository
import dev.cannelloni.scorza.db.RomScanner
import dev.cannelloni.scorza.db.RommLinkRepository
import dev.cannelloni.scorza.romm.sync.SaveSyncStore
import dev.cannelloni.scorza.db.RomsRepository
import dev.cannelloni.scorza.util.ArcadeTitleLookup
import dev.cannelloni.scorza.util.ArtworkLookup
import dev.cannelloni.scorza.util.RomDirectoryWalker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideCannelloniDatabase(paths: CannelloniPathsProvider): CannelloniDatabase =
        CannelloniDatabase(paths)

    @Provides @Singleton
    fun provideGameOverrideStore(db: CannelloniDatabase): GameOverrideStore =
        GameOverrideStore(db)

    @Provides @Singleton
    fun provideArtworkLookup(paths: CannelloniPathsProvider): ArtworkLookup =
        ArtworkLookup(paths)

    @Provides @Singleton
    fun provideArcadeTitleLookup(paths: CannelloniPathsProvider): ArcadeTitleLookup =
        ArcadeTitleLookup(paths)

    @Provides @Singleton
    fun provideRomDirectoryWalker(
        paths: CannelloniPathsProvider,
        arcadeTitleLookup: ArcadeTitleLookup,
        @ApplicationContext context: Context,
    ): RomDirectoryWalker = RomDirectoryWalker(paths, context.assets, arcadeTitleLookup)

    @Provides @Singleton
    fun provideRomScanner(
        db: CannelloniDatabase,
        walker: RomDirectoryWalker,
        artwork: ArtworkLookup,
    ): RomScanner = RomScanner(db, walker, artwork)

    @Provides @Singleton
    fun provideRomsRepository(
        paths: CannelloniPathsProvider,
        db: CannelloniDatabase,
        artwork: ArtworkLookup,
    ): RomsRepository = RomsRepository(paths, db, artwork)

    @Provides @Singleton
    fun provideAppsRepository(db: CannelloniDatabase, artwork: ArtworkLookup): AppsRepository =
        AppsRepository(db, artwork)

    @Provides @Singleton
    fun provideCollectionsRepository(db: CannelloniDatabase): CollectionsRepository =
        CollectionsRepository(db)

    @Provides @Singleton
    fun provideRecentlyPlayedRepository(db: CannelloniDatabase): RecentlyPlayedRepository =
        RecentlyPlayedRepository(db)

    @Provides @Singleton
    fun provideRommLinkRepository(
        db: CannelloniDatabase,
        paths: CannelloniPathsProvider,
    ): RommLinkRepository = RommLinkRepository(db) { paths.romDir }

    @Provides
    @Singleton
    fun provideSaveSyncStore(db: CannelloniDatabase): SaveSyncStore = SaveSyncStore(db)
}
