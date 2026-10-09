package com.example.tvcontrolcenter

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class ControlCenterActivity : Activity() {

    private companion object {
        const val COLOR_TEXT = 0xFFF2F2F7.toInt()
        const val COLOR_TEXT_DIM = 0x99EBEBF5.toInt()
        const val COLOR_DARK = 0xFF1C1C1E.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val ANIM = 160L
    }

    private class FocusableRow(
        val root: View,
        val icons: Array<ImageView>,
        val texts: Array<TextView>,
        val dimTextIndex: Int
    )

    private class Tile(
        val key: String,
        val root: View,
        val icon: ImageView,
        val label: TextView,
        var on: Boolean = false
    )

    private val rows = ArrayList<FocusableRow>()
    private val tiles = ArrayList<Tile>()

    private lateinit var panel: View
    private lateinit var scrim: View
    private var closing = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            panel.postDelayed({ refreshStates() }, 350)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrim = findViewById(R.id.scrim)
        panel = findViewById(R.id.panel)
        scrim.setOnClickListener { dismissCenter() }

        bindRow(R.id.rowUser, intArrayOf(R.id.ivAvatar, R.id.ivChevron), intArrayOf(R.id.tvUser), -1) {
            toast("Switch user is not available on this device")
        }
        bindRow(R.id.rowSleep, intArrayOf(R.id.ivSleep), intArrayOf(R.id.tvSleep), -1) {
            goToSleep()
        }
        bindRow(
            R.id.rowNowPlaying,
            intArrayOf(R.id.ivNpIcon),
            intArrayOf(R.id.tvNpTitle, R.id.tvNpSubtitle),
            1
        ) {
            toast("Nothing is playing right now")
        }

        bindTile("wifi", R.id.tileWifi, R.id.icWifi, R.id.lblWifi) { toggleWifi() }
        bindTile("bluetooth", R.id.tileBluetooth, R.id.icBluetooth, R.id.lblBluetooth) { toggleBluetooth() }
        bindTile("focus", R.id.tileFocus, R.id.icFocus, R.id.lblFocus) { toggleFocus() }
        bindTile("airplane", R.id.tileAirplane, R.id.icAirplane, R.id.lblAirplane) { openAirplaneSettings() }
        bindTile("screensaver", R.id.tileScreensaver, R.id.icScreensaver, R.id.lblScreensaver) { openScreensaverSettings() }
        bindTile("power", R.id.tilePower, R.id.icPower, R.id.lblPower) { goToSleep() }

        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 42)
        }

        refreshStates()
        panel.post { animateIn() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
        refreshStates()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(stateReceiver)
        } catch (t: Throwable) {
            // not registered
        }
    }

    // ------------------------------------------------------------------
    // Binding + focus styling (tvOS look: white pill, dark glyphs, scale)
    // ------------------------------------------------------------------

    private fun bindRow(rowId: Int, iconIds: IntArray, textIds: IntArray, dimIndex: Int, onClick: () -> Unit) {
        val root = findViewById<View>(rowId)
        val row = FocusableRow(
            root,
            iconIds.map { findViewById<ImageView>(it) }.toTypedArray(),
            textIds.map { findViewById<TextView>(it) }.toTypedArray(),
            dimIndex
        )
        rows.add(row)
        root.setOnClickListener {
            pulse(root, 1.04f)
            onClick()
        }
        root.setOnFocusChangeListener { _, hasFocus -> styleRow(row, hasFocus) }
    }

    private fun bindTile(key: String, rootId: Int, iconId: Int, labelId: Int, onClick: () -> Unit) {
        val root = findViewById<View>(rootId)
        val tile = Tile(key, root, findViewById(iconId), findViewById(labelId))
        tiles.add(tile)
        root.setOnClickListener {
            pulse(root, 1.07f)
            onClick()
            root.postDelayed({ refreshStates() }, 300)
        }
        root.setOnFocusChangeListener { _, hasFocus -> styleTile(tile, hasFocus) }
    }

    private fun styleRow(row: FocusableRow, focused: Boolean) {
        val scale = if (focused) 1.04f else 1f
        row.root.animate().scaleX(scale).scaleY(scale).setDuration(ANIM).start()
        row.root.translationZ = if (focused) dp(6) else 0f
        row.root.setBackgroundResource(if (focused) R.drawable.bg_row_focused else R.drawable.bg_row_normal)
        row.icons.forEach {
            it.imageTintList = ColorStateList.valueOf(if (focused) COLOR_DARK else COLOR_TEXT)
        }
        row.texts.forEachIndexed { i, tv ->
            tv.setTextColor(
                if (focused) COLOR_DARK
                else if (i == row.dimTextIndex) COLOR_TEXT_DIM
                else COLOR_TEXT
            )
        }
    }

    private fun styleTile(tile: Tile, focused: Boolean) {
        val scale = if (focused) 1.07f else 1f
        tile.root.animate().scaleX(scale).scaleY(scale).setDuration(ANIM).start()
        tile.root.translationZ = if (focused) dp(6) else 0f
        when {
            focused -> {
                tile.root.setBackgroundResource(R.drawable.bg_tile_focused)
                tile.icon.imageTintList = ColorStateList.valueOf(COLOR_DARK)
                tile.label.setTextColor(COLOR_DARK)
            }
            tile.on -> {
                tile.root.setBackgroundResource(R.drawable.bg_tile_on)
                tile.icon.imageTintList = ColorStateList.valueOf(COLOR_WHITE)
                tile.label.setTextColor(COLOR_WHITE)
            }
            else -> {
                tile.root.setBackgroundResource(R.drawable.bg_tile_normal)
                tile.icon.imageTintList = ColorStateList.valueOf(COLOR_TEXT)
                tile.label.setTextColor(COLOR_TEXT)
            }
        }
    }

    private fun pulse(v: View, focusedScale: Float) {
        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).withEndAction {
            val s = if (v.hasFocus()) focusedScale else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(110).start()
        }.start()
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private fun refreshStates() {
        for (tile in tiles) {
            tile.on = when (tile.key) {
                "wifi" -> isWifiOn()
                "bluetooth" -> isBluetoothOn()
                "focus" -> isFocusOn()
                "airplane" -> isAirplaneOn()
                else -> false
            }
            styleTile(tile, tile.root.hasFocus())
        }
    }

    private fun isWifiOn(): Boolean = try {
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (t: Throwable) {
        false
    }

    @Suppress("DEPRECATION")
    private fun isBluetoothOn(): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    } catch (t: Throwable) {
        false
    }

    private fun isFocusOn(): Boolean = try {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (t: Throwable) {
        false
    }

    private fun isAirplaneOn(): Boolean = try {
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (t: Throwable) {
        false
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun toggleWifi() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ok = wm.setWifiEnabled(!wm.isWifiEnabled)
            if (!ok) openWifiSettings()
        } catch (t: Throwable) {
            openWifiSettings()
        }
    }

    private fun openWifiSettings() {
        var opened = false
        if (Build.VERSION.SDK_INT >= 29) opened = launch(Intent(Settings.Panel.ACTION_WIFI))
        if (!opened) {
            if (!launch(Intent(Settings.ACTION_WIFI_SETTINGS))) toast("Wi-Fi settings not found")
        }
    }

    private fun toggleBluetooth() {
        val adapter = try {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        } catch (t: Throwable) {
            null
        }
        if (adapter == null) {
            toast("Bluetooth is not available on this device")
            return
        }
        try {
            if (adapter.isEnabled) disableBt(adapter) else enableBt(adapter)
        } catch (t: Throwable) {
            if (!launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) toast("Bluetooth settings not found")
        }
    }

    @Suppress("DEPRECATION")
    private fun enableBt(a: BluetoothAdapter) {
        if (!a.enable()) launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    @Suppress("DEPRECATION")
    private fun disableBt(a: BluetoothAdapter) {
        if (!a.disable()) launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun toggleFocus() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            toast("Grant Do Not Disturb access to use Focus")
            launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val target = if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else
            NotificationManager.INTERRUPTION_FILTER_ALL
        try {
            nm.setInterruptionFilter(target)
        } catch (t: Throwable) {
            toast("Unable to change Focus")
        }
    }

    private fun openAirplaneSettings() {
        if (!launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))) {
            toast("Airplane settings not found")
        }
    }

    private fun openScreensaverSettings() {
        if (!launch(Intent(Settings.ACTION_DREAM_SETTINGS))) {
            toast("No screen saver settings found")
        }
    }

    private fun goToSleep() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val m = PowerManager::class.java.getMethod("goToSleep", Long::class.javaPrimitiveType)
            m.invoke(pm, SystemClock.uptimeMillis())
        } catch (t: Throwable) {
            toast("Hold the power button on your remote to sleep")
        }
    }

    // ------------------------------------------------------------------
    // Panel animation + key handling
    // ------------------------------------------------------------------

    private fun animateIn() {
        scrim.alpha = 0f
        panel.translationX = panel.width.toFloat()
        scrim.animate().alpha(1f).setDuration(220).start()
        panel.animate()
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction { findViewById<View>(R.id.rowUser).requestFocus() }
            .start()
    }

    private fun dismissCenter() {
        if (closing) return
        closing = true
        scrim.animate().alpha(0f).setDuration(180).start()
        panel.animate()
            .translationX(panel.width.toFloat() + dp(24))
            .setDuration(200)
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        dismissCenter()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    dismissCenter()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val f = currentFocus
                    if (f != null && f.nextFocusRightId == f.id) {
                        dismissCenter()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun launch(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
