package dev.cannelloni.scorza.di

import android.content.Context
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cannelloni.scorza.config.CannelloniPaths
import dev.cannelloni.scorza.config.PlatformConfig
import dev.cannelloni.scorza.db.CannelloniDatabase
import dev.cannelloni.scorza.db.RommLinkRepository
import dev.cannelloni.scorza.db.ScanScheduler
import dev.cannelloni.scorza.romm.PlatformMap
import dev.cannelloni.scorza.romm.RommClient
import dev.cannelloni.scorza.romm.RommConnectionStore
import dev.cannelloni.scorza.romm.RommHttp
import dev.cannelloni.scorza.romm.RommImageLoader
import dev.cannelloni.scorza.romm.RommLibrary
import dev.cannelloni.scorza.romm.RommSlugMap
import dev.cannelloni.scorza.romm.cache.CachedRommLibrary
import dev.cannelloni.scorza.romm.cache.RommDatabase
import dev.cannelloni.scorza.romm.cache.RommSyncCoordinator
import dev.cannelloni.scorza.romm.download.RommDownloadQueue
import dev.cannelloni.scorza.romm.download.RommDownloader
import dev.cannelloni.scorza.romm.download.RommInstaller
import dev.cannelloni.scorza.romm.sync.DeviceRegistrar
import dev.cannelloni.scorza.romm.sync.LocalSaveResolver
import dev.cannelloni.scorza.romm.sync.PendingConflictStore
import dev.cannelloni.scorza.romm.sync.RestorePromotionStore
import dev.cannelloni.scorza.romm.sync.SaveBackupManager
import dev.cannelloni.scorza.romm.sync.SaveSyncService
import dev.cannelloni.scorza.romm.sync.SaveSyncStatusHolder
import dev.cannelloni.scorza.romm.sync.SaveSyncStore
import dev.cannelloni.scorza.romm.sync.SlotManager
import dev.cannelloni.scorza.romm.sync.SyncHistoryStore
import dev.cannelloni.scorza.romm.sync.SyncScheduler
import dev.cannelloni.scorza.settings.SettingsRepository
import dev.cannelloni.scorza.ui.viewmodel.RommBrowseViewModel
import dev.cannelloni.scorza.util.ArtworkLookup
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RommModule {

    @Provides @Singleton
    fun provideRommHttp(store: RommConnectionStore): RommHttp =
        RommHttp(
            tokenProvider = { store.token },
            allowSelfSignedProvider = { store.allowSelfSigned },
            hostProvider = { store.host },
        )

    @Provides @Singleton
    fun provideRommClient(store: RommConnectionStore, http: RommHttp): RommClient =
        RommClient(
            baseUrlProvider = { store.host },
            clientProvider = { http.client() },
            downloadClientProvider = { http.downloadClient() },
        )

    @Provides @Singleton
    fun provideRommSlugMap(@ApplicationContext context: Context): RommSlugMap =
        RommSlugMap.load(context.assets)

    @Provides @Singleton
    fun providePlatformMap(slugMap: RommSlugMap, platformConfig: PlatformConfig): PlatformMap =
        PlatformMap(slugMap, isSupported = { platformConfig.isKnownTag(it) })

    @Provides @Singleton
    fun provideRommDatabase(paths: CannelloniPathsProvider): RommDatabase =
        RommDatabase { CannelloniPaths(paths.root).rommDatabase }

    @Provides @Singleton
    fun provideRommSyncCoordinator(
        client: RommClient,
        platformMap: PlatformMap,
        db: RommDatabase,
        rommStore: RommConnectionStore,
        @ApplicationContext context: Context,
    ): RommSyncCoordinator = RommSyncCoordinator(
        client, platformMap, db,
        enabledGroups = { rommStore.enabledCollectionGroups() },
        collectionsLabel = { context.getString(dev.cannelloni.scorza.R.string.romm_sync_collections) },
    )

    @Provides @Singleton
    fun provideDeviceRegistrar(
        settings: SettingsRepository,
        client: RommClient,
    ): DeviceRegistrar = DeviceRegistrar(settings, client)

    @Provides @Singleton
    fun provideRommDevicePairing(
        client: RommClient,
        store: RommConnectionStore,
        settings: SettingsRepository,
        registrar: DeviceRegistrar,
        @ApplicationContext context: Context,
        @IoScope ioScope: CoroutineScope,
    ): dev.cannelloni.scorza.romm.RommDevicePairing = dev.cannelloni.scorza.romm.RommDevicePairing(
        client = client,
        store = store,
        settings = settings,
        scope = ioScope,
        io = kotlinx.coroutines.Dispatchers.IO,
        deviceIdentifier = {
            android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "cannelloni-unknown"
        },
        deviceName = { registrar.defaultDeviceName() },
        clientVersion = { dev.cannelloni.scorza.BuildConfig.VERSION_NAME },
    )

    @Provides @Singleton
    fun provideLocalSaveResolver(paths: CannelloniPathsProvider): LocalSaveResolver =
        LocalSaveResolver(paths.root)

    @Provides @Singleton
    fun provideSaveBackupManager(paths: CannelloniPathsProvider, resolver: LocalSaveResolver): SaveBackupManager =
        SaveBackupManager(paths.root, resolver)

    @Provides @Singleton
    fun provideSyncHistoryStore(db: CannelloniDatabase): SyncHistoryStore = SyncHistoryStore(db)

    @Provides @Singleton
    fun providePendingConflictStore(db: CannelloniDatabase): PendingConflictStore = PendingConflictStore(db)

    @Provides @Singleton
    fun provideRestorePromotionStore(db: CannelloniDatabase): RestorePromotionStore = RestorePromotionStore(db)

    @Provides @Singleton
    fun provideSaveSyncStatusHolder(): SaveSyncStatusHolder = SaveSyncStatusHolder()

    @Provides @Singleton
    fun provideSaveSyncService(
        client: RommClient,
        connStore: RommConnectionStore,
        settings: SettingsRepository,
        registrar: DeviceRegistrar,
        store: SaveSyncStore,
        resolver: LocalSaveResolver,
        links: RommLinkRepository,
        paths: CannelloniPathsProvider,
        backupManager: SaveBackupManager,
        history: SyncHistoryStore,
        pendingConflicts: PendingConflictStore,
        promotions: RestorePromotionStore,
        statusHolder: SaveSyncStatusHolder,
        matcher: dev.cannelloni.scorza.romm.sync.RommCacheMatcher,
        roms: dev.cannelloni.scorza.db.RomsRepository,
    ): SaveSyncService = SaveSyncService(client, connStore, settings, registrar, store, resolver, links, paths, backupManager, history, pendingConflicts, promotions, statusHolder, matcher, roms)

    @Provides @Singleton
    fun provideRommCacheMatcher(
        cache: RommDatabase,
    ): dev.cannelloni.scorza.romm.sync.RommCacheMatcher = dev.cannelloni.scorza.romm.sync.RommCacheMatcher(cache)

    @Provides @Singleton
    fun provideSlotManager(
        client: RommClient,
        store: SaveSyncStore,
        resolver: LocalSaveResolver,
        registrar: DeviceRegistrar,
        paths: CannelloniPathsProvider,
        service: SaveSyncService,
    ): SlotManager = SlotManager(client, store, resolver, registrar, paths, service)

    @Provides @Singleton
    fun provideRommLibrary(db: RommDatabase): RommLibrary =
        CachedRommLibrary(db)

    @Provides @Singleton
    fun provideRommBrowseViewModel(
        library: RommLibrary,
        syncCoordinator: RommSyncCoordinator,
        db: RommDatabase,
        romsRepository: dev.cannelloni.scorza.db.RomsRepository,
        linkRepository: RommLinkRepository,
        settings: dev.cannelloni.scorza.settings.SettingsRepository,
        client: RommClient,
        paths: CannelloniPathsProvider,
        rommStore: RommConnectionStore,
    ): RommBrowseViewModel =
        RommBrowseViewModel(
            library = library,
            syncCoordinator = syncCoordinator,
            db = db,
            presentNamesFor = { tag -> romsRepository.presentFileNames(tag) },
            linkedIdsProvider = { linkRepository.presentRommIds() },
            hiddenTagsProvider = { settings.hiddenRommPlatforms },
            firmwareFor = { platformId -> client.getFirmware(platformId) },
            biosDirFor = { tag -> dev.cannelloni.scorza.config.CannelloniPaths(paths.root).biosFor(tag) },
            enabledCollectionGroups = { rommStore.enabledCollectionGroups() },
            serverUnsupported = { dev.cannelloni.scorza.romm.RommCapabilities.isKnownUnsupported(rommStore.serverVersion) },
        )

    @Provides @Singleton
    fun provideRommImageLoader(
        @ApplicationContext context: Context,
        http: RommHttp,
        paths: CannelloniPathsProvider,
    ): ImageLoader =
        RommImageLoader.build(context, http, java.io.File(dev.cannelloni.scorza.config.CannelloniPaths(paths.root).configCache, "RommArt"))

    @Provides @Singleton
    fun provideRommArtDownloader(
        http: RommHttp,
        paths: CannelloniPathsProvider,
    ): dev.cannelloni.scorza.romm.art.RommArtDownloader =
        dev.cannelloni.scorza.romm.art.RommArtDownloader(http, paths)

    @Provides @Singleton
    fun provideRommArtFetcher(
        roms: dev.cannelloni.scorza.db.RomsRepository,
        artwork: ArtworkLookup,
        db: RommDatabase,
        links: RommLinkRepository,
        store: RommConnectionStore,
        artDownloader: dev.cannelloni.scorza.romm.art.RommArtDownloader,
        paths: CannelloniPathsProvider,
        settings: dev.cannelloni.scorza.settings.SettingsRepository,
        @IoScope ioScope: CoroutineScope,
    ): dev.cannelloni.scorza.romm.art.RommArtFetcher = dev.cannelloni.scorza.romm.art.RommArtFetcher(
        roms = roms,
        artwork = artwork,
        db = db,
        links = links,
        store = store,
        artDownloader = artDownloader,
        paths = paths,
        concurrency = { settings.concurrentDownloads },
        scope = ioScope,
    )

    @Provides @Singleton
    fun provideRommDownloader(
        client: RommClient,
        links: RommLinkRepository,
        artwork: ArtworkLookup,
        artDownloader: dev.cannelloni.scorza.romm.art.RommArtDownloader,
        scanScheduler: ScanScheduler,
        store: RommConnectionStore,
        http: RommHttp,
        paths: CannelloniPathsProvider,
        settings: dev.cannelloni.scorza.settings.SettingsRepository,
        @IoScope ioScope: CoroutineScope,
    ): RommDownloader = RommDownloader(
        queue = RommDownloadQueue(),
        client = client,
        installer = RommInstaller(),
        links = links,
        artwork = artwork,
        artDownloader = artDownloader,
        scanScheduler = scanScheduler,
        store = store,
        http = http,
        paths = paths,
        concurrency = { settings.concurrentDownloads },
        scope = ioScope,
    )

    @Provides @Singleton
    fun provideSyncScheduler(
        @ApplicationContext context: Context,
        service: SaveSyncService,
        statusHolder: SaveSyncStatusHolder,
        platformConfig: PlatformConfig,
        settings: SettingsRepository,
        paths: CannelloniPathsProvider,
        @IoScope ioScope: CoroutineScope,
        http: RommHttp,
    ): SyncScheduler = SyncScheduler(context, service, statusHolder, platformConfig, settings, { paths.romDir }, ioScope, http)
}
