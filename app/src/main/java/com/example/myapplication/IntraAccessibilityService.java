package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class IntraAccessibilityService extends AccessibilityService {
    private static final String TAG = "INTRAHACK_AS";
    
    public static final String ACTION_SCREEN_TYPE = "com.example.myapplication.SCREEN_TYPE";
    public static final String EXTRA_SCREEN_TYPE = "screen_type";
    
    public enum ScreenType {
        INSTAGRAM_REELS,
        INSTAGRAM_DMS,
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
        
        // Broadcast the screen type to AppMonitorService
        Intent intent = new Intent(ACTION_SCREEN_TYPE);
        intent.putExtra(EXTRA_SCREEN_TYPE, currentType.name());
        sendBroadcast(intent);
        
        rootNode.recycle();
    }

    private ScreenType detectInstagramScreen(AccessibilityNodeInfo node) {
        // BrainRot style detection:
        // Reels usually have a container for video or specific interaction buttons
        // DMs usually have a list of messages or a chat input
        
        // This is a heuristic approach. Instagram changes IDs often, 
        // but we can look for specific text or content descriptions.
        
        if (findNodeByTextOrId(node, "reels_video_container") || 
            findNodeByTextOrId(node, "Reels")) {
            return ScreenType.INSTAGRAM_REELS;
        }
        
        if (findNodeByTextOrId(node, "Direct") || 
            findNodeByTextOrId(node, "action_bar_container_external_user")) {
            return ScreenType.INSTAGRAM_DMS;
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
