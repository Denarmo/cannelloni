package dev.cannelloni.scorza.boot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import dev.cannelloni.scorza.R
import dev.cannelloni.scorza.config.CannelloniPaths
import dev.cannelloni.scorza.config.PlatformConfig
import dev.cannelloni.scorza.db.CannelloniDatabase
import dev.cannelloni.scorza.db.CollectionsRepository
import dev.cannelloni.scorza.db.ScanScheduler
import dev.cannelloni.scorza.db.importer.ImportProgress
import dev.cannelloni.scorza.db.importer.ImportResult
import dev.cannelloni.scorza.db.importer.Importer
import dev.cannelloni.scorza.di.CannelloniPathsProvider
import dev.cannelloni.scorza.di.IoScope
import dev.cannelloni.scorza.input.BindingController
import dev.cannelloni.scorza.input.LauncherActions
import dev.cannelloni.scorza.launcher.InstalledCoreService
import dev.cannelloni.scorza.launcher.LaunchManager
import dev.cannelloni.scorza.navigation.LauncherScreen
import dev.cannelloni.scorza.navigation.NavigationController
import dev.cannelloni.scorza.settings.ContentMode
import dev.cannelloni.scorza.settings.SettingsRepository
import dev.cannelloni.scorza.setup.SetupCoordinator
import dev.cannelloni.scorza.ui.viewmodel.GameListViewModel
import dev.cannelloni.scorza.ui.viewmodel.SettingsViewModel
import dev.cannelloni.scorza.updater.UpdateManager
import dev.cannelloni.scorza.util.KitchenLog
import dev.cannelloni.scorza.util.ScanLog
import dev.cannelloni.scorza.util.StorageLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

sealed interface BootResult {
    data object Success : BootResult
    data class Failure(val message: String) : BootResult
}

@ActivityScoped
class BootInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val platformConfig: PlatformConfig,
    private val cannelloniDatabase: CannelloniDatabase,
    private val scanScheduler: ScanScheduler,
    private val cannelloniPaths: CannelloniPathsProvider,
    @IoScope private val ioScope: CoroutineScope,
    private val installedCoreService: InstalledCoreService,
    private val gameListViewModel: GameListViewModel,
    private val settingsViewModel: SettingsViewModel,
    private val collectionsRepository: CollectionsRepository,
    private val updateManager: UpdateManager,
    private val bindingController: BindingController,
    private val nav: NavigationController,
    private val launchManager: LaunchManager,
    private val launcherActions: LauncherActions,
    private val setupCoordinator: SetupCoordinator,
) {

    suspend fun run(onPhase: (BootPhase, Float, String) -> Unit): BootResult {
        val root = cannelloniPaths.root
        val romDir = cannelloniPaths.romDir
        val importPhase = if (hasLegacyData(root)) BootPhase.IMPORT else BootPhase.INITIAL_SCAN
        onPhase(BootPhase.LIBRARY_REFRESH, 0f, context.getString(R.string.boot_preparing))

        ScanLog.init(root.absolutePath)
        dev.cannelloni.scorza.util.InputLog.init(root.absolutePath)
        KitchenLog.init(root.absolutePath)
        StorageLog.init(root.absolutePath)
        dev.cannelloni.scorza.util.RommLog.init(root.absolutePath)
        dev.cannelloni.scorza.util.ErrorLog.init(root.absolutePath)
        setupCoordinator.logStorageDiagnostics()
        platformConfig.load()
        // Backfill only. Fills platforms that have no stored choice and never touches one the
        // user made, so it is safe to run on every boot rather than only on first run.
        platformConfig.seedUnsetPlatforms(context.packageManager)
        ioScope.launch {
            launchManager.syncRetroArchAssets(root)
            launchManager.syncRetroArchConfig(root)
        }
        ioScope.launch { dev.cannelloni.scorza.util.DirectoryLayout.ensure(root, romDir, context.assets, platformConfig) }

        val importer = Importer(
            context = context,
            cannelloniRoot = root,
            romDirectory = romDir,
            db = cannelloniDatabase,
            platformConfig = platformConfig,
            scanScheduler = scanScheduler,
            onProgress = ImportProgress { progress, label ->
                onPhase(importPhase, progress, label)
            },
            scanDisk = settings.scanLibraryAutomatically,
        )

        val result = withContext(Dispatchers.IO) { importer.run() }

        if (result is ImportResult.Failure) {
            ScanLog.write("ERROR import returned Failure: ${result.cause.message}")
            return BootResult.Failure(result.cause.message ?: context.getString(R.string.boot_import_failed))
        }

        ioScope.launch {
            installedCoreService.queryAllPackages()
        }

        withContext(Dispatchers.Main) {
            gameListViewModel.showFavoriteStars = settings.contentMode != ContentMode.FIVE_GAME_HANDHELD
            settingsViewModel.reinitialize(root, context.packageManager, context.packageName, collectionsRepository)

            ioScope.launch { updateManager.purgeStaleDownloads() }

            if (updateManager.shouldAutoCheck()) {
                ioScope.launch { updateManager.checkForUpdate() }
            }
            // updateAvailable -> settingsViewModel.updateInfo is now pushed lifecycle-bound
            // from MainActivity.ReadyNavGraph so it cancels on Activity destroy.

            bindingController.onProgress = { keys, elapsedMs ->
                val cs = nav.currentScreen
                if (cs is LauncherScreen.ShortcutBinding) {
                    nav.replaceTop(cs.copy(heldKeys = keys, countdownMs = elapsedMs))
                }
            }
            bindingController.onCommit = { chord ->
                val cs = nav.currentScreen
                if (cs is LauncherScreen.ShortcutBinding) {
                    val action = dev.cannelloni.igm.ShortcutAction.entries.getOrNull(cs.selectedIndex)
                    if (action != null) {
                        val cleared = cs.shortcuts.filterValues { it != chord }
                        nav.replaceTop(cs.copy(
                            shortcuts = cleared + (action to chord),
                            listening = false, heldKeys = emptySet(), countdownMs = 0,
                        ))
                    }
                }
            }
            bindingController.onCancel = {
                val cs = nav.currentScreen
                if (cs is LauncherScreen.ShortcutBinding && cs.listening) {
                    nav.replaceTop(cs.copy(listening = false, heldKeys = emptySet(), countdownMs = 0))
                }
            }

            nav.screenStack.clear()
            nav.screenStack.add(LauncherScreen.SystemList)
        }

        val shouldScanDisk = settings.scanLibraryAutomatically
        return suspendCancellableCoroutine { cont ->
            launcherActions.rescanSystemList(
                scanDisk = shouldScanDisk,
                onProgress = { tag, current, total ->
                    onPhase(BootPhase.LIBRARY_REFRESH, current.toFloat() / total.coerceAtLeast(1), tag)
                },
                onComplete = { cont.resume(BootResult.Success) },
            )
        }
    }

    private fun hasLegacyData(root: File): Boolean {
        val p = CannelloniPaths(root)
        return p.collectionsDir.exists() ||
            p.coresJson.exists() ||
            p.raGameIdsFile.exists() ||
            p.raGameIdsLegacyFile.exists() ||
            p.recentlyPlayedFile.exists() ||
            p.configOrdering.exists() ||
            p.toolsDir.exists() ||
            p.portsDir.exists() ||
            p.platformCacheFile.exists() ||
            p.gameCacheFile.exists()
    }
}
