package app.gamenative.vapor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Minecraft: Java Edition support. Vapor doesn't embed a Java runtime; it sets up and opens an
 * official open-source Java Edition launcher (whose Microsoft sign-in is approved), and gives
 * phone-tuned settings and a mod setup guide.
 */
object MinecraftHub {
    data class Launcher(
        val name: String,
        val packageName: String,
        val downloadUrl: String,
        val blurb: String,
    )

    val ZALITH = Launcher(
        name = "Zalith Launcher 2",
        packageName = "com.movtery.zalithlauncher.v2",
        downloadUrl = "https://github.com/ZalithLauncher/ZalithLauncher2/releases/latest",
        blurb = "Modern look, built-in Modrinth & CurseForge mod browser, Java 8–25. Recommended.",
    )

    val AMETHYST = Launcher(
        name = "Amethyst",
        packageName = "org.angelauramc.amethyst",
        downloadUrl = "https://github.com/AngelAuraMC/Amethyst-Android/releases/latest",
        blurb = "The official PojavLauncher successor. Fabric/Forge support, very compatible.",
    )

    /** Older launchers that also work if the player already has one. */
    private val OTHERS = listOf(
        Launcher("Zalith Launcher", "com.movtery.zalithlauncher", ZALITH.downloadUrl, ""),
        Launcher("PojavLauncher", "net.kdt.pojavlaunch", AMETHYST.downloadUrl, ""),
    )

    val ALL = listOf(ZALITH, AMETHYST) + OTHERS

    fun installed(context: Context): List<Launcher> = ALL.filter { isInstalled(context, it.packageName) }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }

    fun launch(context: Context, launcher: Launcher): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(launcher.packageName)
        if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}
