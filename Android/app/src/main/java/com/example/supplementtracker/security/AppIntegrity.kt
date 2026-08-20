package com.example.supplementtracker.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.example.supplementtracker.BuildConfig
import java.io.File
import java.security.MessageDigest

data class IntegrityVerdict(
    val ok: Boolean,
    val reason: String
)

object AppIntegrity {
    fun evaluate(context: Context): IntegrityVerdict {
        if (BuildConfig.DEBUG) return IntegrityVerdict(ok = true, reason = "debug")
        if (isDebuggerAttached()) return IntegrityVerdict(ok = false, reason = "debugger")
        if (isProbablyRooted()) return IntegrityVerdict(ok = false, reason = "root")
        if (isXposedPresent()) return IntegrityVerdict(ok = false, reason = "hook")
        val expected = BuildConfig.EXPECTED_CERT_SHA256.trim()
        if (expected.isNotEmpty() && !isSignatureValid(context, expected)) {
            return IntegrityVerdict(ok = false, reason = "signature")
        }
        return IntegrityVerdict(ok = true, reason = "ok")
    }

    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    private fun isProbablyRooted(): Boolean {
        val tags = Build.TAGS
        if (!tags.isNullOrBlank() && tags.contains("test-keys")) return true
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/sbin/magisk",
            "/data/adb/magisk",
            "/data/adb/modules"
        )
        if (paths.any { File(it).exists() }) return true
        val danger = listOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so"
        )
        if (danger.any { File(it).exists() }) return true
        return false
    }

    private fun isXposedPresent(): Boolean {
        val suspects = listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "com.saurik.substrate.MS$2",
            "com.topjohnwu.magisk"
        )
        return suspects.any { name ->
            runCatching { Class.forName(name) }.isSuccess
        }
    }

    private fun isSignatureValid(context: Context, expectedHexSha256: String): Boolean {
        val normalized = expectedHexSha256.lowercase().replace(":", "").trim()
        val digest = signingCertDigestSha256(context) ?: return false
        return digest.equals(normalized, ignoreCase = true)
    }

    private fun signingCertDigestSha256(context: Context): String? {
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signing = info.signingInfo ?: return null
                val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
                certs ?: return null
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures ?: return null
            }
            val first = signatures.firstOrNull() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(first.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
