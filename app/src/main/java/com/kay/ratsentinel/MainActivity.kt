package com.kay.ratsentinel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var status: TextView
    private val findings = mutableListOf<String>()

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
        addButton(root, "REVIEW ACCESSIBILITY SERVICES", { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        addButton(root, "REVIEW DEVICE ADMIN APPS", { openSettings(Settings.ACTION_SECURITY_SETTINGS) })
        addButton(root, "APP PERMISSION SETTINGS", { openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) })
        addButton(root, "SIMULATION LAB", ::runSimulation)
        addButton(root, "EXPORT INCIDENT LOG", ::exportLog)
        output = TextView(this).apply {
            text = "No audit has run yet. Select a test above.\n\nNote: Android limits visibility into other apps. Findings are indicators for manual investigation, not proof of infection."
            textSize = 13f
            setTextColor(Color.rgb(225,225,225))
            setPadding(0, 18, 0, 10)
        }
        val scroll = ScrollView(this).apply { addView(output) }
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

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    private fun log(s: String) { findings.add("[${stamp()}] $s"); output.text = findings.takeLast(100).joinToString("\n\n") }

    private fun runAudit() {
        findings.clear()
        log("AUDIT STARTED on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}; Android ${android.os.Build.VERSION.RELEASE}.")
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        log("Visible launcher apps: ${apps.size}. Android package-visibility rules may hide non-launcher apps.")
        apps.sortedBy { it.loadLabel(pm).toString().lowercase() }.forEach {
            val label = it.loadLabel(pm).toString()
            val pkg = it.activityInfo.packageName
            log("APP: $label  •  $pkg")
        }
        log("AUDIT COMPLETE. Review unfamiliar apps and their permissions in Android Settings. This scan does not label apps as malware based on name alone.")
    }

    private fun checkNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val caps = n?.let { cm.getNetworkCapabilities(it) }
        log("NETWORK CHECK: connected=${caps != null}; wifi=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true}; cellular=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true}; vpn=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true}.")
        log("Android app-level network inspection is restricted. A VPN indicator alone is not proof of malicious activity.")
    }

    private fun runSimulation() {
        log("SIMULATION ONLY: scenario 'unexpected remote command'.")
        log("Expected defensive signals: unfamiliar app identity, unusual sensitive permissions, accessibility or device-admin privileges, unexpected VPN, and unexplained network activity.")
        log("No command was sent, no sensor was activated, and no device was modified.")
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
