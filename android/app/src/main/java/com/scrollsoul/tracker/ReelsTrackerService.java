package com.scrollsoul.tracker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ReelsTrackerService extends AccessibilityService {

    private static final String TAG = "ScrollSoul.Service";
    private static final String IG = "com.instagram.android";
    private static final String PREFS = "scrollsoul_prefs";
    private static final long SCROLL_DEBOUNCE_MS = 800;

    private boolean isInReelsView = false;
    private long lastScrollTimestamp = 0;
    
    private boolean currentReelLiked = false;
    private boolean currentReelCommented = false;
    private boolean currentReelShared = false;
    private double maxCompletion = 0.0;
    
    private String currentUsername = "";
    private String currentCaption = "";
    private String currentLikeCount = "";

    private boolean toggleMetadata = true;
    private boolean toggleWatchTime = true;
    private boolean toggleCompletion = true;
    private boolean toggleEngagement = true;
    private boolean toggleOcr = true;

    private Handler handler;
    private Runnable progressPoller;
    private static ReelsTrackerService sInstance;

    private WindowManager windowManager;
    private View overlayView;
    private TextView counterText;

    public static ReelsTrackerService getInstance() { return sInstance; }

    @Override public void onCreate() {
        super.onCreate();
        sInstance = this;
        handler = new Handler(Looper.getMainLooper());
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
    }

    @Override public void onInterrupt() { stopPolling(); }

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

    public void updateOverlayText(String text) {
        handler.post(() -> {
            try {
                if (counterText != null) {
                    counterText.setText(text);
                    counterText.setVisibility(isInReelsView ? View.VISIBLE : View.GONE);
                }
            } catch (Exception e) {}
        });
    }

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
                case AccessibilityEvent.TYPE_VIEW_CLICKED:
                    if (isInReelsView && toggleEngagement) {
                        AccessibilityNodeInfo src = event.getSource();
                        if (src != null) {
                            if (checkEngagementState(src, "like", "unlike")) currentReelLiked = true;
                            if (checkEngagementState(src, "comment", "add a comment")) currentReelCommented = true;
                            if (checkEngagementState(src, "share", "send")) currentReelShared = true;
                            src.recycle();
                        }
                    }
                    break;
            }
        } catch (Exception e) {}
    }

    private void checkReelsState() {
        boolean inReels = isReelsPlayer();
        if (inReels && !isInReelsView) enterReels();
        else if (!inReels && isInReelsView) exitReels();
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
        resetState();
        lastScrollTimestamp = System.currentTimeMillis();
        if (toggleCompletion) startPolling();
        emit("onEnterReels", "{}");
    }

    private void exitReels() {
        isInReelsView = false;
        stopPolling();
        emit("onExitReels", "{}");
    }

    private void resetState() {
        currentUsername = "";
        currentCaption = ""; 
        currentLikeCount = "";
        currentReelLiked = false;
        currentReelCommented = false;
        currentReelShared = false;
        maxCompletion = 0.0;
    }

    private void onScroll(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastScrollTimestamp < SCROLL_DEBOUNCE_MS) return;

        resetState();
        lastScrollTimestamp = now;

        if (toggleMetadata) {
            handler.postDelayed(() -> {
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null) { try { scrape(r); } finally { r.recycle(); } }
                emitScroll(now);
            }, 600);
        } else {
            emitScroll(now);
        }
    }

    private void startPolling() {
        stopPolling();
        progressPoller = new Runnable() {
            @Override public void run() {
                if (isInReelsView) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null) {
                        try {
                            double c = getCompletion(root);
                            if (c > maxCompletion) maxCompletion = c;
                            
                            if (!currentReelLiked && checkEngagementState(root, "unlike")) currentReelLiked = true;
                            if (!currentReelCommented && checkEngagementState(root, "add a comment", "commenting")) currentReelCommented = true;
                            if (!currentReelShared && checkEngagementState(root, "share", "send")) currentReelShared = true;
                            
                            JSONObject d = new JSONObject(); 
                            d.put("completionPercent", maxCompletion); 
                            d.put("liked", currentReelLiked);
                            d.put("commented", currentReelCommented);
                            d.put("shared", currentReelShared);
                            emit("onProgressUpdate", d.toString()); 
                        } catch (Exception e) {}
                        finally { root.recycle(); }
                    }
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.postDelayed(progressPoller, 500);
    }

    private void stopPolling() {
        if (progressPoller != null) { handler.removeCallbacks(progressPoller); progressPoller = null; }
    }

    private boolean checkEngagementState(AccessibilityNodeInfo node, String... keywords) {
        if (node == null) return false;
        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();
        
        for (String kw : keywords) {
            if (desc != null && desc.toString().toLowerCase().contains(kw)) return true;
            if (text != null && text.toString().toLowerCase().contains(kw)) return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { 
                boolean found = checkEngagementState(child, keywords); 
                child.recycle(); 
                if (found) return true; 
            }
        }
        return false;
    }

    private double getCompletion(AccessibilityNodeInfo root) {
        if (root == null) return 0;
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
    }

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

        extractFromContentDescriptions(root, 0);
    }

    private void extractFromContentDescriptions(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 12) return;
        if (!currentUsername.isEmpty() && !currentCaption.isEmpty()) return;

        CharSequence descCs = node.getContentDescription();
        if (descCs != null) {
            String desc = descCs.toString().trim();
            if (currentCaption.isEmpty() && desc.contains(" said ")) {
                int idx = desc.indexOf(" said ");
                String user = desc.substring(0, idx).trim();
                String caption = desc.substring(idx + 6).trim();
                if (!caption.isEmpty() && caption.length() > 3) {
                    currentCaption = caption;
                    if (currentUsername.isEmpty() && !user.isEmpty() && !user.contains(" ")) {
                        currentUsername = user.replace("@", "");
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { extractFromContentDescriptions(child, depth + 1); child.recycle(); }
        }
    }

    private boolean isUiLabel(String text) {
        String lower = text.toLowerCase().trim();
        String[] uiStrings = {
            "turn on sound", "tap to unmute", "follow", "following",
            "share", "like", "comment", "send", "more", "audio",
            "original audio", "reel", "sponsored", "remix", "use audio",
            "view translation", "see translation", "translate", "save", "likes"
        };
        for (String ui : uiStrings) { if (lower.equals(ui)) return true; }
        return lower.length() < 3;
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
        if (t != null && t.length() > 0 && !isUiLabel(t.toString())) { sb.append(t).append(" "); }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) { collectText(c, sb, depth + 1); c.recycle(); }
        }
    }

    private void emitScroll(long ts) {
        try {
            JSONObject d = new JSONObject();
            d.put("username", currentUsername);
            d.put("caption", currentCaption);
            d.put("likeCount", currentLikeCount); 
            d.put("timestamp", ts);
            emit("onReelScrolled", d.toString());
        } catch (JSONException e) {}
    }

    private void recycle(List<AccessibilityNodeInfo> ns) { if (ns != null) for (AccessibilityNodeInfo n : ns) if (n != null) n.recycle(); }
    private void emit(String name, String json) { ReelsTrackerModule m = ReelsTrackerModule.getInstance(); if (m != null) m.sendEvent(name, json); }
    private void loadToggles() { SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE); toggleMetadata = p.getBoolean("toggle_metadata", true); toggleWatchTime = p.getBoolean("toggle_watchtime", true); toggleCompletion = p.getBoolean("toggle_completion", true); toggleEngagement = p.getBoolean("toggle_engagement", true); toggleOcr = p.getBoolean("toggle_ocr", true); }
    public void saveToggle(String key, boolean val) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(key, val).apply(); switch (key) { case "toggle_metadata": toggleMetadata = val; break; case "toggle_watchtime": toggleWatchTime = val; break; case "toggle_completion": toggleCompletion = val; if (val && isInReelsView) startPolling(); else stopPolling(); break; case "toggle_engagement": toggleEngagement = val; break; case "toggle_ocr": toggleOcr = val; break; } }
    public boolean getToggle(String key) { return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, true); }
    public String getServiceStatus() { 
        try { 
            JSONObject s = new JSONObject(); 
            s.put("isRunning", true); 
            s.put("isInReelsView", isInReelsView); 
            return s.toString(); 
        } catch (JSONException e) { 
            return "{}"; 
        } 
    }
}