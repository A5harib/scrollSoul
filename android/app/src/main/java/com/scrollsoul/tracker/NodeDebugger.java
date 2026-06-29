package com.scrollsoul.tracker;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;

/**
 * Dumps the Instagram accessibility node tree to logcat for debugging.
 * Filter with: adb logcat -s ScrollMind.Debug
 */
public class NodeDebugger {
    private static final String TAG = "ScrollMind.Debug";
    private static final int MAX_DEPTH = 15;

    public static void dumpTree(AccessibilityNodeInfo root, String label) {
        Log.i(TAG, "═══ NODE TREE DUMP: " + label + " ═══");
        if (root == null) {
            Log.w(TAG, "Root is null!");
            return;
        }
        dumpNode(root, 0);
        Log.i(TAG, "═══ END DUMP ═══");
    }

    private static void dumpNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_DEPTH) return;

        String indent = new String(new char[depth]).replace("\0", "  ");
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        String cls = node.getClassName() != null ? node.getClassName().toString() : "null";
        String id = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

        // Truncate long text
        if (text.length() > 80) text = text.substring(0, 80) + "...";
        if (desc.length() > 80) desc = desc.substring(0, 80) + "...";

        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("[").append(cls.substring(cls.lastIndexOf('.') + 1)).append("]");
        if (!id.isEmpty()) sb.append(" id=").append(id);
        if (!text.isEmpty()) sb.append(" text=\"").append(text).append("\"");
        if (!desc.isEmpty()) sb.append(" desc=\"").append(desc).append("\"");
        sb.append(" bounds=").append(bounds.toShortString());
        if (node.isClickable()) sb.append(" CLICK");
        if (node.isScrollable()) sb.append(" SCROLL");

        Log.d(TAG, sb.toString());

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNode(child, depth + 1);
                child.recycle();
            }
        }
    }

    /** Log a quick summary of a single node */
    public static void logNode(String prefix, AccessibilityNodeInfo node) {
        if (node == null) {
            Log.d(TAG, prefix + ": null");
            return;
        }
        String id = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "no-id";
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        Log.d(TAG, prefix + ": id=" + id + " text=\"" + text + "\" desc=\"" + desc + "\" bounds=" + b.toShortString());
    }
}
