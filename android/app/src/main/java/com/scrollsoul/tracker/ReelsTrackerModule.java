package com.scrollsoul.tracker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.util.Base64;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.Display;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;

/**
 * React Native Native Module that bridges the AccessibilityService to JavaScript.
 *
 * Exposed methods:
 *  - isServiceEnabled()         → Check if the accessibility service is currently enabled
 *  - openAccessibilitySettings()→ Navigate user to accessibility settings
 *  - getServiceStatus()         → Get current tracking state
 *  - writeTextFile()            → Write local JSON data to file storage
 *  - readTextFile()             → Read local JSON data from file storage
 *  - takeScreenshot()           → Take visual screenshot of target display and return base64
 *  - updateOverlay()            → Set overlay visual counter text
 *  - setToggle()                → Set shared preference toggle state
 *  - getToggle()                → Get shared preference toggle state
 */
public class ReelsTrackerModule extends ReactContextBaseJavaModule {

    private static final String TAG = "ScrollSoul.Module";
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

    @ReactMethod
    public void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        reactContext.startActivity(intent);
    }

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

    @ReactMethod
    public void writeTextFile(String filename, String content, Promise promise) {
        try {
            File file = new File(reactContext.getFilesDir(), filename);
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to write file: " + e.getMessage(), e);
        }
    }

    @ReactMethod
    public void readTextFile(String filename, Promise promise) {
        try {
            File file = new File(reactContext.getFilesDir(), filename);
            if (!file.exists()) {
                promise.resolve("");
                return;
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            promise.resolve(sb.toString().trim());
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to read file: " + e.getMessage(), e);
        }
    }

    @ReactMethod
    public void takeScreenshot(Promise promise) {
        ReelsTrackerService service = ReelsTrackerService.getInstance();
        if (service == null) {
            promise.reject("ERROR", "ReelsTrackerService is not running");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, new java.util.concurrent.Executor() {
                    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    @Override
                    public void execute(Runnable command) {
                        handler.post(command);
                    }
                }, new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(@NonNull AccessibilityService.ScreenshotResult screenshotResult) {
                        try {
                            Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace());
                            if (bitmap != null) {
                                Bitmap swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                
                                // Scale down to optimize upload bandwidth for Groq
                                int originalWidth = swBitmap.getWidth();
                                int originalHeight = swBitmap.getHeight();
                                int targetWidth = 720;
                                int targetHeight = (int) (originalHeight * ((float) targetWidth / originalWidth));
                                Bitmap scaledBitmap = Bitmap.createScaledBitmap(swBitmap, targetWidth, targetHeight, true);
                                
                                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
                                byte[] bytes = out.toByteArray();
                                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                                
                                scaledBitmap.recycle();
                                swBitmap.recycle();
                                bitmap.recycle();
                                
                                promise.resolve(base64);
                            } else {
                                promise.reject("ERROR", "Screenshot bitmap was null");
                            }
                        } catch (Exception e) {
                            promise.reject("ERROR", "Failed to process screenshot: " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        promise.reject("ERROR", "Screenshot failed with error code: " + errorCode);
                    }
                });
            } catch (Exception e) {
                promise.reject("ERROR", "Failed to invoke takeScreenshot: " + e.getMessage(), e);
            }
        } else {
            promise.reject("ERROR", "Screenshot requires Android 11 (API 30) or above");
        }
    }

    @ReactMethod
    public void updateOverlay(String text, Promise promise) {
        try {
            ReelsTrackerService service = ReelsTrackerService.getInstance();
            if (service != null) {
                service.updateOverlayText(text);
                promise.resolve(true);
            } else {
                promise.resolve(false);
            }
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to update overlay: " + e.getMessage(), e);
        }
    }

    @ReactMethod
    public void setToggle(String key, boolean value, Promise promise) {
        try {
            ReelsTrackerService service = ReelsTrackerService.getInstance();
            if (service != null) {
                service.saveToggle(key, value);
                promise.resolve(true);
            } else {
                reactContext.getSharedPreferences("scrollsoul_prefs", 0)
                        .edit().putBoolean(key, value).apply();
                promise.resolve(true);
            }
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to set toggle", e);
        }
    }

    @ReactMethod
    public void getToggle(String key, Promise promise) {
        try {
            ReelsTrackerService service = ReelsTrackerService.getInstance();
            if (service != null) {
                promise.resolve(service.getToggle(key));
            } else {
                boolean val = reactContext.getSharedPreferences("scrollsoul_prefs", 0)
                        .getBoolean(key, true);
                promise.resolve(val);
            }
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get toggle", e);
        }
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Required for RN built-in Event Emitter
    }

    @ReactMethod
    public void removeListeners(int count) {
        // Required for RN built-in Event Emitter
    }
}
