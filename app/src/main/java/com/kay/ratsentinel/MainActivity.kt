package com.kay.ratsentinel

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.graphics.Typeface
import android.widget.*
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var resultsPanel: LinearLayout
    private val findings = mutableListOf<String>()
    private val sensitivePermissions = linkedMapOf(
        "Camera" to android.Manifest.permission.CAMERA,
        "Microphone" to android.Manifest.permission.RECORD_AUDIO,
        "Precise location" to android.Manifest.permission.ACCESS_FINE_LOCATION,
        "Background location" to android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        "Contacts" to android.Manifest.permission.READ_CONTACTS,
        "SMS" to android.Manifest.permission.READ_SMS,
        "Call history" to android.Manifest.permission.READ_CALL_LOG,
        "Phone state" to android.Manifest.permission.READ_PHONE_STATE,
        "Nearby devices" to android.Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 0, 0)
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 20)
            setBackgroundColor(Color.rgb(13, 13, 16))
        }
        root.addView(TextView(this).apply {
            text = "RAT SENTINEL"; textSize = 26f
            setTextColor(Color.rgb(255, 65, 65)); typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "ON-DEVICE SECURITY AUDIT  •  ANDROID " + android.os.Build.VERSION.RELEASE
            textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0, 8, 0, 18)
        })
        addButton(root, "LIVE SCREEN VIEW • SHARE THIS DEVICE", ::requestLiveScreenShare)
        addButton(root, "STOP LIVE SCREEN VIEW", { stopService(Intent(this, ScreenShareService::class.java)); log("Screen sharing stopped.") })
        addButton(root, "SCAN ALL INSTALLED APPS", ::runAudit)
        addButton(root, "RUN SAFE RAT ATTACK EMULATION", ::runRatEmulation)
        addButton(root, "CHECK NETWORK / VPN", ::checkNetwork)
        addButton(root, "REVIEW ACCESSIBILITY SERVICES", { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        addButton(root, "REVIEW DEVICE ADMIN APPS", { openSettings(Settings.ACTION_SECURITY_SETTINGS) })
        addButton(root, "OPEN ANDROID APP PERMISSIONS", { openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) })
        addButton(root, "EXPLAIN SCAN RESULTS", ::showScanGuide)
        addButton(root, "EXPORT INCIDENT LOG", ::exportLog)
        resultsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        output = TextView(this).apply {
            text = "Run a full installed-app audit or safe RAT emulation. Real app results include direct Android App Info controls."
            textSize = 13f; setTextColor(Color.rgb(225,225,225)); setPadding(0, 18, 0, 10)
        }
        resultsPanel.addView(output)
        root.addView(ScrollView(this).apply { addView(resultsPanel) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(120, 12, 20))
            isAllCaps = false; setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 })
    }

    private fun addResultButton(label: String, action: () -> Unit) {
        resultsPanel.addView(Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 22, 28)); setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 })
    }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    private fun log(s: String) {
        findings.add("[" + stamp() + "] " + s)
        output.text = findings.takeLast(300).joinToString("\n\n")
    }

    private fun resetResults(message: String) {
        findings.clear(); resultsPanel.removeAllViews()
        output = TextView(this).apply { textSize = 13f; setTextColor(Color.rgb(225,225,225)); setPadding(0,18,0,10) }
        resultsPanel.addView(output); log(message)
    }


    private fun requestLiveScreenShare() {
        AlertDialog.Builder(this)
            .setTitle("Start live screen sharing")
            .setMessage("Android will show its own screen-capture consent prompt. If you approve, this device's screen will stream over your local Wi-Fi to a browser on your Pixel 9. A persistent notification stays visible. Stop sharing here or from the notification.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), 7717)
            }.show()
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7717) {
            if (resultCode != RESULT_OK || data == null) {
                log("Screen sharing was not started: Android screen-capture consent was declined.")
                return
            }
            val accessToken = java.util.UUID.randomUUID().toString().replace("-", "")
            val service = Intent(this, ScreenShareService::class.java).apply {
                putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenShareService.EXTRA_RESULT_DATA, data)
                putExtra(ScreenShareService.EXTRA_TOKEN, accessToken)
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                val ip = localIpv4Address()
                if (ip == null) {
                    log("Screen capture approved, but no local IPv4 address was found. Connect both devices to the same Wi-Fi and try again.")
                } else {
                    log("LIVE SCREEN SHARE STARTED. Pixel 9 must use the same Wi-Fi. A one-time access token is included in the stream URL displayed in the next dialog.")
                    AlertDialog.Builder(this).setTitle("Screen sharing started")
                        .setMessage("On your Pixel 9, open the local address below while both devices are on the same Wi-Fi:\n\nhttp://" + ip + ":8765/\n\nThe live stream requires a one-time token. Stop sharing from the persistent notification.")
                        .setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                log("Could not start screen-sharing service: " + e.message)
            }
        }
    }

    private fun localIpv4Address(): String? {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    private fun runAudit() {
        resetResults("FULL INSTALLED-APP AUDIT • " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " • Android " + android.os.Build.VERSION.RELEASE)
        val pm = packageManager
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .filter { it.packageName != packageName }
                .sortedBy { it.applicationInfo?.loadLabel(pm)?.toString()?.lowercase(Locale.ROOT) ?: it.packageName }
        } catch (e: Exception) { log("Could not enumerate installed packages: " + e.message); return }

        val activeAdmins = try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet<String>() }

        val enabledA11y = try {
            Secure.getString(contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.mapNotNull { it.substringBefore('/').takeIf { p -> p.isNotBlank() } }?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet<String>() }

        var reviewed = 0
        var reviewCount = 0
        log("Android returned " + packages.size + " installed packages. Full package visibility is enabled for this sideloaded security-audit app.")
        log("Enabled accessibility packages: " + if (enabledA11y.isEmpty()) "None reported" else enabledA11y.joinToString())
        log("Active device-admin packages: " + if (activeAdmins.isEmpty()) "None reported" else activeAdmins.joinToString())

        for (pkgInfo in packages) {
            val ai = pkgInfo.applicationInfo ?: continue
            val pkg = pkgInfo.packageName
            val label = try { ai.loadLabel(pm).toString() } catch (_: Exception) { pkg }
            val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val requested = pkgInfo.requestedPermissions ?: emptyArray()
            val granted = mutableListOf<String>()
            requested.forEachIndexed { index, permission ->
                val flags = pkgInfo.requestedPermissionsFlags?.getOrNull(index) ?: 0
                if ((flags and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    sensitivePermissions.entries.firstOrNull { it.value == permission }?.let { granted.add(it.key) }
                }
            }
            val hasA11y = enabledA11y.contains(pkg)
            val hasAdmin = activeAdmins.contains(pkg)
            val installer = try {
                if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
                else pm.getInstallerPackageName(pkg)
            } catch (_: Exception) { null }
            val isFlagged = granted.isNotEmpty() || hasA11y || hasAdmin
            if (isFlagged) reviewCount++
            reviewed++
            log(buildString {
                append(label + "\nPackage: " + pkg)
                append("\nType: " + if (isSystem) "System / preinstalled" else "User-installed")
                append("\nInstaller: " + (installer ?: "Unknown / preloaded"))
                append("\nSensitive permissions granted: " + if (granted.isEmpty()) "None in checked list" else granted.joinToString(", "))
                append("\nEnabled accessibility service: " + hasA11y)
                append("\nActive device administrator: " + hasAdmin)
                append("\nReview status: " + if (isFlagged) "REVIEW" else "NO CHECKED INDICATOR")
                append("\nInterpretation: Permission or special access is an indicator to investigate, not proof of a RAT.")
            })
            addResultButton("APP INFO / PERMISSIONS / FORCE STOP / UNINSTALL • " + label) { openAppInfo(pkg) }
        }
        log("AUDIT COMPLETE: reviewed " + reviewed + " installed packages; " + reviewCount + " have at least one checked permission or special-access indicator.")
        log("This is a permission/configuration audit, not signature-based malware scanning or proof an app is safe.")
        addResultButton("OPEN ACCESSIBILITY SETTINGS") { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        addResultButton("OPEN DEVICE ADMIN SETTINGS") { openSettings(Settings.ACTION_SECURITY_SETTINGS) }
    }

    private fun openAppInfo(packageName: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
            log("Opened Android App info for " + packageName + ". Use Permissions, Force stop, Disable (if available), or Uninstall.")
        } catch (e: Exception) { log("Could not open App info for " + packageName + ": " + e.message) }
    }

    private fun runRatEmulation() {
        resetResults("SAFE RAT BEHAVIOR EMULATION • LOCAL DEVICE")
        log("Runs only inside RAT Sentinel. It does not install malware, contact a command server, access sensors, read messages, collect files, hide itself, or persist.")
        val events = listOf(
            "Simulated command-and-control heartbeat to a reserved local test address",
            "Simulated remote command queued: INVENTORY_APPS",
            "Simulated microphone access request without user action",
            "Simulated attempt to enable an accessibility service",
            "Simulated outbound data-transfer burst"
        )
        val detections = listOf(
            "NETWORK BEACON PATTERN",
            "REMOTE COMMAND QUEUE PATTERN",
            "SENSITIVE SENSOR ACCESS PATTERN",
            "PRIVILEGED ACCESS / ACCESSIBILITY PATTERN",
            "POSSIBLE DATA EXFILTRATION PATTERN"
        )
        events.forEachIndexed { i, event ->
            log("TEST EVENT: " + event)
            log("LOCAL TEST RULE FIRED: " + detections[i])
        }
        log("EMULATION RESULT: " + detections.size + " of " + events.size + " known simulated indicators matched.")
        log("This validates only the in-app demonstration rules. It does not prove Android observed real malware behavior. Use the real installed-app audit and live network check for device state.")
        addResultButton("RUN REAL INSTALLED-APP AUDIT") { runAudit() }
        addResultButton("CHECK REAL NETWORK / VPN STATE") { checkNetwork() }
    }

    private fun checkNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        resetResults("LIVE NETWORK / VPN CHECK")
        log("Connected=" + (caps != null) + "; Wi-Fi=" + (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) +
            "; Cellular=" + (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) +
            "; VPN=" + (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true))
        log("VPN=true means Android reports an active VPN transport. It does not identify the owning app or prove spying.")
        addResultButton("OPEN VPN SETTINGS") { openSettings(Settings.ACTION_VPN_SETTINGS) }
    }

    private fun showScanGuide() {
        AlertDialog.Builder(this).setTitle("How to read RAT Sentinel")
            .setMessage("INSTALLED-APP AUDIT: Reads the installed package list and granted state for selected sensitive permissions. It also reports enabled accessibility services and active device administrators where Android exposes them.\n\nREVIEW: A permission or special access exists. It may be legitimate. Check app purpose, publisher and installer.\n\nAPP INFO: Opens Android's controls. You can review/revoke permissions, Force stop, Disable if offered, or Uninstall. Android controls the final action.\n\nSAFE RAT EMULATION: Runs harmless simulated indicators inside this app. It does not attack Android or other apps.\n\nLIMITS: This is not a commercial malware scanner. It does not inspect private app data, instrument other processes, or guarantee detection of every RAT.")
            .setPositiveButton("Got it", null).show()
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)); log("Opened Android settings: " + action) }
        catch (e: Exception) { log("Could not open settings page: " + e.message) }
    }

    private fun exportLog() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "RAT Sentinel incident log")
            putExtra(Intent.EXTRA_TEXT, findings.joinToString("\n\n"))
        }
        startActivity(Intent.createChooser(send, "Export incident log"))
    }
}
