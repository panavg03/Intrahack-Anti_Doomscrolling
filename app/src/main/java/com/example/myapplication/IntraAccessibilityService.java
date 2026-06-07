package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

@SuppressLint("AccessibilityPolicy")
public class IntraAccessibilityService extends AccessibilityService {
    private static final String TAG = "INTRAHACK_AS";
    
    public static final String ACTION_SCREEN_TYPE = "com.example.myapplication.SCREEN_TYPE";
    public static final String EXTRA_SCREEN_TYPE = "screen_type";
    public static final String EXTRA_IS_SCROLLING = "is_scrolling";
    
    public enum ScreenType {
        INSTAGRAM_REELS,
        INSTAGRAM_DMS,
        INSTAGRAM_PROFILE,
        OTHER
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null || !event.getPackageName().equals("com.instagram.android")) {
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        ScreenType currentType = detectInstagramScreen(rootNode);
        
        // Detect if the user is currently scrolling
        boolean isScrolling = (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED);
        
        if (isScrolling) {
            LogManager.log(this, "Accessibility: User scrolling on " + currentType.name());
        }

        // Broadcast the screen type and scrolling status to AppMonitorService
        Intent intent = new Intent(ACTION_SCREEN_TYPE);
        intent.putExtra(EXTRA_SCREEN_TYPE, currentType.name());
        intent.putExtra(EXTRA_IS_SCROLLING, isScrolling);
        sendBroadcast(intent);
        
        rootNode.recycle();
    }

    private ScreenType detectInstagramScreen(AccessibilityNodeInfo node) {
        // Reels detection
        if (findNodeByTextOrId(node, "reels_video_container") || 
            findNodeByTextOrId(node, "Reels") ||
            findNodeByTextOrId(node, "reels_viewer_pager")) {
            return ScreenType.INSTAGRAM_REELS;
        }
        
        // DM detection
        if (findNodeByTextOrId(node, "Direct") || 
            findNodeByTextOrId(node, "action_bar_container_external_user") ||
            findNodeByTextOrId(node, "thread_title")) {
            return ScreenType.INSTAGRAM_DMS;
        }

        // Profile detection
        if (findNodeByTextOrId(node, "profile_tab") || 
            findNodeByTextOrId(node, "self_profile_header_container")) {
            return ScreenType.INSTAGRAM_PROFILE;
        }

        return ScreenType.OTHER;
    }

    private boolean findNodeByTextOrId(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;
        
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByViewId("com.instagram.android:id/" + target);
        if (nodes != null && !nodes.isEmpty()) return true;
        
        nodes = node.findAccessibilityNodeInfosByText(target);
        if (nodes != null && !nodes.isEmpty()) return true;

        return false;
    }

    @Override
    public void onInterrupt() {
    }
}
