package com.app.pldscript;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 控件树查看器 - 在屏幕上标注所有控件
 */
public class ViewTreeOverlay {
    private static WindowManager windowManager;
    private static OverlayView overlayView;
    private static WindowManager.LayoutParams overlayParams;
    private static boolean isEnabled = false;
    private static final String PREFS_NAME = "ViewTreeOverlayPrefs";
    private static final String KEY_ENABLED = "overlay_enabled";
    
    // 菜单相关
    private static View nodeMenuView;
    private static WindowManager.LayoutParams nodeMenuParams;
    
    // 信息窗口相关
    private static View infoWindowView;
    private static View treeWindowView;

    /**
     * 初始化覆盖层（不自动显示，只在用户手动开启时显示）
     */
    public static void init(Context context) {
        if (windowManager == null) {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }
        // 不自动加载状态，覆盖层只在用户手动开启时显示
        // 这样可以避免拦截主界面的触摸事件
    }

    /**
     * 切换显示状态
     */
    public static void toggle(Context context) {
        if (isEnabled) {
            hide(context);
        } else {
            show(context);
        }
        // 保存状态
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, isEnabled).apply();
    }

    /**
     * 显示覆盖层
     */
    public static void show(Context context) {
        if (overlayView != null) return;
        isEnabled = true;

        overlayView = new OverlayView(context);
        
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // 获取屏幕实际尺寸
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        
        // 使用 FLAG_NOT_TOUCH_MODAL 允许接收触摸事件，但让事件可以穿透到其他窗口
        // 在 onInterceptTouchEvent 中会检查是否点击在悬浮窗/菜单上，如果是则返回 false 让事件穿透
        overlayParams = new WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 允许接收触摸事件，但事件可以穿透
                PixelFormat.TRANSLUCENT
        );
        // 确保覆盖层从屏幕左上角 (0,0) 开始
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = 0;
        overlayParams.y = 0;
        
        android.util.Log.d("ViewTreeOverlay", String.format(
            "创建覆盖层 - 屏幕尺寸: %dx%d, 位置: (%d,%d), 类型: %d",
            screenWidth, screenHeight, overlayParams.x, overlayParams.y, type));
        
        windowManager.addView(overlayView, overlayParams);
        overlayView.refresh();
        
        // 通知 FloatWindow 更新按钮文本
        FloatWindow.updateViewTreeButton();
        
        android.util.Log.d("ViewTreeOverlay", "覆盖层已创建，使用 FLAG_NOT_TOUCH_MODAL，可以点击控件方框");
    }

    /**
     * 隐藏覆盖层
     */
    public static void hide(Context context) {
        // 先隐藏所有窗口
        hideNodeMenu();
        hideInfoWindow();
        hideTreeWindow();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
            overlayParams = null;
        }
        isEnabled = false;
        // 通知 FloatWindow 更新按钮文本
        FloatWindow.updateViewTreeButton();
    }

    /**
     * 刷新覆盖层（重新获取控件树并重绘）
     */
    public static void refresh() {
        if (overlayView != null) {
            overlayView.refresh();
        }
    }

    /**
     * 检查是否已启用
     */
    public static boolean isEnabled() {
        return isEnabled;
    }

    /**
     * 覆盖层视图
     * 使用 FrameLayout 以便使用 onInterceptTouchEvent 来拦截事件
     */
    private static class OverlayView extends android.widget.FrameLayout {
        private List<Rect> nodeRects = new ArrayList<>();
        private List<AccessibilityNodeInfo> nodeInfos = new ArrayList<>(); // 存储对应的节点信息
        private Paint paint;
        private Paint textPaint;
        private Context context;
        private int screenWidth;
        private int screenHeight;
        private int statusBarHeight;

        public OverlayView(Context context) {
            super(context);
            this.context = context;
            // 设置背景透明
            setBackgroundColor(android.graphics.Color.TRANSPARENT);
            initScreenInfo();
            initPaint();
            
            // 使用 setOnTouchListener 来处理触摸事件
            // 这样可以更精确地控制事件传递
            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    // 使用 getRawX() 和 getRawY() 获取屏幕绝对坐标
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    
                    // 1. 最优先：检查悬浮窗气泡 - 如果点击在气泡上，不处理，让事件传递到 FloatWindow
                    if (isPointInFloatWindowBubble((int) rawX, (int) rawY)) {
                        android.util.Log.d("ViewTreeOverlay", String.format("点击在悬浮窗气泡上 (%.0f,%.0f)，不处理，让事件传递", rawX, rawY));
                        return false; // 返回 false，让事件传递到 FloatWindow
                    }
                    
                    // 2. 检查悬浮窗菜单 - 如果点击在菜单上，不处理，让事件传递
                    if (isPointInFloatWindowMenu((int) rawX, (int) rawY)) {
                        android.util.Log.d("ViewTreeOverlay", String.format("点击在悬浮窗菜单上 (%.0f,%.0f)，不处理，让事件传递", rawX, rawY));
                        return false; // 返回 false，让事件传递到 FloatWindow
                    }
                    
                    // 3. 检查信息窗口和控件查看菜单 - 如果点击在这些窗口上，不处理
                    if (isPointInInfoWindow((int) rawX, (int) rawY) || isPointInMenu((int) rawX, (int) rawY)) {
                        android.util.Log.d("ViewTreeOverlay", String.format("点击在信息窗口或菜单上 (%.0f,%.0f)，不处理", rawX, rawY));
                        return false; // 返回 false，让事件传递
                    }
                    
                    // 4. 其他所有地方都处理事件
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        // 使用相对坐标查找节点
                        float x = event.getX();
                        float y = event.getY();
                        android.util.Log.d("ViewTreeOverlay", String.format("点击在其他地方 (%.0f,%.0f)，查找节点并显示信息", x, y));
                        AccessibilityNodeInfo clickedNode = findNodeAtPosition((int) x, (int) y);
                        if (clickedNode != null) {
                            showNodeMenu(context, clickedNode, (int) x, (int) y);
                            clickedNode.recycle();
                            return true; // 消费事件
                        } else {
                            android.util.Log.d("ViewTreeOverlay", "未找到节点，但已尝试处理");
                            return true; // 即使没找到节点，也消费事件
                        }
                    }
                    
                    return false;
                }
            });
        }

        /**
         * 初始化屏幕信息
         */
        private void initScreenInfo() {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            
            // 获取状态栏高度
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
            } else {
                // 如果无法获取，使用默认值或通过反射获取
                statusBarHeight = getStatusBarHeightFallback();
            }
            
            android.util.Log.d("ViewTreeOverlay", "屏幕尺寸: " + screenWidth + "x" + screenHeight + ", 状态栏高度: " + statusBarHeight);
        }

        /**
         * 备用方法获取状态栏高度
         */
        private int getStatusBarHeightFallback() {
            try {
                Class<?> clazz = Class.forName("com.android.internal.R$dimen");
                Object object = clazz.newInstance();
                int height = Integer.parseInt(clazz.getField("status_bar_height").get(object).toString());
                return context.getResources().getDimensionPixelSize(height);
            } catch (Exception e) {
                // 如果都失败，返回一个估算值（通常状态栏高度在24-48dp之间）
                return dp(24);
            }
        }

        private void initPaint() {
            // 方框画笔
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.RED);

            // 文本画笔
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.YELLOW);
            textPaint.setTextSize(dp(10));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // 确保视图尺寸与屏幕尺寸一致
            setMeasuredDimension(screenWidth, screenHeight);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            if (nodeRects.isEmpty()) {
                return;
            }

            // 获取视图的实际尺寸和位置
            int viewWidth = getWidth();
            int viewHeight = getHeight();
            int viewLeft = getLeft();
            int viewTop = getTop();
            
            // 调试信息（仅在第一次绘制时输出）
            if (android.util.Log.isLoggable("ViewTreeOverlay", android.util.Log.DEBUG)) {
                android.util.Log.d("ViewTreeOverlay", String.format(
                    "绘制信息 - 视图尺寸: %dx%d, 视图位置: (%d,%d), 屏幕尺寸: %dx%d, 状态栏高度: %d",
                    viewWidth, viewHeight, viewLeft, viewTop, screenWidth, screenHeight, statusBarHeight));
            }
            
            // 绘制所有控件的方框
            for (Rect rect : nodeRects) {
                if (rect != null && !rect.isEmpty()) {
                    // getBoundsInScreen() 返回的坐标是屏幕绝对坐标
                    // 覆盖层视图应该从屏幕 (0,0) 开始，所以直接使用这些坐标
                    canvas.drawRect(rect, paint);
                    
                    // 在左上角绘制坐标信息（可选，避免过于拥挤）
                    // 只绘制较大的控件
                    if (rect.width() > dp(50) && rect.height() > dp(50)) {
                        String info = rect.left + "," + rect.top;
                        canvas.drawText(info, rect.left, rect.top - dp(2), textPaint);
                    }
                }
            }
        }

        // 触摸事件处理已移到 setOnTouchListener 中
        
        /**
         * 检查当前活动窗口是否是应用自己的窗口
         */
        private boolean isAppOwnWindow() {
            PLDScript service = PLDScript.getInstance();
            if (service == null) return false;
            
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) return false;
            
            try {
                CharSequence packageName = root.getPackageName();
                if (packageName != null) {
                    String currentPackage = packageName.toString();
                    String appPackage = context.getPackageName();
                    boolean isOwnWindow = currentPackage.equals(appPackage);
                    root.recycle();
                    return isOwnWindow;
                }
                root.recycle();
            } catch (Exception e) {
                if (root != null) {
                    root.recycle();
                }
            }
            return false;
        }
        
        /**
         * 检查点是否在控件方框范围内
         */
        public boolean isPointInNodeRect(int x, int y) {
            for (Rect rect : nodeRects) {
                if (rect != null && !rect.isEmpty()) {
                    Rect expandedRect = new Rect(rect);
                    int tolerance = dp(10);
                    expandedRect.inset(-tolerance, -tolerance);
                    if (expandedRect.contains(x, y)) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        /**
         * 检查点是否在悬浮窗气泡范围内
         * 增加较大的容差，确保不会误判
         */
        public boolean isPointInFloatWindowBubble(int x, int y) {
            int[] bubblePos = FloatWindow.getBubblePosition();
            if (bubblePos != null) {
                int bx = bubblePos[0];
                int by = bubblePos[1];
                int bw = bubblePos[2];
                int bh = bubblePos[3];
                // 增加较大的容差，确保不会误判
                int tolerance = dp(30);
                boolean inBubble = x >= bx - tolerance && x <= bx + bw + tolerance 
                        && y >= by - tolerance && y <= by + bh + tolerance;
                if (inBubble) {
                    android.util.Log.d("ViewTreeOverlay", String.format(
                        "点在悬浮窗气泡内: (%d,%d) 气泡区域: (%d,%d) %dx%d 容差: %d", 
                        x, y, bx, by, bw, bh, tolerance));
                }
                return inBubble;
            } else {
                android.util.Log.d("ViewTreeOverlay", String.format(
                    "悬浮窗气泡位置为null，点: (%d,%d)", x, y));
            }
            return false;
        }
        
        /**
         * 检查点是否在悬浮窗菜单范围内（FloatWindow的菜单，不是控件查看菜单）
         * 增加较大的容差，确保菜单点击不被拦截
         */
        public boolean isPointInFloatWindowMenu(int x, int y) {
            int[] menuPos = FloatWindow.getMenuPosition();
            if (menuPos != null) {
                int mx = menuPos[0];
                int my = menuPos[1];
                int mw = menuPos[2];
                int mh = menuPos[3];
                // 增加更大的容差，确保菜单点击不被拦截
                int tolerance = dp(30);
                boolean inMenu = x >= mx - tolerance && x <= mx + mw + tolerance 
                        && y >= my - tolerance && y <= my + mh + tolerance;
                if (inMenu) {
                    android.util.Log.d("ViewTreeOverlay", String.format(
                        "点在悬浮窗菜单内: (%d,%d) 菜单区域: (%d,%d) %dx%d 容差: %d", 
                        x, y, mx, my, mw, mh, tolerance));
                } else {
                    android.util.Log.d("ViewTreeOverlay", String.format(
                        "点不在悬浮窗菜单内: (%d,%d) 菜单区域: (%d,%d) %dx%d", 
                        x, y, mx, my, mw, mh));
                }
                return inMenu;
            } else {
                android.util.Log.d("ViewTreeOverlay", String.format(
                    "悬浮窗菜单位置为null，点: (%d,%d)", x, y));
            }
            return false;
        }
        
        /**
         * 检查点是否在信息窗口范围内
         */
        public boolean isPointInInfoWindow(int x, int y) {
            if (infoWindowView == null) return false;
            // 信息窗口是居中显示的，检查是否在中心区域
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;
            int windowWidth = (int) (screenWidth * 0.8);
            int windowHeight = (int) (screenHeight * 0.7);
            int left = centerX - windowWidth / 2;
            int top = centerY - windowHeight / 2;
            int right = left + windowWidth;
            int bottom = top + windowHeight;
            return x >= left && x <= right && y >= top && y <= bottom;
        }
        
        /**
         * 检查点是否在菜单范围内
         */
        public boolean isPointInMenu(int x, int y) {
            if (nodeMenuView == null || nodeMenuParams == null) return false;
            // 菜单位置由nodeMenuParams决定
            int menuX = nodeMenuParams.x;
            int menuY = nodeMenuParams.y;
            int menuWidth = nodeMenuView.getWidth();
            int menuHeight = nodeMenuView.getHeight();
            if (menuWidth == 0 || menuHeight == 0) {
                // 如果还没有测量，使用估算值
                menuWidth = dp(150);
                menuHeight = dp(150);
            }
            return x >= menuX && x <= menuX + menuWidth && y >= menuY && y <= menuY + menuHeight;
        }
        

        /**
         * 根据坐标查找对应的控件节点
         */
        private AccessibilityNodeInfo findNodeAtPosition(int x, int y) {
            // 获取无障碍服务实例
            PLDScript service = PLDScript.getInstance();
            if (service == null) {
                return null;
            }

            // 获取根节点
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                return null;
            }

            // 查找包含该坐标的节点（从最上层开始）
            AccessibilityNodeInfo foundNode = findNodeAtPositionRecursive(root, x, y);
            
            // 回收根节点
            root.recycle();
            
            return foundNode;
        }

        /**
         * 递归查找包含指定坐标的节点
         */
        private AccessibilityNodeInfo findNodeAtPositionRecursive(AccessibilityNodeInfo node, int x, int y) {
            if (node == null) {
                return null;
            }

            try {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                
                // 检查坐标是否在节点范围内
                if (bounds.contains(x, y)) {
                    // 先检查子节点（更上层的节点）
                    int childCount = node.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            AccessibilityNodeInfo childResult = findNodeAtPositionRecursive(child, x, y);
                            if (childResult != null) {
                                return childResult;
                            }
                        }
                    }
                    // 如果没有子节点包含该坐标，返回当前节点
                    return AccessibilityNodeInfo.obtain(node);
                }
            } catch (Exception e) {
                android.util.Log.e("ViewTreeOverlay", "查找节点时出错", e);
            }
            
            return null;
        }

        /**
         * 刷新控件树并重绘
         */
        public void refresh() {
            nodeRects.clear();
            // 注意：不在这里存储nodeInfos，因为节点会被回收
            // 需要时重新获取
            
            // 获取无障碍服务实例
            PLDScript service = PLDScript.getInstance();
            if (service == null) {
                return;
            }

            // 获取根节点
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                return;
            }

            // 遍历所有节点并获取坐标
            Stack<AccessibilityNodeInfo> stack = new Stack<>();
            stack.push(root);

            while (!stack.isEmpty()) {
                AccessibilityNodeInfo node = stack.pop();
                if (node == null) continue;

                try {
                    // 获取节点在屏幕上的位置
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    
                    // 只添加有效的、可见的控件
                    if (!bounds.isEmpty() && bounds.width() > 0 && bounds.height() > 0) {
                        // 验证坐标是否在屏幕范围内
                        // getBoundsInScreen() 返回的是屏幕绝对坐标，包括状态栏区域
                        // 坐标系统：屏幕左上角为 (0,0)，包括状态栏
                        if (bounds.left >= 0 && bounds.top >= 0 
                                && bounds.left < screenWidth && bounds.top < screenHeight
                                && bounds.right > 0 && bounds.bottom > 0
                                && bounds.right <= screenWidth && bounds.bottom <= screenHeight) {
                            // 直接使用屏幕坐标，覆盖层视图应该从屏幕 (0,0) 开始
                            nodeRects.add(new Rect(bounds));
                        }
                    }

                    // 添加子节点
                    int childCount = node.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            stack.push(child);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("ViewTreeOverlay", "处理节点时出错", e);
                }
            }
            
            // 只回收根节点（子节点由系统管理，回收root时会自动处理）
            try {
                root.recycle();
            } catch (Exception e) {
                android.util.Log.w("ViewTreeOverlay", "回收根节点时出错", e);
            }

            // 强制重绘
            postInvalidate();
        }

        private int dp(int value) {
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (value * density + 0.5f);
        }

        /**
         * 显示节点菜单
         */
        private void showNodeMenu(Context context, AccessibilityNodeInfo node, int x, int y) {
            // 先关闭之前的菜单
            hideNodeMenuInternal();
            
            // 复制节点，因为原节点会被回收
            final AccessibilityNodeInfo nodeCopy = AccessibilityNodeInfo.obtain(node);
            
            // 创建菜单
            LinearLayout menu = new LinearLayout(context);
            menu.setOrientation(LinearLayout.VERTICAL);
            menu.setBackgroundColor(Color.argb(240, 30, 30, 30));
            int pad = dp(12);
            menu.setPadding(pad, pad, pad, pad);
            
            // 创建菜单项
            TextView viewInfo = createMenuItem(context, "查看控件信息");
            TextView viewTree = createMenuItem(context, "查看控件树");
            TextView close = createMenuItem(context, "关闭");
            TextView exit = createMenuItem(context, "退出查看");
            // 退出按钮使用不同的颜色，更醒目
            exit.setBackgroundColor(Color.argb(200, 200, 0, 0));
            
            viewInfo.setOnClickListener(v -> {
                hideNodeMenuInternal();
                showNodeInfo(context, nodeCopy);
                // showNodeInfo 内部会复制节点，所以这里需要回收
                nodeCopy.recycle();
            });
            
            viewTree.setOnClickListener(v -> {
                hideNodeMenuInternal();
                showViewTree(context);
                // 回收节点
                nodeCopy.recycle();
            });
            
            close.setOnClickListener(v -> {
                hideNodeMenuInternal();
                // 回收节点
                nodeCopy.recycle();
            });
            
            exit.setOnClickListener(v -> {
                hideNodeMenuInternal();
                // 回收节点
                nodeCopy.recycle();
                // 关闭查看控件状态
                hide(context);
            });
            
            menu.addView(viewInfo);
            menu.addView(viewTree);
            menu.addView(close);
            menu.addView(exit);
            
            // 创建菜单窗口参数
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            
            nodeMenuParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            nodeMenuParams.gravity = Gravity.TOP | Gravity.START;
            
            // 测量菜单尺寸
            menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int menuWidth = menu.getMeasuredWidth();
            int menuHeight = menu.getMeasuredHeight();
            
            // 计算菜单位置（确保不超出屏幕）
            int menuX = x;
            int menuY = y;
            
            // 如果菜单会超出右边界，调整到左侧
            if (menuX + menuWidth > screenWidth) {
                menuX = x - menuWidth;
            }
            // 如果菜单会超出下边界，调整到上方
            if (menuY + menuHeight > screenHeight) {
                menuY = y - menuHeight;
            }
            
            // 确保不超出左边界和上边界
            menuX = Math.max(0, menuX);
            menuY = Math.max(0, menuY);
            
            nodeMenuParams.x = menuX;
            nodeMenuParams.y = menuY;
            
            nodeMenuView = menu;
            windowManager.addView(nodeMenuView, nodeMenuParams);
        }

        /**
         * 创建菜单项
         */
        private TextView createMenuItem(Context context, String text) {
            TextView tv = new TextView(context);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14);
            tv.setPadding(dp(16), dp(12), dp(16), dp(12));
            tv.setBackgroundColor(Color.argb(200, 60, 60, 60));
            return tv;
        }

        /**
         * 隐藏节点菜单（内部方法）
         */
        private void hideNodeMenuInternal() {
            hideNodeMenu();
        }

        /**
         * 显示控件信息
         */
        private void showNodeInfo(Context context, AccessibilityNodeInfo node) {
            if (node == null) return;
            
            // 复制节点信息，因为原节点可能被回收
            AccessibilityNodeInfo nodeCopy = AccessibilityNodeInfo.obtain(node);
            
            // 创建信息窗口
            ScrollView scrollView = new ScrollView(context);
            scrollView.setBackgroundColor(Color.argb(250, 20, 20, 20));
            
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(16), dp(16), dp(16));
            
            // 收集节点信息，使用Android原生名称
            StringBuilder info = new StringBuilder();
            info.append("AccessibilityNodeInfo\n\n");
            
            try {
                // ========== Basic Information ==========
                info.append("=== Basic Information ===\n\n");
                
                // className
                CharSequence className = nodeCopy.getClassName();
                if (className != null) {
                    info.append("className: ").append(className).append("\n");
                } else {
                    info.append("className: null\n");
                }
                
                // viewIdResourceName (完整ID全名)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    String viewId = nodeCopy.getViewIdResourceName();
                    if (viewId != null) {
                        info.append("viewIdResourceName: ").append(viewId).append("\n");
                    } else {
                        info.append("viewIdResourceName: null\n");
                    }
                }
                
                // packageName
                CharSequence packageName = nodeCopy.getPackageName();
                if (packageName != null) {
                    info.append("packageName: ").append(packageName).append("\n");
                } else {
                    info.append("packageName: null\n");
                }
                
                info.append("\n");
                
                // ========== Text Information ==========
                info.append("=== Text Information ===\n\n");
                
                // text
                CharSequence text = nodeCopy.getText();
                if (text != null && text.length() > 0) {
                    info.append("text: ").append(text).append("\n");
                    info.append("text.length(): ").append(text.length()).append("\n");
                } else {
                    info.append("text: null\n");
                }
                
                // contentDescription
                CharSequence desc = nodeCopy.getContentDescription();
                if (desc != null && desc.length() > 0) {
                    info.append("contentDescription: ").append(desc).append("\n");
                } else {
                    info.append("contentDescription: null\n");
                }
                
                // hintText (API 26+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    CharSequence hintText = nodeCopy.getHintText();
                    if (hintText != null && hintText.length() > 0) {
                        info.append("hintText: ").append(hintText).append("\n");
                    } else {
                        info.append("hintText: null\n");
                    }
                }
                
                // error (API 23+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    CharSequence error = nodeCopy.getError();
                    if (error != null && error.length() > 0) {
                        info.append("error: ").append(error).append("\n");
                    } else {
                        info.append("error: null\n");
                    }
                }
                
                // stateDescription (API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    CharSequence stateDesc = nodeCopy.getStateDescription();
                    if (stateDesc != null && stateDesc.length() > 0) {
                        info.append("stateDescription: ").append(stateDesc).append("\n");
                    } else {
                        info.append("stateDescription: null\n");
                    }
                }
                
                info.append("\n");
                
                // ========== Bounds ==========
                info.append("=== Bounds ===\n\n");
                
                Rect bounds = new Rect();
                nodeCopy.getBoundsInScreen(bounds);
                info.append("boundsInScreen.left: ").append(bounds.left).append("\n");
                info.append("boundsInScreen.top: ").append(bounds.top).append("\n");
                info.append("boundsInScreen.right: ").append(bounds.right).append("\n");
                info.append("boundsInScreen.bottom: ").append(bounds.bottom).append("\n");
                info.append("boundsInScreen.width(): ").append(bounds.width()).append("\n");
                info.append("boundsInScreen.height(): ").append(bounds.height()).append("\n");
                info.append("boundsInScreen.centerX(): ").append(bounds.centerX()).append("\n");
                info.append("boundsInScreen.centerY(): ").append(bounds.centerY()).append("\n");
                
                // boundsInParent (已废弃，但可能仍有用)
                Rect boundsInParent = new Rect();
                nodeCopy.getBoundsInParent(boundsInParent);
                info.append("boundsInParent: (").append(boundsInParent.left).append(", ")
                    .append(boundsInParent.top).append(", ").append(boundsInParent.right)
                    .append(", ").append(boundsInParent.bottom).append(")\n");
                
                info.append("\n");
                
                // ========== Hierarchy ==========
                info.append("=== Hierarchy ===\n\n");
                
                // depth
                int depth = calculateNodeDepth(nodeCopy);
                info.append("depth: ").append(depth).append("\n");
                
                // childCount
                int childCount = nodeCopy.getChildCount();
                info.append("childCount: ").append(childCount).append("\n");
                
                // parent
                AccessibilityNodeInfo parent = nodeCopy.getParent();
                if (parent != null) {
                    CharSequence parentClassName = parent.getClassName();
                    if (parentClassName != null) {
                        info.append("parent.className: ").append(parentClassName).append("\n");
                    }
                    String parentViewId = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        parentViewId = parent.getViewIdResourceName();
                    }
                    if (parentViewId != null) {
                        info.append("parent.viewIdResourceName: ").append(parentViewId).append("\n");
                    }
                    parent.recycle();
                } else {
                    info.append("parent: null (root node)\n");
                }
                
                info.append("\n");
                
                // ========== State Properties ==========
                info.append("=== State Properties ===\n\n");
                
                info.append("isClickable(): ").append(nodeCopy.isClickable()).append("\n");
                info.append("isLongClickable(): ").append(nodeCopy.isLongClickable()).append("\n");
                info.append("isFocusable(): ").append(nodeCopy.isFocusable()).append("\n");
                info.append("isFocused(): ").append(nodeCopy.isFocused()).append("\n");
                info.append("isSelected(): ").append(nodeCopy.isSelected()).append("\n");
                info.append("isEnabled(): ").append(nodeCopy.isEnabled()).append("\n");
                info.append("isScrollable(): ").append(nodeCopy.isScrollable()).append("\n");
                info.append("isEditable(): ").append(nodeCopy.isEditable()).append("\n");
                info.append("isCheckable(): ").append(nodeCopy.isCheckable()).append("\n");
                info.append("isChecked(): ").append(nodeCopy.isChecked()).append("\n");
                info.append("isPassword(): ").append(nodeCopy.isPassword()).append("\n");
                info.append("isMultiLine(): ").append(nodeCopy.isMultiLine()).append("\n");
                info.append("isDismissable(): ").append(nodeCopy.isDismissable()).append("\n");
                info.append("isImportantForAccessibility(): ").append(nodeCopy.isImportantForAccessibility()).append("\n");
                
                // API 19+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    info.append("isVisibleToUser(): ").append(nodeCopy.isVisibleToUser()).append("\n");
                }
                
                // API 21+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    info.append("isAccessibilityFocused(): ").append(nodeCopy.isAccessibilityFocused()).append("\n");
                }
                
                // API 22+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    info.append("canOpenPopup(): ").append(nodeCopy.canOpenPopup()).append("\n");
                }
                
                // API 23+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    info.append("isContextClickable(): ").append(nodeCopy.isContextClickable()).append("\n");
                }
                
                // API 26+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    info.append("isHeading(): ").append(nodeCopy.isHeading()).append("\n");
                }
                
                // API 28+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.append("isShowingHintText(): ").append(nodeCopy.isShowingHintText()).append("\n");
                }
                
                info.append("\n");
                
                // ========== Actions ==========
                info.append("=== Available Actions ===\n\n");
                
                int actionsInt = nodeCopy.getActions();
                info.append("getActions(): ").append(actionsInt).append(" (0x").append(Integer.toHexString(actionsInt)).append(")\n");
                
                if (actionsInt != 0) {
                    List<String> actionNames = new ArrayList<>();
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_FOCUS) != 0) {
                        actionNames.add("ACTION_FOCUS");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_CLEAR_FOCUS) != 0) {
                        actionNames.add("ACTION_CLEAR_FOCUS");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_SELECT) != 0) {
                        actionNames.add("ACTION_SELECT");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_CLEAR_SELECTION) != 0) {
                        actionNames.add("ACTION_CLEAR_SELECTION");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_CLICK) != 0) {
                        actionNames.add("ACTION_CLICK");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_LONG_CLICK) != 0) {
                        actionNames.add("ACTION_LONG_CLICK");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) != 0) {
                        actionNames.add("ACTION_ACCESSIBILITY_FOCUS");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) != 0) {
                        actionNames.add("ACTION_CLEAR_ACCESSIBILITY_FOCUS");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY) != 0) {
                        actionNames.add("ACTION_NEXT_AT_MOVEMENT_GRANULARITY");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) != 0) {
                        actionNames.add("ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT) != 0) {
                        actionNames.add("ACTION_NEXT_HTML_ELEMENT");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT) != 0) {
                        actionNames.add("ACTION_PREVIOUS_HTML_ELEMENT");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != 0) {
                        actionNames.add("ACTION_SCROLL_FORWARD");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) != 0) {
                        actionNames.add("ACTION_SCROLL_BACKWARD");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_COPY) != 0) {
                        actionNames.add("ACTION_COPY");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_PASTE) != 0) {
                        actionNames.add("ACTION_PASTE");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_CUT) != 0) {
                        actionNames.add("ACTION_CUT");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_SET_SELECTION) != 0) {
                        actionNames.add("ACTION_SET_SELECTION");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_EXPAND) != 0) {
                        actionNames.add("ACTION_EXPAND");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_COLLAPSE) != 0) {
                        actionNames.add("ACTION_COLLAPSE");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_DISMISS) != 0) {
                        actionNames.add("ACTION_DISMISS");
                    }
                    if ((actionsInt & AccessibilityNodeInfo.ACTION_SET_TEXT) != 0) {
                        actionNames.add("ACTION_SET_TEXT");
                    }
                    // API 23+ (使用反射获取常量值)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            java.lang.reflect.Field field;
                            // ACTION_SHOW_ON_SCREEN
                            try {
                                field = AccessibilityNodeInfo.class.getField("ACTION_SHOW_ON_SCREEN");
                                int actionValue = field.getInt(null);
                                if ((actionsInt & actionValue) != 0) {
                                    actionNames.add("ACTION_SHOW_ON_SCREEN");
                                }
                            } catch (Exception e) {
                                // 常量不存在，跳过
                            }
                            // ACTION_SCROLL_TO_POSITION
                            try {
                                field = AccessibilityNodeInfo.class.getField("ACTION_SCROLL_TO_POSITION");
                                int actionValue = field.getInt(null);
                                if ((actionsInt & actionValue) != 0) {
                                    actionNames.add("ACTION_SCROLL_TO_POSITION");
                                }
                            } catch (Exception e) {
                                // 常量不存在，跳过
                            }
                            // ACTION_SCROLL_UP
                            try {
                                field = AccessibilityNodeInfo.class.getField("ACTION_SCROLL_UP");
                                int actionValue = field.getInt(null);
                                if ((actionsInt & actionValue) != 0) {
                                    actionNames.add("ACTION_SCROLL_UP");
                                }
                            } catch (Exception e) {
                                // 常量不存在，跳过
                            }
                            // ACTION_SCROLL_DOWN
                            try {
                                field = AccessibilityNodeInfo.class.getField("ACTION_SCROLL_DOWN");
                                int actionValue = field.getInt(null);
                                if ((actionsInt & actionValue) != 0) {
                                    actionNames.add("ACTION_SCROLL_DOWN");
                                }
                            } catch (Exception e) {
                                // 常量不存在，跳过
                            }
                            // ACTION_SCROLL_LEFT
                            try {
                                field = AccessibilityNodeInfo.class.getField("ACTION_SCROLL_LEFT");
                                int actionValue = field.getInt(null);
                                if ((actionsInt & actionValue) != 0) {
                                    actionNames.add("ACTION_SCROLL_LEFT");
                                }
                            } catch (Exception e) {
                                // 常量不存在，跳过
                            }
                            // ACTION_SCROLL_RIGHT
                            try {
                                field = AccessibilityNodeInfo.class.getField("ACTION_SCROLL_RIGHT");
                                int actionValue = field.getInt(null);
                                if ((actionsInt & actionValue) != 0) {
                                    actionNames.add("ACTION_SCROLL_RIGHT");
                                }
                            } catch (Exception e) {
                                // 常量不存在，跳过
                            }
                            // ACTION_CONTEXT_CLICK
                            try {
                                field = AccessibilityNodeInfo.class.getField("ACTION_CONTEXT_CLICK");
                                int actionValue = field.getInt(null);
                                if ((actionsInt & actionValue) != 0) {
                                    actionNames.add("ACTION_CONTEXT_CLICK");
                                }
                            } catch (Exception e) {
                                // 常量不存在，跳过
                            }
                        } catch (Exception e) {
                            // 反射失败，跳过这些常量
                        }
                    }
                    // API 24+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            java.lang.reflect.Field field = AccessibilityNodeInfo.class.getField("ACTION_SET_PROGRESS");
                            int actionValue = field.getInt(null);
                            if ((actionsInt & actionValue) != 0) {
                                actionNames.add("ACTION_SET_PROGRESS");
                            }
                        } catch (Exception e) {
                            // 常量不存在，跳过
                        }
                    }
                    
                    if (actionNames.isEmpty()) {
                        info.append("No standard actions found\n");
                    } else {
                        for (String actionName : actionNames) {
                            info.append(actionName).append("\n");
                        }
                    }
                } else {
                    info.append("No actions available\n");
                }
                
                info.append("\n");
                
                // ========== Additional Information ==========
                info.append("=== Additional Information ===\n\n");
                
                // windowId
                info.append("windowId: ").append(nodeCopy.getWindowId()).append("\n");
                
                // liveRegion (API 19+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    info.append("liveRegion: ").append(nodeCopy.getLiveRegion()).append("\n");
                }
                
                // drawingOrder (API 24+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    info.append("drawingOrder: ").append(nodeCopy.getDrawingOrder()).append("\n");
                }
                
                // inputType (API 19+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    int inputType = nodeCopy.getInputType();
                    info.append("inputType: ").append(inputType).append(" (0x").append(Integer.toHexString(inputType)).append(")\n");
                }
                
                // maxTextLength (API 21+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    int maxTextLength = nodeCopy.getMaxTextLength();
                    info.append("maxTextLength: ").append(maxTextLength).append("\n");
                }
                
                // textSelectionStart (API 18+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    int textSelectionStart = nodeCopy.getTextSelectionStart();
                    int textSelectionEnd = nodeCopy.getTextSelectionEnd();
                    info.append("textSelectionStart: ").append(textSelectionStart).append("\n");
                    info.append("textSelectionEnd: ").append(textSelectionEnd).append("\n");
                }
                
                // collectionInfo (API 19+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    AccessibilityNodeInfo.CollectionInfo collectionInfo = nodeCopy.getCollectionInfo();
                    if (collectionInfo != null) {
                        info.append("collectionInfo.rowCount: ").append(collectionInfo.getRowCount()).append("\n");
                        info.append("collectionInfo.columnCount: ").append(collectionInfo.getColumnCount()).append("\n");
                        info.append("collectionInfo.isHierarchical: ").append(collectionInfo.isHierarchical()).append("\n");
                        info.append("collectionInfo.selectionMode: ").append(collectionInfo.getSelectionMode()).append("\n");
                    } else {
                        info.append("collectionInfo: null\n");
                    }
                }
                
                // collectionItemInfo (API 19+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    AccessibilityNodeInfo.CollectionItemInfo collectionItemInfo = nodeCopy.getCollectionItemInfo();
                    if (collectionItemInfo != null) {
                        info.append("collectionItemInfo.rowIndex: ").append(collectionItemInfo.getRowIndex()).append("\n");
                        info.append("collectionItemInfo.rowSpan: ").append(collectionItemInfo.getRowSpan()).append("\n");
                        info.append("collectionItemInfo.columnIndex: ").append(collectionItemInfo.getColumnIndex()).append("\n");
                        info.append("collectionItemInfo.columnSpan: ").append(collectionItemInfo.getColumnSpan()).append("\n");
                        info.append("collectionItemInfo.isHeading: ").append(collectionItemInfo.isHeading()).append("\n");
                        info.append("collectionItemInfo.isSelected: ").append(collectionItemInfo.isSelected()).append("\n");
                    } else {
                        info.append("collectionItemInfo: null\n");
                    }
                }
                
                // rangeInfo (API 22+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    AccessibilityNodeInfo.RangeInfo rangeInfo = nodeCopy.getRangeInfo();
                    if (rangeInfo != null) {
                        info.append("rangeInfo.min: ").append(rangeInfo.getMin()).append("\n");
                        info.append("rangeInfo.max: ").append(rangeInfo.getMax()).append("\n");
                        info.append("rangeInfo.current: ").append(rangeInfo.getCurrent()).append("\n");
                        info.append("rangeInfo.type: ").append(rangeInfo.getType()).append("\n");
                    } else {
                        info.append("rangeInfo: null\n");
                    }
                }
                
                // extras (API 19+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    android.os.Bundle extras = nodeCopy.getExtras();
                    if (extras != null && !extras.isEmpty()) {
                        info.append("extras: ").append(extras.toString()).append("\n");
                    } else {
                        info.append("extras: null or empty\n");
                    }
                }
                
            } finally {
                // 回收复制的节点
                nodeCopy.recycle();
            }
            
            TextView textView = new TextView(context);
            textView.setText(info.toString());
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(12);
            textView.setPadding(dp(8), dp(8), dp(8), dp(8));
            
            content.addView(textView);
            scrollView.addView(content);
            
            // 创建信息窗口
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    (int) (screenWidth * 0.8),
                    (int) (screenHeight * 0.7),
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 允许外部触摸
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;
            
            // 添加关闭按钮
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackgroundColor(Color.argb(250, 20, 20, 20));
            
            TextView closeBtn = new TextView(context);
            closeBtn.setText("关闭");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(16);
            closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
            closeBtn.setBackgroundColor(Color.argb(200, 100, 0, 0));
            closeBtn.setGravity(Gravity.CENTER);
            closeBtn.setOnClickListener(v -> {
                hideInfoWindow();
            });
            
            container.addView(closeBtn);
            container.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            
            // 设置容器可点击，防止点击穿透
            container.setOnClickListener(v -> {
                // 点击容器本身不关闭，只有点击关闭按钮才关闭
            });
            
            // 设置窗口标志
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            
            infoWindowView = container;
            windowManager.addView(container, params);
            
            // 注册返回键监听
            registerBackKeyHandler(container);
        }

        /**
         * 显示控件树
         */
        private void showViewTree(Context context) {
            PLDScript service = PLDScript.getInstance();
            if (service == null) return;
            
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) return;
            
            // 创建树形视图
            ScrollView scrollView = new ScrollView(context);
            scrollView.setBackgroundColor(Color.argb(250, 20, 20, 20));
            
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(16), dp(16), dp(16));
            
            // 构建树形文本
            StringBuilder treeText = new StringBuilder();
            buildTreeText(root, treeText, "", true);
            
            TextView textView = new TextView(context);
            textView.setText(treeText.toString());
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(10);
            textView.setPadding(dp(8), dp(8), dp(8), dp(8));
            textView.setTypeface(android.graphics.Typeface.MONOSPACE);
            
            content.addView(textView);
            scrollView.addView(content);
            
            // 创建树形视图窗口
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    (int) (screenWidth * 0.9),
                    (int) (screenHeight * 0.8),
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 允许外部触摸
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;
            
            // 添加关闭按钮
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackgroundColor(Color.argb(250, 20, 20, 20));
            
            TextView closeBtn = new TextView(context);
            closeBtn.setText("关闭");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(16);
            closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
            closeBtn.setBackgroundColor(Color.argb(200, 100, 0, 0));
            closeBtn.setGravity(Gravity.CENTER);
            closeBtn.setOnClickListener(v -> {
                hideTreeWindow();
            });
            
            container.addView(closeBtn);
            container.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            
            // 设置容器可点击，防止点击穿透
            container.setOnClickListener(v -> {
                // 点击容器本身不关闭，只有点击关闭按钮才关闭
            });
            
            // 设置窗口标志 - 允许接收触摸和按键事件
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            
            treeWindowView = container;
            windowManager.addView(container, params);
            
            // 注册返回键监听
            registerBackKeyHandler(container);
            
            // 回收根节点
            root.recycle();
        }

        /**
         * 递归构建树形文本
         */
        private void buildTreeText(AccessibilityNodeInfo node, StringBuilder sb, String prefix, boolean isLast) {
            if (node == null) return;
            
            try {
                // 当前节点信息
                sb.append(prefix);
                sb.append(isLast ? "└─ " : "├─ ");
                
                CharSequence className = node.getClassName();
                if (className != null) {
                    sb.append(className);
                }
                
                CharSequence text = node.getText();
                if (text != null && text.length() > 0) {
                    String textStr = text.toString();
                    if (textStr.length() > 20) {
                        textStr = textStr.substring(0, 20) + "...";
                    }
                    sb.append(" [").append(textStr).append("]");
                }
                
                sb.append("\n");
                
                // 子节点
                int childCount = node.getChildCount();
                String childPrefix = prefix + (isLast ? "   " : "│  ");
                
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        buildTreeText(child, sb, childPrefix, i == childCount - 1);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ViewTreeOverlay", "构建树形文本时出错", e);
            }
        }
    }
    
    /**
     * 隐藏树形视图窗口
     */
    public static void hideTreeWindow() {
        if (treeWindowView != null && windowManager != null) {
            try {
                windowManager.removeView(treeWindowView);
            } catch (Exception e) {
                android.util.Log.e("ViewTreeOverlay", "关闭树形视图时出错", e);
            }
            treeWindowView = null;
        }
    }
    
    /**
     * 检查是否有信息窗口显示
     */
    public static boolean hasInfoWindow() {
        return infoWindowView != null;
    }
    
    /**
     * 检查是否有树形视图窗口显示
     */
    public static boolean hasTreeWindow() {
        return treeWindowView != null;
    }
    
    /**
     * 隐藏信息窗口（公开方法）
     */
    public static void hideInfoWindow() {
        if (infoWindowView != null && windowManager != null) {
            try {
                windowManager.removeView(infoWindowView);
            } catch (Exception e) {
                android.util.Log.e("ViewTreeOverlay", "关闭信息窗口时出错", e);
            }
            infoWindowView = null;
        }
    }
    
    /**
     * 隐藏节点菜单（公开方法）
     */
    public static void hideNodeMenu() {
        if (nodeMenuView != null && windowManager != null) {
            try {
                windowManager.removeView(nodeMenuView);
            } catch (Exception e) {
                android.util.Log.e("ViewTreeOverlay", "移除菜单时出错", e);
            }
            nodeMenuView = null;
            nodeMenuParams = null;
        }
    }
    
    /**
     * 检查是否有节点菜单显示
     */
    public static boolean hasNodeMenu() {
        return nodeMenuView != null;
    }
    
    /**
     * 计算节点深度
     */
    private static int calculateNodeDepth(AccessibilityNodeInfo node) {
        if (node == null) return 0;
        int depth = 0;
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            depth++;
            AccessibilityNodeInfo temp = parent.getParent();
            parent.recycle();
            parent = temp;
        }
        return depth;
    }
    
    // getActionName 方法已移除，直接使用 ACTION 常量名
    
    /**
     * 注册返回键处理器
     */
    private static void registerBackKeyHandler(View container) {
        // 设置容器可以接收按键事件
        container.setFocusable(true);
        container.setFocusableInTouchMode(true);
        container.requestFocus();
        
        // 设置按键监听
        container.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK 
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    hideInfoWindow();
                    hideTreeWindow();
                    hideNodeMenu();
                    return true;
                }
                return false;
            }
        });
    }
}

