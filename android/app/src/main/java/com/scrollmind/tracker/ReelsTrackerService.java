package com.scrollmind.tracker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

public class ReelsTrackerService extends AccessibilityService {

    private static final String TAG = "ScrollMind.Service";
    private static final String IG = "com.instagram.android";
    private static final String PREFS = "scrollmind_prefs";
    private static final long SCROLL_DEBOUNCE_MS = 800;

    // State
    private boolean isInReelsView = false;
    private long currentReelId = -1;
    private long lastReelId = -1; // Added to prevent duplicate bug
    private long lastScrollTimestamp = 0;
    private int reelCounter = 0;
    private String currentUsername = "";
    private String currentCaption = "";
    private String lastUsername = "";
    private String lastCaption = "";
    private boolean debugDumpDone = false; // dump tree once per session

    // Toggles
    private boolean toggleMetadata = true, toggleWatchTime = true;
    private boolean toggleCompletion = true, toggleEngagement = true, toggleOcr = true;

    private TrackerDatabaseHelper dbHelper;
    private Handler handler;
    private Runnable progressPoller;
    private static ReelsTrackerService sInstance;

    public static ReelsTrackerService getInstance() { return sInstance; }

    @Override public void onCreate() {
        super.onCreate();
        sInstance = this;
        dbHelper = TrackerDatabaseHelper.getInstance(this);
        handler = new Handler(Looper.getMainLooper());
        loadToggles();
        Log.i(TAG, "Service created");
    }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Service connected");
        emit("onServiceConnected", "{}");
    }

    @Override public void onDestroy() {
        super.onDestroy(); sInstance = null; stopPolling();
    }

    @Override public void onInterrupt() {
        Log.w(TAG, "Service interrupted"); stopPolling();
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
                // Re-check if we left reels
                if (isInReelsView && !isReelsPlayer()) exitReels();
                break;
        }
    }

    // ── DETECTION ────────────────────────────────────────────────────────────

    private void checkReelsState() {
        boolean inReels = isReelsPlayer();
        Log.d(TAG, "checkReelsState: inReels=" + inReels + " wasInReels=" + isInReelsView);
        if (inReels && !isInReelsView) enterReels();
        else if (!inReels && isInReelsView) exitReels();
    }

    private boolean isReelsPlayer() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        try {
            boolean hasReelContainer = false;
            int screenH = getResources().getDisplayMetrics().heightPixels;

            // 1. Look for known reel container IDs (IG changes these often)
            String[] containerIds = {
                IG + ":id/clips_viewer_view_pager",
                IG + ":id/reel_viewer_root",
                IG + ":id/fragment_clips_viewer_root",
                IG + ":id/layout_clips_viewer_root",
                IG + ":id/clips_video_container"
            };
            for (String id : containerIds) {
                List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
                if (ns != null && !ns.isEmpty()) {
                    for (AccessibilityNodeInfo n : ns) {
                        Rect b = new Rect();
                        n.getBoundsInScreen(b);
                        // Make sure it takes up most of the screen
                        if (b.height() > screenH * 0.5) {
                            hasReelContainer = true;
                            break;
                        }
                    }
                    recycle(ns);
                    if (hasReelContainer) break;
                }
            }

            // 2. Fallback: Check for Reels specific UI (caption, audio button, etc)
            if (!hasReelContainer) {
                String[] fallbackIds = {
                    IG + ":id/clips_caption", 
                    IG + ":id/reel_viewer_caption",
                    IG + ":id/reel_music_attribution_subtitle"
                };
                for (String id : fallbackIds) {
                    List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
                    if (ns != null && !ns.isEmpty()) {
                        hasReelContainer = true;
                        recycle(ns);
                        break;
                    }
                }
            }

            if (!hasReelContainer) return false;

            // Determine if we are on Reels tab vs Home tab
            String activeTab = getActiveTab(root);
            Log.d(TAG, "Reel container found. Active tab: " + activeTab);

            if (activeTab.equals("Reels") || activeTab.equals("none")) {
                // "Reels" tab selected, or no nav bar (dedicated viewer from DM/explore)
                return true;
            } else {
                return false;
            }

        } finally {
            root.recycle();
        }
    }

    private String getActiveTab(AccessibilityNodeInfo root) {
        String[] tabDescriptions = {"Home", "Search", "Reels", "Shop", "Profile", "Create", "Notifications"};

        for (String tabDesc : tabDescriptions) {
            AccessibilityNodeInfo found = findNodeByContentDesc(root, tabDesc, 0);
            if (found != null) {
                if (found.isSelected()) {
                    found.recycle();
                    return tabDesc;
                }
                found.recycle();
            }
        }

        boolean hasAnyTab = false;
        for (String tabDesc : tabDescriptions) {
            AccessibilityNodeInfo found = findNodeByContentDesc(root, tabDesc, 0);
            if (found != null) {
                hasAnyTab = true;
                found.recycle();
                break;
            }
        }

        return hasAnyTab ? "unknown" : "none";
    }

    private AccessibilityNodeInfo findNodeByContentDesc(AccessibilityNodeInfo node, String target, int depth) {
        if (node == null || depth > 4) return null;

        // Custom bounds visibility check (Fixes the Homepage vs Reels page bug safely)
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        if (b.height() <= 0 || b.top >= screenH || b.bottom <= 0) {
            return null; 
        }

        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().trim();
            if (d.equals(target) || d.startsWith(target)) {
                return AccessibilityNodeInfo.obtain(node);
            }
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
        debugDumpDone = false;
        Log.i(TAG, ">>> ENTERED REELS");
        if (toggleCompletion) startPolling();
        emit("onEnterReels", "{}");
    }

    private void exitReels() {
        isInReelsView = false;
        finalizeReel();
        stopPolling();
        Log.i(TAG, "<<< EXITED REELS (watched " + reelCounter + ")");
        try {
            JSONObject d = new JSONObject();
            d.put("reelsWatched", reelCounter);
            emit("onExitReels", d.toString());
        } catch (JSONException e) {}
    }

    // ── SCROLL (debounced) ───────────────────────────────────────────────────

    private void onScroll(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastScrollTimestamp < SCROLL_DEBOUNCE_MS) {
            return;
        }

        if (toggleWatchTime && currentReelId > 0) {
            long wt = now - lastScrollTimestamp;
            double comp = getCompletion();
            dbHelper.updateReelWatchData(currentReelId, wt, comp);
        }

        currentReelId = -1;
        currentUsername = "";
        currentCaption = ""; 

        lastScrollTimestamp = now;
        reelCounter++;

        if (!debugDumpDone) {
            debugDumpDone = true;
            AccessibilityNodeInfo dumpRoot = getRootInActiveWindow();
            if (dumpRoot != null) {
                NodeDebugger.dumpTree(dumpRoot, "FIRST_REEL_SCROLL");
                dumpRoot.recycle();
            }
        }

        if (toggleMetadata) {
            handler.postDelayed(() -> {
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null) {
                    try {
                        scrape(r);
                    } finally { r.recycle(); }
                }
                emitScroll(now);
            }, 600);
        } else {
            currentReelId = dbHelper.insertReel("", "");
            emitScroll(now);
        }
    }

    private void emitScroll(long ts) {
        try {
            JSONObject d = new JSONObject();
            d.put("reelNumber", reelCounter);
            d.put("reelId", currentReelId);
            d.put("username", currentUsername);
            d.put("caption", currentCaption);
            d.put("timestamp", ts);
            emit("onReelScrolled", d.toString());
        } catch (JSONException e) {}
    }

    // ── SCRAPING ─────────────────────────────────────────────────────────────

    private void scrape(AccessibilityNodeInfo root) {
        currentUsername = "";
        currentCaption = "";

        String[] uIds = {
            IG + ":id/reel_viewer_username", IG + ":id/clips_username",
            IG + ":id/username_text_view", IG + ":id/clips_viewer_attribution_line",
            IG + ":id/reel_music_attribution_username", IG + ":id/reel_viewer_title"
        };
        for (String id : uIds) {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
            if (ns != null && !ns.isEmpty()) {
                for (AccessibilityNodeInfo n : ns) {
                    String t = getText(n);
                    if (!t.isEmpty() && !t.contains(" ")) {
                        currentUsername = t.replace("@", "").trim();
                        break;
                    }
                }
                recycle(ns);
                if (!currentUsername.isEmpty()) break;
            }
        }
        if (currentUsername.isEmpty()) currentUsername = findUsernameHeuristic(root);

        String[] cIds = {
            IG + ":id/clips_caption", IG + ":id/reel_viewer_caption",
            IG + ":id/clips_caption_text", IG + ":id/caption_text",
            IG + ":id/clips_viewer_video_caption", IG + ":id/video_caption",
            IG + ":id/contextual_feed_caption_container"
        };
        for (String id : cIds) {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(id);
            if (ns != null && !ns.isEmpty()) {
                for (AccessibilityNodeInfo n : ns) {
                    String t = deepText(n);
                    if (!t.isEmpty() && !isUiLabel(t)) {
                        currentCaption = t.trim();
                        break;
                    }
                }
                recycle(ns);
                if (!currentCaption.isEmpty()) break;
            }
        }
        if (currentCaption.isEmpty()) currentCaption = findCaptionHeuristic(root);

        // Fixes the duplication bug: Restore ID if the UI hasn't caught up with the scroll yet
        if (currentUsername.equals(lastUsername) && currentCaption.equals(lastCaption) && !currentUsername.isEmpty()) {
            currentReelId = lastReelId; 
            return;
        }

        lastUsername = currentUsername;
        lastCaption = currentCaption;
        currentReelId = dbHelper.insertReel(currentUsername, currentCaption);
        lastReelId = currentReelId;
    }

    private boolean isUiLabel(String text) {
        String lower = text.toLowerCase().trim();
        // Added ignore cases so description isn't messed up
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
        
        // Bounds check to ensure we aren't pulling hidden text/comments
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        if (b.height() <= 0 || b.top >= screenH || b.bottom <= 0) {
            return; 
        }

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

    public String getServiceStatus() {
        try {
            JSONObject s = new JSONObject();
            s.put("isRunning", true); s.put("isInReelsView", isInReelsView);
            s.put("currentReelId", currentReelId); s.put("reelCounter", reelCounter);
            s.put("currentUsername", currentUsername);
            s.put("toggleMetadata", toggleMetadata); s.put("toggleWatchTime", toggleWatchTime);
            s.put("toggleCompletion", toggleCompletion); s.put("toggleEngagement", toggleEngagement);
            s.put("toggleOcr", toggleOcr);
            return s.toString();
        } catch (JSONException e) { return "{}"; }
    }
}