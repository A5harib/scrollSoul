package com.scrollmind.tracker;

import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

/**
 * React Native Native Module that bridges the AccessibilityService to JavaScript.
 *
 * Exposed methods:
 *  - isServiceEnabled()     → Check if the accessibility service is currently enabled
 *  - openAccessibilitySettings() → Navigate user to accessibility settings
 *  - getServiceStatus()     → Get current tracking state
 *  - getRecentReels(limit)  → Query recent reels from SQLite
 *  - getStats()             → Get aggregate analytics
 *  - setToggle(key, value)  → Enable/disable specific features
 *  - getToggle(key)         → Get a toggle's current state
 */
public class ReelsTrackerModule extends ReactContextBaseJavaModule {

    private static final String TAG = "ScrollMind.Module";
    private static final String MODULE_NAME = "ReelsTrackerModule";

    private final ReactApplicationContext reactContext;
    private static ReelsTrackerModule sInstance;

    public ReelsTrackerModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
        sInstance = this;
    }

    public static ReelsTrackerModule getInstance() {
        return sInstance;
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  EVENT EMISSION (called from AccessibilityService)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Sends a named event with JSON data to React Native's DeviceEventEmitter.
     */
    public void sendEvent(String eventName, String jsonData) {
        if (reactContext == null || !reactContext.hasActiveReactInstance()) {
            Log.w(TAG, "No active React instance — event dropped: " + eventName);
            return;
        }

        try {
            WritableMap params = Arguments.createMap();
            params.putString("data", jsonData);

            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);

            Log.d(TAG, "📡 Emitted event: " + eventName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to emit event: " + eventName, e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  REACT METHODS — Exposed to JavaScript
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Checks if the ScrollMind accessibility service is currently enabled.
     */
    @ReactMethod
    public void isServiceEnabled(Promise promise) {
        try {
            String enabledServices = Settings.Secure.getString(
                    reactContext.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

            boolean enabled = enabledServices != null &&
                    enabledServices.contains(reactContext.getPackageName() + "/" +
                            ReelsTrackerService.class.getName());

            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to check service status", e);
        }
    }

    /**
     * Opens the Android Accessibility Settings page.
     */
    @ReactMethod
    public void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        reactContext.startActivity(intent);
    }

    /**
     * Returns the current service status (tracking state, toggles, etc.)
     */
    @ReactMethod
    public void getServiceStatus(Promise promise) {
        try {
            ReelsTrackerService service = ReelsTrackerService.getInstance();
            if (service != null) {
                promise.resolve(service.getServiceStatus());
            } else {
                promise.resolve("{\"isRunning\": false}");
            }
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get service status", e);
        }
    }

    /**
     * Returns the most recent N reels as a JSON array string.
     */
    @ReactMethod
    public void getRecentReels(int limit, Promise promise) {
        try {
            TrackerDatabaseHelper db = TrackerDatabaseHelper.getInstance(reactContext);
            String json = db.getRecentReelsJSON(limit);
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get recent reels", e);
        }
    }

    /**
     * Returns aggregate analytics stats as a JSON string.
     */
    @ReactMethod
    public void getStats(Promise promise) {
        try {
            TrackerDatabaseHelper db = TrackerDatabaseHelper.getInstance(reactContext);
            String json = db.getAggregateStatsJSON();
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get stats", e);
        }
    }

    /**
     * Returns the total count of tracked reels.
     */
    @ReactMethod
    public void getTotalReelCount(Promise promise) {
        try {
            TrackerDatabaseHelper db = TrackerDatabaseHelper.getInstance(reactContext);
            promise.resolve(db.getTotalReelCount());
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get reel count", e);
        }
    }

    /**
     * Returns un-synced reels for cloud sync.
     */
    @ReactMethod
    public void getUnsyncedReels(Promise promise) {
        try {
            TrackerDatabaseHelper db = TrackerDatabaseHelper.getInstance(reactContext);
            String json = db.getUnsyncedReelsJSON();
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get unsynced reels", e);
        }
    }

    /**
     * Sets a feature toggle on the accessibility service.
     * Valid keys: toggle_metadata, toggle_watchtime, toggle_completion,
     *             toggle_engagement, toggle_ocr
     */
    @ReactMethod
    public void setToggle(String key, boolean value, Promise promise) {
        try {
            ReelsTrackerService service = ReelsTrackerService.getInstance();
            if (service != null) {
                service.saveToggle(key, value);
                promise.resolve(true);
            } else {
                // Service not running — save to SharedPreferences directly
                reactContext.getSharedPreferences("scrollmind_prefs", 0)
                        .edit().putBoolean(key, value).apply();
                promise.resolve(true);
            }
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to set toggle", e);
        }
    }

    /**
     * Gets a feature toggle's current value.
     */
    @ReactMethod
    public void getToggle(String key, Promise promise) {
        try {
            ReelsTrackerService service = ReelsTrackerService.getInstance();
            if (service != null) {
                promise.resolve(service.getToggle(key));
            } else {
                boolean val = reactContext.getSharedPreferences("scrollmind_prefs", 0)
                        .getBoolean(key, true);
                promise.resolve(val);
            }
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get toggle", e);
        }
    }

    /**
     * Clears all reel history from the local database.
     */
    @ReactMethod
    public void clearHistory(Promise promise) {
        try {
            TrackerDatabaseHelper db = TrackerDatabaseHelper.getInstance(reactContext);
            db.clearAllReels();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to clear history", e);
        }
    }

    /**
     * Required for DeviceEventEmitter listeners in RN.
     */
    @ReactMethod
    public void addListener(String eventName) {
        // Required for RN built-in Event Emitter
    }

    @ReactMethod
    public void removeListeners(int count) {
        // Required for RN built-in Event Emitter
    }
}
