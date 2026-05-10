package com.scrollmind.tracker;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
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

    // State
    private boolean isInReelsView = false;
    private long currentReelId = -1;
    private long lastReelId = -1;
    private long lastScrollTimestamp = 0;
    private int reelCounter = 0;
    
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
        Log.i(TAG, "Service created");
    }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        initOverlay();
        Log.i(TAG, "Service connected");
        emit("onServiceConnected", "{}");
    }

    @Override public void onDestroy() {
        super.onDestroy(); 
        sInstance = null; 
        stopPolling();
        if (overlayView != null) windowManager.removeView(overlayView);
        if (textRecognizer != null) textRecognizer.close();
    }

    @Override public void onInterrupt() {
        Log.w(TAG, "Service interrupted"); stopPolling();
    }

    private void initOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new TextView(this);
        counterText = (TextView) overlayView;
        
        // Style the counter (tablet style)
        counterText.setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame);
        counterText.setPadding(30, 10, 30, 10);
        counterText.setTextColor(0xFF000000);
        counterText.setTextSize(14);
        counterText.setAlpha(0.8f);
        counterText.setGravity(Gravity.CENTER);
        counterText.setText("ScrollMind: 0");
        counterText.setVisibility(View.GONE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 50;
        
        windowManager.addView(overlayView, params);
    }

    private void updateCounterUI() {
        handler.post(() -> {
            int count = dbHelper.getTodayReelCount();
            counterText.setText("Reels Today: " + count);
            if (isInReelsView) {
                counterText.setVisibility(View.VISIBLE);
            } else {
                counterText.setVisibility(View.GONE);
            }
        });
    }

    // ── FILE LOGGER FOR DEBUGGING ────────────────────────────────────────────

   // ── FILE LOGGER FOR DEBUGGING ────────────────────────────────────────────

    private void logToFile(String tag, String id, String value) {
        String shortId = id.contains("/") ? id.substring(id.lastIndexOf("/") + 1) : id;
        
        // Format:  TAG: [id_name] -> The text value
        String logLine = tag + ": [" + (shortId.isEmpty() ? "NO_ID" : shortId) + "] -> " + value.replace("\n", " | ");
        
        // We broadcast this specific log to the "ScrollMind.LogStream" tag
        Log.d("ScrollMind.LogStream", logLine);
    }

    private void scanAndLogAllNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 15) return;

        String text = getText(node);
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String id = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";
        String cls = node.getClassName() != null ? node.getClassName().toString() : "";
        String shortCls = cls.contains(".") ? cls.substring(cls.lastIndexOf(".") + 1) : cls;

        // Log ALL nodes with text or desc, even without IDs
        if (!text.isEmpty()) {
            logToFile("SCAN_TEXT", id.isEmpty() ? shortCls : id, text.replace("\n", " | "));
        }
        if (!desc.isEmpty() && desc.length() > 2) {
            logToFile("SCAN_DESC", id.isEmpty() ? shortCls : id, desc.replace("\n", " | "));
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scanAndLogAllNodes(child, depth + 1);
                child.recycle();
            }
        }
    }

    // ── EVENT DISPATCHER ─────────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !IG.equals(pkg.toString())) {
            if (isInReelsView) exitReels();
            return;
        }

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                checkReelsState();
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                if (isInReelsView) onScroll(event);
                break;
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                if (isInReelsView && toggleEngagement) onClick(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                if (isInReelsView) {
                    if (!isReelsPlayer()) exitReels();
                } else {
                    checkReelsState();
                }
                break;
        }
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
            // Priority 1: Check for like_count node (user's requested signal)
            List<AccessibilityNodeInfo> likes = root.findAccessibilityNodeInfosByViewId(IG + ":id/like_count");
            if (likes != null && !likes.isEmpty()) {
                recycle(likes);
                return true;
            }

            // Priority 2: Check standard Reel containers
            int screenH = getResources().getDisplayMetrics().heightPixels;
            String[] containerIds = {
                IG + ":id/clips_viewer_view_pager", IG + ":id/reel_viewer_root",
                IG + ":id/fragment_clips_viewer_root", IG + ":id/layout_clips_viewer_root",
                IG + ":id/clips_video_container"
            };
            for (String id : containerIds) {
                List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
                if (ns != null && !ns.isEmpty()) {
                    for (AccessibilityNodeInfo n : ns) {
                        Rect b = new Rect(); n.getBoundsInScreen(b);
                        if (b.height() > screenH * 0.5) { recycle(ns); return true; }
                    }
                    recycle(ns);
                }
            }
            
            // Check caption as fallback
            String[] fallbackIds = { IG + ":id/clips_caption", IG + ":id/reel_viewer_caption" };
            for (String id : fallbackIds) {
                List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
                if (ns != null && !ns.isEmpty()) { recycle(ns); return true; }
            }

            return false;
        } finally {
            root.recycle();
        }
    }

    private String getActiveTab(AccessibilityNodeInfo root) {
        String[] tabDescriptions = {"Home", "Search", "Reels", "Shop", "Profile", "Create", "Notifications"};
        for (String tabDesc : tabDescriptions) {
            AccessibilityNodeInfo found = findNodeByContentDesc(root, tabDesc, 0);
            if (found != null) {
                if (found.isSelected()) { found.recycle(); return tabDesc; }
                found.recycle();
            }
        }
        boolean hasAnyTab = false;
        for (String tabDesc : tabDescriptions) {
            AccessibilityNodeInfo found = findNodeByContentDesc(root, tabDesc, 0);
            if (found != null) { hasAnyTab = true; found.recycle(); break; }
        }
        return hasAnyTab ? "unknown" : "none";
    }

    private AccessibilityNodeInfo findNodeByContentDesc(AccessibilityNodeInfo node, String target, int depth) {
        if (node == null || depth > 4) return null;

        Rect b = new Rect(); node.getBoundsInScreen(b);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        if (b.height() <= 0 || b.top >= screenH || b.bottom <= 0) return null; 

        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().trim();
            if (d.equals(target) || d.startsWith(target)) return AccessibilityNodeInfo.obtain(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeByContentDesc(child, target, depth + 1);
                child.recycle();
                if (result != null) return result;
            }
        }
        return null;
    }

    private void enterReels() {
        isInReelsView = true;
        lastScrollTimestamp = System.currentTimeMillis();
        reelCounter = 0;
        Log.i(TAG, ">>> ENTERED REELS");
        if (toggleCompletion) startPolling();
        emit("onEnterReels", "{}");
    }

    private void exitReels() {
        isInReelsView = false;
        finalizeReel();
        stopPolling();
        try { JSONObject d = new JSONObject(); d.put("reelsWatched", reelCounter); emit("onExitReels", d.toString()); } catch (JSONException e) {}
    }

    // ── SCROLL (debounced) ───────────────────────────────────────────────────

    private void onScroll(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastScrollTimestamp < SCROLL_DEBOUNCE_MS) return;

        if (toggleWatchTime && currentReelId > 0) {
            long wt = now - lastScrollTimestamp;
            dbHelper.updateReelWatchData(currentReelId, wt, getCompletion());
        }

        currentReelId = -1;
        currentUsername = "";
        currentCaption = ""; 
        currentLikeCount = "";

        lastScrollTimestamp = now;
        reelCounter++;

        logToFile("\n======", "======", "NEW REEL SCROLLED (Reel #" + reelCounter + ")");
        updateCounterUI();

        if (toggleMetadata) {
            handler.postDelayed(() -> {
                takeScreenshotAndProcess();
            }, 800);
        } else {
            currentReelId = dbHelper.insertReel("", "");
            emitScroll(now);
        }
    }

    private void takeScreenshotAndProcess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, ocrExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull ScreenshotResult screenshotResult) {
                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace());
                    if (bitmap != null) {
                        // Hardware bitmaps are not directly usable by ML Kit, copy to software
                        Bitmap swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                        processBitmap(swBitmap);
                        // Recycle original hardware bitmap
                        bitmap.recycle();
                    }
                }

                @Override
                public void onFailure(int i) {
                    Log.e(TAG, "Screenshot failed: " + i);
                    handler.post(() -> {
                        AccessibilityNodeInfo r = getRootInActiveWindow();
                        if (r != null) { try { scrape(r); } finally { r.recycle(); } }
                        emitScroll(System.currentTimeMillis());
                    });
                }
            });
        } else {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) { try { scrape(r); } finally { r.recycle(); } }
            emitScroll(System.currentTimeMillis());
        }
    }

    private void processBitmap(Bitmap bitmap) {
        String thumbPath = saveThumbnail(bitmap);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        textRecognizer.process(image)
            .addOnSuccessListener(visionText -> {
                parseOcrResult(visionText);
                handler.post(() -> {
                    currentReelId = dbHelper.insertReel(currentUsername, currentCaption);
                    if (!thumbPath.isEmpty()) dbHelper.updateReelThumbnail(currentReelId, thumbPath);
                    dbHelper.updateReelOcrText(currentReelId, visionText.getText());
                    AccessibilityNodeInfo r = getRootInActiveWindow();
                    if (r != null) { try { scrape(r); } finally { r.recycle(); } }
                    emitScroll(System.currentTimeMillis());
                    updateCounterUI();
                });
                bitmap.recycle(); // Done with bitmap
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "OCR failed", e);
                handler.post(() -> {
                    currentReelId = dbHelper.insertReel("OCR_FAILED", "");
                    if (!thumbPath.isEmpty()) dbHelper.updateReelThumbnail(currentReelId, thumbPath);
                    emitScroll(System.currentTimeMillis());
                });
                bitmap.recycle(); // Done with bitmap
            });
    }

    private void parseOcrResult(Text result) {
        currentUsername = "";
        currentCaption = "";
        List<Text.TextBlock> blocks = result.getTextBlocks();
        if (blocks.isEmpty()) return;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenH = dm.heightPixels;
        int screenW = dm.widthPixels;

        StringBuilder captionBuilder = new StringBuilder();
        for (Text.TextBlock block : blocks) {
            Rect rect = block.getBoundingBox();
            if (rect == null) continue;
            String text = block.getText().trim();
            if (rect.top > screenH * 0.5 && rect.left < screenW * 0.8) {
                if (currentUsername.isEmpty() && (!text.contains(" ") || text.startsWith("@"))) {
                    currentUsername = text.replace("@", "").split(" ")[0];
                    continue;
                }
                if (text.length() > 3 && !isUiLabel(text)) {
                    captionBuilder.append(text).append(" ");
                }
            }
        }
        currentCaption = captionBuilder.toString().trim();
        logToFile("OCR_USER", "ocr", currentUsername);
        logToFile("OCR_DESC", "ocr", currentCaption);
    }

    private String saveThumbnail(Bitmap bitmap) {
        File dir = new File(getExternalFilesDir(null), "thumbnails");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "reel_" + System.currentTimeMillis() + ".webp");
        try (FileOutputStream out = new FileOutputStream(file)) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 4, bitmap.getHeight() / 4, true);
            scaled.compress(Bitmap.CompressFormat.WEBP, 70, out);
            scaled.recycle();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Thumbnail failed", e);
            return "";
        }
    }

    private void emitScroll(long ts) {
        try {
            JSONObject d = new JSONObject();
            d.put("reelNumber", reelCounter);
            d.put("reelId", currentReelId);
            d.put("username", currentUsername);
            d.put("caption", currentCaption);
            d.put("likeCount", currentLikeCount); // Added Like Count to RN Payload
            d.put("timestamp", ts);
            emit("onReelScrolled", d.toString());
        } catch (JSONException e) {}
    }

    // ── SCRAPING ─────────────────────────────────────────────────────────────

    private void scrape(AccessibilityNodeInfo root) {
        currentUsername = "";
        currentCaption = "";
        currentLikeCount = "";

        // Strategy 1: Like count (always fetchable if node exists)
        String[] lIds = {
            IG + ":id/like_count", IG + ":id/row_feed_textview_likes",
            IG + ":id/clips_viewer_like_count"
        };
        for (String id : lIds) {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
            if (ns != null && !ns.isEmpty()) {
                for (AccessibilityNodeInfo n : ns) {
                    String t = getText(n).trim();
                    if (!t.isEmpty()) {
                        currentLikeCount = t;
                        logToFile("FOUND_LIKES", id, currentLikeCount);
                        break;
                    }
                }
                recycle(ns);
                if (!currentLikeCount.isEmpty()) break;
            }
        }
        
        // Strategy 2: Content Descriptions (legacy fallback)
        extractFromContentDescriptions(root, 0);
    }

    /**
     * Walk the node tree and extract username + caption from content descriptions.
     * Instagram Reels uses patterns like:
     *   "username said The actual caption text"
     *   "username"  (on the username label)
     *   "3,898 follow username on Instagram" (on the follow button)
     */
    private void extractFromContentDescriptions(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 12) return;
        // Stop early if we found both
        if (!currentUsername.isEmpty() && !currentCaption.isEmpty()) return;

        CharSequence descCs = node.getContentDescription();
        if (descCs != null) {
            String desc = descCs.toString().trim();

            // Pattern 1: "username said <caption>"
            if (currentCaption.isEmpty() && desc.contains(" said ")) {
                int idx = desc.indexOf(" said ");
                String user = desc.substring(0, idx).trim();
                String caption = desc.substring(idx + 6).trim();
                if (!caption.isEmpty() && caption.length() > 3) {
                    currentCaption = caption;
                    logToFile("FOUND_DESC_SAID", "contentDescription", currentCaption);
                    if (currentUsername.isEmpty() && !user.isEmpty() && !user.contains(" ")) {
                        currentUsername = user.replace("@", "");
                        logToFile("FOUND_USER_SAID", "contentDescription", currentUsername);
                    }
                }
            }

            // Pattern 2: "N follow username on Instagram" (follow button)
            if (currentUsername.isEmpty() && desc.contains(" follow ") && desc.contains(" on Instagram")) {
                int fIdx = desc.indexOf(" follow ");
                int oIdx = desc.indexOf(" on Instagram");
                if (fIdx < oIdx) {
                    String user = desc.substring(fIdx + 8, oIdx).trim();
                    if (!user.isEmpty() && !user.contains(" ")) {
                        currentUsername = user.replace("@", "");
                        logToFile("FOUND_USER_FOLLOW", "contentDescription", currentUsername);
                    }
                }
            }

            // Pattern 3: "Liked by X and Y others" — this is NOT a caption, skip
            // Pattern 4: Simple username (single word, no spaces, near bottom)
            if (currentUsername.isEmpty() && !desc.contains(" ") && desc.length() > 1 && desc.length() <= 30) {
                Rect b = new Rect(); node.getBoundsInScreen(b);
                int sh = getResources().getDisplayMetrics().heightPixels;
                if (b.top > sh * 0.5 && b.bottom < sh * 0.95) {
                    currentUsername = desc.replace("@", "");
                    logToFile("FOUND_USER_DESC", "contentDescription", currentUsername);
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractFromContentDescriptions(child, depth + 1);
                child.recycle();
            }
        }
    }

    private boolean isUiLabel(String text) {
        String lower = text.toLowerCase().trim();
        String[] uiStrings = {
            "turn on sound", "tap to unmute", "follow", "following",
            "share", "like", "comment", "send", "more", "audio",
            "original audio", "reel", "sponsored", "remix", "use audio", 
            "use template", "view translation", "see translation", 
            "translate", "add comment...", "music", "paid partnership",
            "hide translation", "save"
        };
        for (String ui : uiStrings) {
            if (lower.equals(ui)) return true;
        }
        return lower.length() < 3;
    }

    private String findUsernameHeuristic(AccessibilityNodeInfo node) {
        if (node == null) return "";
        String t = getText(node);
        if (!t.isEmpty() && !t.contains(" ") && t.length() <= 30 && !t.contains("\n")) {
            Rect b = new Rect(); node.getBoundsInScreen(b);
            int sh = getResources().getDisplayMetrics().heightPixels;
            if (b.top > sh * 0.5 && b.bottom < sh * 0.95) {
                if (node.isClickable() || (node.getParent() != null && node.getParent().isClickable())) {
                    return t.replace("@", "");
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) { String r = findUsernameHeuristic(c); c.recycle(); if (!r.isEmpty()) return r; }
        }
        return "";
    }

    private String findCaptionHeuristic(AccessibilityNodeInfo node) {
        if (node == null) return "";
        String t = getText(node);
        if (t.length() > 8 && t.contains(" ") && !isUiLabel(t)) {
            Rect b = new Rect(); node.getBoundsInScreen(b);
            int sh = getResources().getDisplayMetrics().heightPixels;
            if (b.top > sh * 0.5 && b.bottom < sh * 0.95) return t;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) { String r = findCaptionHeuristic(c); c.recycle(); if (!r.isEmpty()) return r; }
        }
        return "";
    }
    
    private String findLikeCountHeuristic(AccessibilityNodeInfo node) {
        if (node == null) return "";
        String t = getText(node).toLowerCase().trim();
        // Look for things like "1.2M likes", "456K likes", "12,000 likes"
        if (t.endsWith("likes") && t.length() < 20) {
            return t;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) { String r = findLikeCountHeuristic(c); c.recycle(); if (!r.isEmpty()) return r; }
        }
        return "";
    }

    private String getText(AccessibilityNodeInfo n) {
        if (n == null) return "";
        CharSequence t = n.getText();
        return t != null ? t.toString() : "";
    }

    private String deepText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        collectText(node, sb, 0);
        return sb.toString().trim();
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 6) return;
        
        // I removed the restrictive bounds check here. Captions are often long
        // and go below the screen. Removing it fixes the caption cutting off.
        CharSequence t = node.getText();
        if (t != null && t.length() > 0 && !isUiLabel(t.toString())) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(t);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) { collectText(c, sb, depth + 1); c.recycle(); }
        }
    }

    // ── PROGRESS ─────────────────────────────────────────────────────────────

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
            return findProgressRecursive(root);
        } finally { root.recycle(); }
    }

    private double findProgressRecursive(AccessibilityNodeInfo n) {
        if (n == null) return 0;
        CharSequence cls = n.getClassName();
        if (cls != null && (cls.toString().contains("ProgressBar") || cls.toString().contains("SeekBar"))) {
            AccessibilityNodeInfo.RangeInfo ri = n.getRangeInfo();
            if (ri != null && ri.getMax() > 0) return (ri.getCurrent() / ri.getMax()) * 100;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) { double r = findProgressRecursive(c); c.recycle(); if (r > 0) return r; }
        }
        return 0;
    }

    // ── ENGAGEMENT ───────────────────────────────────────────────────────────

    private void onClick(AccessibilityEvent event) {
        AccessibilityNodeInfo src = event.getSource();
        if (src == null) return;
        try {
            String desc = src.getContentDescription() != null ? src.getContentDescription().toString().toLowerCase() : "";
            String id = src.getViewIdResourceName() != null ? src.getViewIdResourceName().toLowerCase() : "";
            boolean like = desc.contains("like") && !desc.contains("liked by");
            boolean comment = desc.contains("comment") || id.contains("comment");
            boolean share = desc.contains("share") || desc.contains("send") || id.contains("share");
            if ((like || comment || share) && currentReelId > 0) {
                dbHelper.updateReelEngagement(currentReelId, like, comment, share);
                String action = like ? "like" : comment ? "comment_tap" : "share";
                try { JSONObject d = new JSONObject(); d.put("reelId", currentReelId); d.put("username", currentUsername); d.put("action", action); d.put("timestamp", System.currentTimeMillis()); emit("onEngagement", d.toString()); } catch (JSONException e) {}
            }
        } finally { src.recycle(); }
    }

    // ── Database ─────────────────────────────────────────────────────────────────

    private void finalizeReel() {
        if (currentReelId > 0 && toggleWatchTime) {
            long wt = System.currentTimeMillis() - lastScrollTimestamp;
            dbHelper.updateReelWatchData(currentReelId, wt, getCompletion());
        }
    }

    private void recycle(List<AccessibilityNodeInfo> ns) {
        if (ns != null) for (AccessibilityNodeInfo n : ns) if (n != null) n.recycle();
    }

    private void emit(String name, String json) {
        ReelsTrackerModule m = ReelsTrackerModule.getInstance();
        if (m != null) m.sendEvent(name, json);
    }

    // ── TOGGLES ──────────────────────────────────────────────────────────────

    private void loadToggles() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        toggleMetadata = p.getBoolean("toggle_metadata", true);
        toggleWatchTime = p.getBoolean("toggle_watchtime", true);
        toggleCompletion = p.getBoolean("toggle_completion", true);
        toggleEngagement = p.getBoolean("toggle_engagement", true);
        toggleOcr = p.getBoolean("toggle_ocr", true);
    }

    public void saveToggle(String key, boolean val) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(key, val).apply();
        switch (key) {
            case "toggle_metadata": toggleMetadata = val; break;
            case "toggle_watchtime": toggleWatchTime = val; break;
            case "toggle_completion": toggleCompletion = val; if (val && isInReelsView) startPolling(); else stopPolling(); break;
            case "toggle_engagement": toggleEngagement = val; break;
            case "toggle_ocr": toggleOcr = val; break;
        }
    }
    public boolean getToggle(String key) {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, true);
    }
    // 👆
    public String getServiceStatus() {
        try {
            JSONObject s = new JSONObject();
            s.put("isRunning", true); s.put("isInReelsView", isInReelsView);
            s.put("currentReelId", currentReelId); s.put("reelCounter", reelCounter);
            s.put("currentUsername", currentUsername);
            return s.toString();
        } catch (JSONException e) { return "{}"; }
    }
}