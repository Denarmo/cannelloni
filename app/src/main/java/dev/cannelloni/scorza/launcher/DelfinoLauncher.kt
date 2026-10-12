package dev.cannelloni.scorza.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.cannelloni.igm.DelfinoLaunchParams

class DelfinoLauncher(private val context: Context) {

    fun launch(params: DelfinoLaunchParams, targetPackage: String): LaunchResult {
        if (!context.isPackageInstalled(targetPackage)) {
            return LaunchResult.AppNotInstalled(targetPackage)
        }
        return context.startActivityNoAnim(buildIntent(params, targetPackage), "Failed to launch Delfino")
    }

    companion object {
        const val ACTIVITY = "org.dolphinemu.dolphinemu.cannelloni.DelfinoLaunchActivity"
        const val LEGACY_ACTIVITY = "org.dolphinemu.dolphinemu.cannoli.DelfinoLaunchActivity"
        val PACKAGE_CANDIDATES = listOf(
            "dev.cannelloni.delfino.debug", "dev.cannelloni.delfino",
            "dev.cannoli.delfino.debug", "dev.cannoli.delfino",
        )

        fun installedPackage(context: Context): String? =
            PACKAGE_CANDIDATES.firstOrNull { context.isPackageInstalled(it) }

        fun buildIntent(params: DelfinoLaunchParams, targetPackage: String, context: Context? = null): Intent =
            Intent().apply {
                val activityName = if (context != null) {
                    val cannelloniIntent = Intent().setComponent(ComponentName(targetPackage, ACTIVITY))
                    if (context.packageManager.queryIntentActivities(cannelloniIntent, 0).isNotEmpty()) {
                        ACTIVITY
                    } else {
                        LEGACY_ACTIVITY
                    }
                } else {
                    ACTIVITY
                }
                component = ComponentName(targetPackage, activityName)
                params.writeToIntent(this)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
