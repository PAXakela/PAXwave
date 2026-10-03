package com.music.bitchord.data.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * PAXwave's battery settings, and whether battery saver is in force right now.
 *
 * Battery saver never rewrites the user's own choices. It is a layer on top:
 * while [active], [AppSettings] reports the heavy visual options as off (see its
 * derived flows), track analysis waits, read-ahead shrinks to the next track,
 * Discord's socket is closed and the screen asks for its standard refresh rate.
 * Turning it off puts everything back exactly as it was.
 */
object BatterySaver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: SharedPreferences

    /** The user's own switch: battery saver on regardless of the system. */
    val manual = MutableStateFlow(false)

    /** Turn battery saver on whenever Android's own Battery Saver is on. */
    val followSystem = MutableStateFlow(true)

    /** Automix and vocal analysis run only while the phone is charging. */
    val analysisOnlyWhileCharging = MutableStateFlow(false)

    /** Read upcoming songs into the cache ahead of time. */
    val preloadUpcoming = MutableStateFlow(true)

    /** How often podcasts are checked in the background; 0 is never. */
    val podcastRefreshHours = MutableStateFlow(DEFAULT_REFRESH_HOURS)

    /** Background podcast checks and auto-downloads wait for Wi-Fi. */
    val podcastRefreshWifiOnly = MutableStateFlow(false)

    /** Background podcast checks and auto-downloads wait for a charger. */
    val podcastRefreshWhileCharging = MutableStateFlow(false)

    /**
     * Podcasts and radio are handed to the phone's audio chip ("offload") when
     * no effect needs them on the main CPU. See PlaybackService.applyOffloadPolicy.
     */
    val efficientPodcastPlayback = MutableStateFlow(true)

    /** Android's Battery Saver, as last reported by the system. */
    val systemPowerSave = MutableStateFlow(false)

    /** Whether a charger is connected right now. */
    val charging = MutableStateFlow(false)

    /** Whether PAXwave's battery saver is in force. */
    val active: StateFlow<Boolean> = combine(manual, followSystem, systemPowerSave) { manual, follow, system ->
        manual || (follow && system)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("paxwave_battery", Context.MODE_PRIVATE)
        manual.value = prefs.getBoolean(KEY_MANUAL, false)
        followSystem.value = prefs.getBoolean(KEY_FOLLOW_SYSTEM, true)
        analysisOnlyWhileCharging.value = prefs.getBoolean(KEY_ANALYSIS_CHARGING, false)
        preloadUpcoming.value = prefs.getBoolean(KEY_PRELOAD, true)
        podcastRefreshHours.value = prefs.getInt(KEY_REFRESH_HOURS, DEFAULT_REFRESH_HOURS)
        podcastRefreshWifiOnly.value = prefs.getBoolean(KEY_REFRESH_WIFI, false)
        podcastRefreshWhileCharging.value = prefs.getBoolean(KEY_REFRESH_CHARGING, false)
        efficientPodcastPlayback.value = prefs.getBoolean(KEY_EFFICIENT_PODCASTS, true)

        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java)
        systemPowerSave.value = power?.isPowerSaveMode == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                        systemPowerSave.value = power?.isPowerSaveMode == true
                    Intent.ACTION_POWER_CONNECTED -> charging.value = true
                    Intent.ACTION_POWER_DISCONNECTED -> charging.value = false
                    Intent.ACTION_BATTERY_CHANGED -> charging.value = isCharging(intent)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // ACTION_BATTERY_CHANGED is sticky: registering returns the current
        // state straight away, which is the charging flag's starting value.
        app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            charging.value = isCharging(it)
        }
        // System broadcasts only, so nothing from other apps needs to reach it.
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun isCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    /** Whether Automix / vocal analysis may spend CPU on a track right now. */
    fun analysisAllowed(): Boolean =
        !active.value && (!analysisOnlyWhileCharging.value || charging.value)

    /** How many upcoming songs to read ahead: none, the next one, or the usual queue. */
    fun preloadDepth(normal: Int): Int = when {
        !preloadUpcoming.value -> 0
        active.value -> 1
        else -> normal
    }

    fun setManual(value: Boolean) = save(KEY_MANUAL, value) { manual.value = it }
    fun setFollowSystem(value: Boolean) = save(KEY_FOLLOW_SYSTEM, value) { followSystem.value = it }
    fun setAnalysisOnlyWhileCharging(value: Boolean) = save(KEY_ANALYSIS_CHARGING, value) { analysisOnlyWhileCharging.value = it }
    fun setPreloadUpcoming(value: Boolean) = save(KEY_PRELOAD, value) { preloadUpcoming.value = it }
    fun setPodcastRefreshWifiOnly(value: Boolean) = save(KEY_REFRESH_WIFI, value) { podcastRefreshWifiOnly.value = it }
    fun setPodcastRefreshWhileCharging(value: Boolean) = save(KEY_REFRESH_CHARGING, value) { podcastRefreshWhileCharging.value = it }

    fun setEfficientPodcastPlayback(value: Boolean) = save(KEY_EFFICIENT_PODCASTS, value) { efficientPodcastPlayback.value = it }

    fun setPodcastRefreshHours(value: Int) {
        podcastRefreshHours.value = value
        prefs.edit().putInt(KEY_REFRESH_HOURS, value).apply()
    }

    private inline fun save(key: String, value: Boolean, apply: (Boolean) -> Unit) {
        apply(value)
        prefs.edit().putBoolean(key, value).apply()
    }

    /** The choices offered for background podcast checks, in hours; 0 is "never". */
    val REFRESH_CHOICES = listOf(3, 6, 12, 24, 0)
    const val DEFAULT_REFRESH_HOURS = 6

    private const val KEY_MANUAL = "saver_manual"
    private const val KEY_FOLLOW_SYSTEM = "saver_follow_system"
    private const val KEY_ANALYSIS_CHARGING = "analysis_only_charging"
    private const val KEY_PRELOAD = "preload_upcoming"
    private const val KEY_REFRESH_HOURS = "podcast_refresh_hours"
    private const val KEY_REFRESH_WIFI = "podcast_refresh_wifi"
    private const val KEY_REFRESH_CHARGING = "podcast_refresh_charging"
    private const val KEY_EFFICIENT_PODCASTS = "efficient_podcast_playback"
}
