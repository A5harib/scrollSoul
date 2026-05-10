package com.scrollmind.tracker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ReelsTrackerService extends AccessibilityService {

    private static final String TAG = "ScrollMind.Service";
    private static final String IG = "com.instagram.android";
    private static final String PREFS = "scrollmind_prefs";
    private static final long SCROLL_DEBOUNCE_MS = 800;

    private boolean isInReelsView = false;
    private long currentReelId = -1;
    private long lastReelId = -1;
    private long lastScrollTimestamp = 0;
    
    private String currentUsername = "";
    private String currentCaption = "";
    private String currentLikeCount = "";
    private String lastUsername = "";
    private String lastCaption = "";

    // Toggles
    private boolean toggleMetadata = true, toggleWatchTime = true;
    private boolean toggleCompletion = true, toggleEngagement = true, toggleOcr = true;

    private TrackerDatabaseHelper dbHelper;
    private Handler handler;
    private Runnable progressPoller;
    private static ReelsTrackerService sInstance;

    // OCR & Overlay
    private WindowManager windowManager;
    private View overlayView;
    private TextView counterText;
    private TextRecognizer textRecognizer;
    private final Executor ocrExecutor = Executors.newSingleThreadExecutor();

    public static ReelsTrackerService getInstance() { return sInstance; }

    @Override public void onCreate() {
        super.onCreate();
        sInstance = this;
        dbHelper = TrackerDatabaseHelper.getInstance(this);
        handler = new Handler(Looper.getMainLooper());
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        loadToggles();
    }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        initOverlay();
        emit("onServiceConnected", "{}");
    }

    @Override public void onDestroy() {
        super.onDestroy(); 
        sInstance = null; 
        stopPolling();
        try { if (overlayView != null) windowManager.removeView(overlayView); } catch (Exception e) {}
        if (textRecognizer != null) textRecognizer.close();
    }

    @Override public void onInterrupt() { stopPolling(); }

    // ── LOGGERS & UI ──────────────────────────────────────────────────────────

    private void appendRawLog(String data) {
        try {
            File logFile = new File(getExternalFilesDir(null), "scroll_raw_data.txt");
            FileWriter writer = new FileWriter(logFile, true);
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            writer.append("[").append(timestamp).append("] ").append(data).append("\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {}
    }

    private void initOverlay() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = new TextView(this);
            counterText = (TextView) overlayView;
            
            GradientDrawable pillShape = new GradientDrawable();
            pillShape.setShape(GradientDrawable.RECTANGLE);
            pillShape.setCornerRadius(50f);
            pillShape.setColor(0xCC000000); 

            counterText.setBackground(pillShape);
            counterText.setPadding(40, 20, 40, 20);
            counterText.setTextColor(0xFFFFFFFF);
            counterText.setTextSize(14);
            counterText.setGravity(Gravity.CENTER);
            counterText.setText("Reels: 0");
            counterText.setVisibility(View.GONE);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 80; 
            windowManager.addView(overlayView, params);
        } catch (Exception e) {}
    }

    private void updateCounterUI() {
        handler.post(() -> {
            try {
                int count = dbHelper.getTodayReelCount();
                if (counterText != null) {
                    counterText.setText("Reels Today: " + count);
                    counterText.setVisibility(isInReelsView ? View.VISIBLE : View.GONE);
                }
            } catch (Exception e) {}
        });
    }

    // ── EVENT DISPATCHER ─────────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event == null) return;
            CharSequence pkg = event.getPackageName();
            if (pkg == null || !IG.equals(pkg.toString())) {
                if (isInReelsView) exitReels();
                return;
            }

            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    checkReelsState();
                    break;
                case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                    if (isInReelsView) onScroll(event);
                    break;
                // We removed TYPE_VIEW_CLICKED because we now poll the Like status at the end of the reel
            }
        } catch (Exception e) {}
    }

    // ── DETECTION ────────────────────────────────────────────────────────────

    private void checkReelsState() {
        boolean inReels = isReelsPlayer();
        if (inReels && !isInReelsView) enterReels();
        else if (!inReels && isInReelsView) exitReels();
        updateCounterUI(); 
    }

    private boolean isReelsPlayer() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            String[] containerIds = {
                IG + ":id/clips_viewer_view_pager", IG + ":id/reel_viewer_root",
                IG + ":id/fragment_clips_viewer_root", IG + ":id/clips_video_container"
            };
            for (String id : containerIds) {
                List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
                if (ns != null && !ns.isEmpty()) { recycle(ns); return true; }
            }
            return false;
        } finally { root.recycle(); }
    }

    private void enterReels() {
        isInReelsView = true;
        lastScrollTimestamp = System.currentTimeMillis();
        if (toggleCompletion) startPolling();
        emit("onEnterReels", "{}");
    }

    private void exitReels() {
        isInReelsView = false;
        finalizeReel();
        stopPolling();
        int count = dbHelper.getTodayReelCount();
        try { JSONObject d = new JSONObject(); d.put("reelsWatched", count); emit("onExitReels", d.toString()); } catch (JSONException e) {}
    }

    // ── SCROLL & SAVE LOGIC ──────────────────────────────────────────

    private void onScroll(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastScrollTimestamp < SCROLL_DEBOUNCE_MS) return;

        // Save watch time and check if they liked it before moving on
        finalizeReel();

        currentReelId = -1;
        currentUsername = "";
        currentCaption = ""; 
        currentLikeCount = "";
        lastScrollTimestamp = now;

        if (toggleMetadata) {
            handler.postDelayed(() -> {
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null) { try { scrape(r); } finally { r.recycle(); } }

                if (currentUsername.equals(lastUsername) && currentCaption.equals(lastCaption) && !currentUsername.isEmpty()) {
                    currentReelId = lastReelId; 
                } else {
                    currentReelId = dbHelper.insertReel(currentUsername, currentCaption);
                    lastUsername = currentUsername;
                    lastCaption = currentCaption;
                    lastReelId = currentReelId;
                }

                updateCounterUI();
                emitScroll(now); // Emits UI Scrape data immediately
                if (toggleOcr) takeScreenshotAndProcess(currentReelId);
                
            }, 600);
        } else {
            currentReelId = dbHelper.insertReel("", "");
            updateCounterUI();
            emitScroll(now);
        }
    }

    // ── OCR & SCREENSHOT (FIXED LOGIC) ───────────────────────────────────────

    private void takeScreenshotAndProcess(long targetReelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, ocrExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull ScreenshotResult screenshotResult) {
                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace());
                    if (bitmap != null) {
                        Bitmap swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                        processBitmap(swBitmap, targetReelId);
                        bitmap.recycle();
                    }
                }
                @Override public void onFailure(int i) {}
            });
        }
    }

    private void processBitmap(Bitmap bitmap, long targetReelId) {
        String thumbPath = saveThumbnail(bitmap);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        
        textRecognizer.process(image)
            .addOnSuccessListener(visionText -> {
                String ocrUser = "";
                StringBuilder ocrCap = new StringBuilder();
                
                // NEW: Highly aggressive OCR text grabbing
                for (Text.TextBlock block : visionText.getTextBlocks()) {
                    String text = block.getText().trim();
                    if (text.startsWith("@") && ocrUser.isEmpty()) {
                        ocrUser = text.replace("@", "");
                    } else if (text.length() > 6) {
                        ocrCap.append(text).append(" ");
                    }
                }

                final String finalUser = ocrUser;
                final String finalCap = ocrCap.toString().trim();

                handler.post(() -> {
                    if (targetReelId > 0) {
                        if (!thumbPath.isEmpty()) dbHelper.updateReelThumbnail(targetReelId, thumbPath);
                        dbHelper.updateReelOcrText(targetReelId, visionText.getText());
                        
                        // Retroactively update DB if UI Scraping failed but OCR found it
                        if (currentUsername.isEmpty() && (!finalUser.isEmpty() || !finalCap.isEmpty())) {
                            dbHelper.updateReelMetadata(targetReelId, finalUser, finalCap);
                        }
                    }
                    // Trigger RN to refresh history screen with the new Thumbnail and OCR data
                    emitScroll(System.currentTimeMillis());
                });
                bitmap.recycle(); 
            })
            .addOnFailureListener(e -> {
                handler.post(() -> {
                    if (targetReelId > 0 && !thumbPath.isEmpty()) dbHelper.updateReelThumbnail(targetReelId, thumbPath);
                    emitScroll(System.currentTimeMillis());
                });
                bitmap.recycle(); 
            });
    }

    // ── ENGAGEMENT & WATCH TIME (STATE BASED) ────────────────────────────────

    private void finalizeReel() {
        if (currentReelId > 0) {
            long wt = System.currentTimeMillis() - lastScrollTimestamp;
            double comp = getCompletion();
            
            // STATE-BASED ENGAGEMENT: Check if the like button is active right before we leave
            boolean wasLiked = false;
            boolean wasCommented = false;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                wasLiked = checkEngagementState(root, "unlike"); // If the button says 'Unlike', it was liked
                wasCommented = checkEngagementState(root, "add a comment"); // Checking if comment box is open
                root.recycle();
            }

            dbHelper.updateReelWatchData(currentReelId, wt, comp);
            if (wasLiked || wasCommented) {
                dbHelper.updateReelEngagement(currentReelId, wasLiked, wasCommented, false);
            }
        }
    }

    private boolean checkEngagementState(AccessibilityNodeInfo node, String keyword) {
        if (node == null) return false;
        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();
        
        if (desc != null && desc.toString().toLowerCase().contains(keyword)) return true;
        if (text != null && text.toString().toLowerCase().contains(keyword)) return true;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { 
                boolean found = checkEngagementState(child, keyword); 
                child.recycle(); 
                if (found) return true; 
            }
        }
        return false;
    }

    // ── UI SCRAPING ──────────────────────────────────────────────────────────

    private void scrape(AccessibilityNodeInfo root) {
        String[] lIds = { IG + ":id/like_count", IG + ":id/row_feed_textview_likes", IG + ":id/clips_viewer_like_count" };
        for (String id : lIds) {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
            if (ns != null && !ns.isEmpty()) {
                for (AccessibilityNodeInfo n : ns) {
                    String t = getText(n).trim();
                    if (!t.isEmpty()) { currentLikeCount = t.replaceAll("[^0-9kmKM.]", ""); break; }
                }
                recycle(ns);
                if (!currentLikeCount.isEmpty()) break;
            }
        }
        
        String[] uIds = { IG + ":id/reel_viewer_username", IG + ":id/clips_username", IG + ":id/username_text_view" };
        for (String id : uIds) {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
            if (ns != null && !ns.isEmpty()) {
                for (AccessibilityNodeInfo n : ns) {
                    String t = getText(n);
                    if (!t.isEmpty() && !t.contains(" ")) { currentUsername = t.replace("@", "").trim(); break; }
                }
                recycle(ns);
                if (!currentUsername.isEmpty()) break;
            }
        }

        String[] cIds = { IG + ":id/clips_caption", IG + ":id/reel_viewer_caption", IG + ":id/clips_caption_text" };
        for (String id : cIds) {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
            if (ns != null && !ns.isEmpty()) {
                for (AccessibilityNodeInfo n : ns) {
                    String t = deepText(n);
                    if (!t.isEmpty()) { currentCaption = t.trim(); break; }
                }
                recycle(ns);
                if (!currentCaption.isEmpty()) break;
            }
        }
    }

    private String getText(AccessibilityNodeInfo n) { return n == null || n.getText() == null ? "" : n.getText().toString(); }

    private String deepText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        collectText(node, sb, 0);
        return sb.toString().trim();
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 6) return;
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) { sb.append(t).append(" "); }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) { collectText(c, sb, depth + 1); c.recycle(); }
        }
    }

    // ── UTILS ─────────────────────────────────────────────────────────────

    private String saveThumbnail(Bitmap bitmap) {
        File dir = new File(getExternalFilesDir(null), "thumbnails");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "reel_" + System.currentTimeMillis() + ".webp");
        try (FileOutputStream out = new FileOutputStream(file)) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 4, bitmap.getHeight() / 4, true);
            scaled.compress(Bitmap.CompressFormat.WEBP, 70, out);
            scaled.recycle();
            return "file://" + file.getAbsolutePath(); // Added file:// prefix so RN Image component can read it
        } catch (Exception e) { return ""; }
    }

    private void emitScroll(long ts) {
        try {
            int todayCount = dbHelper.getTodayReelCount();
            JSONObject d = new JSONObject();
            d.put("reelNumber", todayCount);
            d.put("reelId", currentReelId);
            d.put("username", currentUsername);
            d.put("caption", currentCaption);
            d.put("likeCount", currentLikeCount); 
            d.put("timestamp", ts);
            appendRawLog(d.toString());
            emit("onReelScrolled", d.toString());
        } catch (JSONException e) {}
    }

    private void startPolling() {
        stopPolling();
        progressPoller = new Runnable() {
            @Override public void run() {
                if (isInReelsView && currentReelId > 0) {
                    double c = getCompletion();
                    if (c > 0) { try { JSONObject d = new JSONObject(); d.put("reelId", currentReelId); d.put("completionPercent", c); emit("onProgressUpdate", d.toString()); } catch (JSONException e) {} }
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.postDelayed(progressPoller, 500);
    }

    private void stopPolling() {
        if (progressPoller != null) { handler.removeCallbacks(progressPoller); progressPoller = null; }
    }

    private double getCompletion() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return 0;
        try {
            String[] ids = { IG + ":id/clips_progress_bar", IG + ":id/reel_viewer_progress", IG + ":id/scrubber" };
            for (String id : ids) {
                List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
                if (ns != null && !ns.isEmpty()) {
                    for (AccessibilityNodeInfo n : ns) {
                        AccessibilityNodeInfo.RangeInfo ri = n.getRangeInfo();
                        if (ri != null && ri.getMax() > 0) { double p = (ri.getCurrent() / ri.getMax()) * 100; recycle(ns); return p; }
                    }
                    recycle(ns);
                }
            }
            return 0;
        } finally { root.recycle(); }
    }

    private void recycle(List<AccessibilityNodeInfo> ns) { if (ns != null) for (AccessibilityNodeInfo n : ns) if (n != null) n.recycle(); }
    private void emit(String name, String json) { ReelsTrackerModule m = ReelsTrackerModule.getInstance(); if (m != null) m.sendEvent(name, json); }
    private void loadToggles() { SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE); toggleMetadata = p.getBoolean("toggle_metadata", true); toggleWatchTime = p.getBoolean("toggle_watchtime", true); toggleCompletion = p.getBoolean("toggle_completion", true); toggleEngagement = p.getBoolean("toggle_engagement", true); toggleOcr = p.getBoolean("toggle_ocr", true); }
    public void saveToggle(String key, boolean val) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(key, val).apply(); switch (key) { case "toggle_metadata": toggleMetadata = val; break; case "toggle_watchtime": toggleWatchTime = val; break; case "toggle_completion": toggleCompletion = val; if (val && isInReelsView) startPolling(); else stopPolling(); break; case "toggle_engagement": toggleEngagement = val; break; case "toggle_ocr": toggleOcr = val; break; } }
    public boolean getToggle(String key) { return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, true); }
    public String getServiceStatus() { try { int count = dbHelper.getTodayReelCount(); JSONObject s = new JSONObject(); s.put("isRunning", true); s.put("isInReelsView", isInReelsView); s.put("currentReelId", currentReelId); s.put("reelCounter", count); s.put("currentUsername", currentUsername); return s.toString(); } catch (JSONException e) { return "{}"; } }
}