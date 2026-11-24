package com.kay.ratsentinel

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var resultsPanel: LinearLayout
    private val findings = mutableListOf<String>()

    private val sensitivePermissions = linkedMapOf(
        "Camera" to android.Manifest.permission.CAMERA,
        "Microphone" to android.Manifest.permission.RECORD_AUDIO,
        "Precise location" to android.Manifest.permission.ACCESS_FINE_LOCATION,
        "Contacts" to android.Manifest.permission.READ_CONTACTS,
        "SMS" to android.Manifest.permission.READ_SMS,
        "Call history" to android.Manifest.permission.READ_CALL_LOG
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
        val title = TextView(this).apply {
            text = "RAT SENTINEL"
            textSize = 26f
            setTextColor(Color.rgb(255, 65, 65))
            typeface = Typeface.DEFAULT_BOLD
        }
        status = TextView(this).apply {
            text = "LOCAL DEVICE SECURITY LAB  •  READY"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 18)
        }
        root.addView(title); root.addView(status)
        addButton(root, "RUN DEVICE AUDIT", ::runAudit)
        addButton(root, "CHECK NETWORK / VPN", ::checkNetwork)
        addButton(root, "SIMULATION LAB", ::runSimulation)
        addButton(root, "REVIEW ACCESSIBILITY SERVICES", { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        addButton(root, "REVIEW DEVICE ADMIN APPS", { openSettings(Settings.ACTION_SECURITY_SETTINGS) })
        addButton(root, "APP PERMISSION SETTINGS", { openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) })
        addButton(root, "EXPLAIN SCAN RESULTS", ::showScanGuide)
        addButton(root, "EXPORT INCIDENT LOG", ::exportLog)
        resultsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        output = TextView(this).apply {
            text = "No audit has run yet.\n\nRun an audit or open the Simulation Lab. Findings are indicators, not proof of infection. Use an app's App info button to review permissions, force-stop it, or uninstall it where Android allows."
            textSize = 13f
            setTextColor(Color.rgb(225,225,225))
            setPadding(0, 18, 0, 10)
        }
        resultsPanel.addView(output)
        val scroll = ScrollView(this).apply { addView(resultsPanel) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(120, 12, 20))
            isAllCaps = false
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = 7
        parent.addView(b, lp)
    }

    private fun addResultButton(label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 22, 28))
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = 4
        resultsPanel.addView(b, lp)
    }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    private fun log(s: String) {
        findings.add("[${stamp()}] $s")
        output.text = findings.takeLast(150).joinToString("\n\n")
    }

    private fun resetResults(message: String) {
        findings.clear()
        resultsPanel.removeAllViews()
        output = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(225,225,225))
            setPadding(0, 18, 0, 10)
        }
        resultsPanel.addView(output)
        log(message)
    }

    private fun runAudit() {
        resetResults("DEVICE AUDIT • ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} • Android ${android.os.Build.VERSION.RELEASE}")
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        var reviewed = 0
        var flagged = 0
        log("Found ${apps.size} launcher-visible apps. Android hides some packages from ordinary apps, so this is not a complete malware scan.")
        for (resolve in apps) {
            val ai = resolve.activityInfo.applicationInfo
            val pkg = ai.packageName
            val label = resolve.loadLabel(pm).toString()
            val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val pkgInfo = try { pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS) } catch (_: Exception) { null }
            val requested = pkgInfo?.requestedPermissions ?: emptyArray()
            val grantedSensitive = sensitivePermissions.filter { (_, permission) ->
                requested.contains(permission) && pm.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED
            }.keys.toList()
            reviewed++
            val attention = grantedSensitive.isNotEmpty() && !isSystem
            if (attention) flagged++
            val classification = if (attention) "REVIEW" else "NO OBVIOUS FLAG"
            val details = buildString {
                append("$label\nPackage: $pkg\nType: ${if (isSystem) "System / preinstalled" else "User-installed"}\nResult: $classification")
                append("\nSensitive permissions granted: ${if (grantedSensitive.isEmpty()) "None detected from checked list" else grantedSensitive.joinToString(", ")}")
                append("\nMeaning: ${if (attention) "A sensitive permission is granted. This can be legitimate; compare it with the app's purpose." else "No checked sensitive permission was found granted. This does not prove the app is safe."}")
            }
            log(details)
            addResultButton("APP INFO / FORCE STOP / UNINSTALL • $label") { openAppInfo(pkg) }
        }
        log("SUMMARY: reviewed $reviewed launcher-visible apps; $flagged user-installed app(s) have at least one checked sensitive permission granted.")
        log("This is a permission review, not a malware verdict. A flag means investigate purpose, developer, install source and behavior before taking action.")
        addResultButton("OPEN ANDROID APP MANAGEMENT") { openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) }
        addResultButton("REVIEW ACCESSIBILITY SERVICES") { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        addResultButton("REVIEW DEVICE ADMIN APPS") { openSettings(Settings.ACTION_SECURITY_SETTINGS) }
    }

    private fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(intent)
            log("Opened App info for $packageName. Android provides the available Force stop, Uninstall, Disable and Permissions controls there.")
        } catch (e: Exception) {
            log("Could not open App info for $packageName: ${e.message}")
        }
    }

    private fun checkNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val caps = n?.let { cm.getNetworkCapabilities(it) }
        resetResults("NETWORK / VPN CHECK")
        log("Connected=${caps != null}; Wi-Fi=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true}; Cellular=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true}; VPN=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true}.")
        log("Meaning: VPN=true only says a VPN transport is active. It does not identify which app owns it or prove spying. Check Android Settings > Network & internet > VPN and verify the provider and always-on setting.")
        addResultButton("OPEN VPN SETTINGS") { openSettings(Settings.ACTION_VPN_SETTINGS) }
    }

    private fun runSimulation() {
        val scenarios = arrayOf(
            "Suspicious app with microphone permission",
            "Unexpected accessibility service",
            "Unexpected VPN connection",
            "Normal app with location permission"
        )
        AlertDialog.Builder(this)
            .setTitle("Choose a safe training scenario")
            .setItems(scenarios) { _, which -> showScenario(which, scenarios[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showScenario(which: Int, name: String) {
        resetResults("SIMULATION LAB • $name")
        when (which) {
            0 -> {
                log("SIMULATED EVIDENCE: An unfamiliar flashlight app has microphone permission granted.")
                log("SEVERITY: MEDIUM • REVIEW REQUIRED, not confirmed malware.")
                log("WHY IT MATTERS: Microphone access is sensitive and is not normally needed for a basic flashlight.")
                log("WHAT TO DO: Verify the app publisher and install source. If you do not trust it, open App info, Force stop, then Uninstall. Revoke microphone permission first if keeping it.")
                addResultButton("OPEN APP PERMISSION SETTINGS") { openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) }
            }
            1 -> {
                log("SIMULATED EVIDENCE: An unknown service appears in Accessibility settings.")
                log("SEVERITY: HIGH • INVESTIGATE PROMPTLY. Accessibility access can observe screen content and perform actions.")
                log("WHAT TO DO: In Accessibility settings, turn off the service if you did not knowingly enable it. Then open the app's App info and uninstall it if untrusted.")
                addResultButton("OPEN ACCESSIBILITY SETTINGS") { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            }
            2 -> {
                log("SIMULATED EVIDENCE: Android reports an active VPN, but the provider is not yet verified.")
                log("SEVERITY: LOW TO MEDIUM • VERIFY THE PROVIDER. A VPN is not automatically malicious.")
                log("WHAT TO DO: Check the VPN name, app publisher, always-on VPN and whether you installed it. Disconnect or remove only if you do not recognize it.")
                addResultButton("OPEN VPN SETTINGS") { openSettings(Settings.ACTION_VPN_SETTINGS) }
            }
            else -> {
                log("SIMULATED EVIDENCE: A map app has location permission granted.")
                log("SEVERITY: INFO • EXPECTED FOR MANY MAP APPS.")
                log("WHY IT MATTERS: Permission context matters. Location access is reasonable for navigation, but review whether it is allowed all the time or only while in use.")
                log("WHAT TO DO: Keep or change the permission based on the app's purpose and your preference.")
                addResultButton("OPEN APP PERMISSION SETTINGS") { openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) }
            }
        }
        log("TRAINING ONLY: No real app was scanned, stopped, changed or uninstalled. These are example findings to teach interpretation and response.")
        addResultButton("RUN A REAL DEVICE AUDIT") { runAudit() }
    }

    private fun showScanGuide() {
        AlertDialog.Builder(this)
            .setTitle("How to read RAT Sentinel results")
            .setMessage(
                "NO OBVIOUS FLAG: None of the small set of checked sensitive permissions was found granted. This is not a clean bill of health.\n\n" +
                "REVIEW: A user-installed launcher app has one or more checked sensitive permissions granted. That can be legitimate. Compare the permission with what the app does.\n\n" +
                "HIGH RISK ACCESS: Accessibility or device-admin access can give an app powerful control. Only keep services you recognize and intentionally enabled.\n\n" +
                "VPN ACTIVE: Indicates a VPN transport, not malware. Verify the VPN app/provider in Android settings.\n\n" +
                "ACTION: App info opens Android's own controls. Depending on the app and device, you may Force stop, Uninstall, Disable or change permissions. System apps may not be uninstallable. RAT Sentinel cannot silently kill or uninstall another app; Android requires system/user controls.\n\n" +
                "LIMITS: This app does not inspect private app data, scan every installed package, detect every RAT, or guarantee device cleanliness."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)); log("Opened Android settings: $action") }
        catch (e: Exception) { log("Could not open settings page: ${e.message}") }
    }

    private fun exportLog() {
        val text = findings.joinToString("\n\n")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "RAT Sentinel incident log"); putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Export incident log"))
    }
}
