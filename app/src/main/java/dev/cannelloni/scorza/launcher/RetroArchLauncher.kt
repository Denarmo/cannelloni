package dev.cannelloni.scorza.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import dev.cannelloni.igm.IgmColors
import dev.cannelloni.igm.IgmDisplaySettings
import dev.cannelloni.igm.IgmInputMapping
import dev.cannelloni.igm.RICOTTA_PROTOCOL_VERSION
import dev.cannelloni.igm.RicottaLaunchParams
import dev.cannelloni.scorza.i18n.LocaleOverride
import java.io.File

sealed interface ProtocolVerdict {
    data object Ok : ProtocolVerdict
    data class UpdateRicotta(val installed: Int, val required: Int) : ProtocolVerdict
    data class UpdateCannelloni(val installed: Int, val required: Int) : ProtocolVerdict
}

data class RicottaIgm(
    val gameTitle: String,
    val stateBasePath: String,
    val cannelloniRoot: String,
    val platformTag: String,
    val platformName: String,
    val igmTriggerKeycodes: List<Int>,
    val quitOnFocusLoss: Boolean = true,
    val preferredRefreshRate: Int? = null,
    val colors: IgmColors? = null,
    val displaySettings: IgmDisplaySettings,
    val inputMapping: IgmInputMapping? = null,
)

class RetroArchLauncher(
    private val context: Context,
    private val getRetroArchPackage: () -> String,
) {
    // Managed RicottaArch: structured, version-negotiated launch contract that drives the
    // shared Cannelloni in-game menu.
    fun launchRicotta(
        romFile: File,
        coreId: String,
        configPath: String? = null,
        targetPackage: String? = null,
        igm: RicottaIgm,
    ): LaunchResult {
        val pkg = targetPackage ?: getRetroArchPackage()
        if (!context.isPackageInstalled(pkg)) return LaunchResult.AppNotInstalled(pkg)

        when (checkProtocol(readInstalledProtocol(pkg), RICOTTA_PROTOCOL_VERSION)) {
            ProtocolVerdict.Ok -> Unit
            is ProtocolVerdict.UpdateRicotta -> return LaunchResult.Error(context.getString(dev.cannelloni.scorza.R.string.launch_error_update_ricotta))
            is ProtocolVerdict.UpdateCannelloni -> return LaunchResult.Error(context.getString(dev.cannelloni.scorza.R.string.launch_error_update_cannelloni))
        }

        val params = RicottaLaunchParams(
            coreId = coreId,
            romPath = romFile.absolutePath,
            configFilePath = configPath,
            gameTitle = igm.gameTitle,
            stateBasePath = igm.stateBasePath,
            cannelloniRoot = igm.cannelloniRoot,
            platformTag = igm.platformTag,
            platformName = igm.platformName,
            igmTriggerKeycodes = igm.igmTriggerKeycodes,
            quitOnFocusLoss = igm.quitOnFocusLoss,
            preferredRefreshRate = igm.preferredRefreshRate,
            colors = igm.colors,
            displaySettings = igm.displaySettings,
            inputMapping = igm.inputMapping,
            localeTag = LocaleOverride.currentTag(context),
        )

        val intent = Intent().apply {
            component = ComponentName(pkg, "dev.cannelloni.ricotta.RicottaLaunchActivity")
            params.writeToIntent(this)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return context.startActivityNoAnim(intent, "Failed to launch RicottaArch")
    }

    // Stock RetroArch (DIY): the classic RetroActivityFuture intent. The user owns RetroArch,
    // so there is no Cannelloni in-game menu and no protocol negotiation.
    fun launchRetroArchIntent(
        romFile: File,
        coreId: String,
        configPath: String? = null,
        targetPackage: String? = null,
    ): LaunchResult {
        val pkg = targetPackage ?: getRetroArchPackage()
        if (!context.isPackageInstalled(pkg)) return LaunchResult.AppNotInstalled(pkg)

        val intent = Intent().apply {
            component = ComponentName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("LIBRETRO", "/data/data/$pkg/cores/${coreId}_android.so")
            putExtra("ROM", romFile.absolutePath)
            if (configPath != null) putExtra("CONFIGFILE", configPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return context.startActivityNoAnim(intent, "Failed to launch RetroArch")
    }

    private fun readInstalledProtocol(pkg: String): Int = try {
        val ai = context.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
        ai.metaData?.getInt("cannelloni.protocol", -1) ?: -1
    } catch (_: PackageManager.NameNotFoundException) {
        -1
    }

    companion object {
        fun isRicotta(pkg: String): Boolean = pkg.startsWith("dev.cannelloni.ricotta")

        fun checkProtocol(installedProtocol: Int, requiredProtocol: Int): ProtocolVerdict = when {
            installedProtocol == requiredProtocol -> ProtocolVerdict.Ok
            installedProtocol < requiredProtocol -> ProtocolVerdict.UpdateRicotta(installedProtocol, requiredProtocol)
            else -> ProtocolVerdict.UpdateCannelloni(installedProtocol, requiredProtocol)
        }
    }
}

fun Context.isPackageInstalled(packageName: String): Boolean =
    packageManager.isPackageInstalled(packageName)

fun Context.startActivityNoAnim(intent: Intent, fallbackMsg: String): LaunchResult = try {
    startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    LaunchResult.Success
} catch (e: Exception) {
    LaunchResult.Error(e.message ?: fallbackMsg)
}

fun Context.romFileProviderUri(file: File): Uri =
    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

fun PackageManager.isPackageInstalled(packageName: String): Boolean = try {
    getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
