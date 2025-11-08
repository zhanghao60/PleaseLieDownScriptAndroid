package com.app.pldscript;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.main.script.MainScript;

public class FloatWindow {
    private static WindowManager windowManager;
    private static View bubbleView;
    private static View menuView;
    private static WindowManager.LayoutParams bubbleParams;
    private static WindowManager.LayoutParams menuParams;
    
    /**
     * 获取悬浮窗位置信息，用于ViewTreeOverlay避免拦截点击
     */
    public static int[] getBubblePosition() {
        if (bubbleParams != null && bubbleView != null) {
            int width = bubbleView.getWidth();
            int height = bubbleView.getHeight();
            if (width == 0 || height == 0) {
                // 如果还没有测量，使用估算值
                width = dp(null, 60);
                height = dp(null, 60);
            }
            return new int[]{bubbleParams.x, bubbleParams.y, width, height};
        }
        return null;
    }
    
    /**
     * 获取菜单位置信息
     */
    public static int[] getMenuPosition() {
        if (menuParams != null && menuView != null) {
            int width = menuView.getWidth();
            int height = menuView.getHeight();
            if (width == 0 || height == 0) {
                width = dp(null, 150);
                height = dp(null, 200);
            }
            return new int[]{menuParams.x, menuParams.y, width, height};
        }
        return null;
    }

    public static boolean isShowing() {
        return bubbleView != null;
    }

    public static void show(Context context) {
        if (bubbleView != null) return;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        // 先初始化控件树查看器（如果启用的话，会在后面添加覆盖层）
        ViewTreeOverlay.init(context);
        // 然后创建悬浮窗，确保悬浮窗在上层（后添加的窗口在上层）
        createBubble(context);
    }

    public static void hide() {
        removeMenu();
        if (windowManager != null && bubbleView != null) {
            windowManager.removeView(bubbleView);
            bubbleView = null;
        }
        // 隐藏控件树查看器（需要context，但这里可能已经无法获取，所以先不传）
        // ViewTreeOverlay会在下次show时重新初始化
    }

    private static void createBubble(Context context) {
        TextView bubble = new TextView(context);
        bubble.setText("PLD");
        bubble.setTextColor(Color.WHITE);
        bubble.setBackgroundResource(R.drawable.round_bubble_bg);
        int padding = dp(context, 12);
        bubble.setPadding(padding, padding, padding, padding);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 允许接收触摸事件，但事件可以穿透
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;

        bubble.setOnTouchListener(new View.OnTouchListener(){
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;
            private final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return false; // 不拦截，允许点击
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            windowManager.updateViewLayout(bubble, params);
                            // 菜单跟随
                            if (menuView != null && menuParams != null) {
                            int[] pos = computeMenuPosition(context, bubble, params);
                                menuParams.x = pos[0];
                                menuParams.y = pos[1];
                                windowManager.updateViewLayout(menuView, menuParams);
                            }
                            return true; // 拖动时拦截
                        }
                        return false; // 未达到拖动阈值，不拦截
                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            // 吸附到最近边缘并限制到屏幕内
                            int screenW = context.getResources().getDisplayMetrics().widthPixels;
                            int screenH = context.getResources().getDisplayMetrics().heightPixels;
                            int bw = bubble.getWidth();
                            int bh = bubble.getHeight();
                            if (bw == 0 || bh == 0) {
                                bubble.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                                bw = bubble.getMeasuredWidth();
                                bh = bubble.getMeasuredHeight();
                            }
                            int targetX = (params.x + bw / 2 < screenW / 2) ? 0 : Math.max(0, screenW - bw);
                            int targetY = Math.max(0, Math.min(params.y, Math.max(0, screenH - bh)));
                            params.x = targetX;
                            params.y = targetY;
                            windowManager.updateViewLayout(bubble, params);
                            // 更新菜单位置
                            if (menuView != null && menuParams != null) {
                                int[] pos = computeMenuPosition(context, bubble, params);
                                menuParams.x = pos[0];
                                menuParams.y = pos[1];
                                windowManager.updateViewLayout(menuView, menuParams);
                            }
                            return true; // 拖动结束，已拦截
                        }
                        return false; // 非拖动，放行以触发 onClick
                }
                return false;
            }
        });

        bubble.setOnClickListener(v -> toggleMenu(context));

        bubbleView = bubble;
        bubbleParams = params;
        windowManager.addView(bubbleView, params);
    }

    private static void toggleMenu(Context context) {
        if (menuView == null) {
            showMenu(context);
        } else {
            removeMenu();
        }
    }
    
    /**
     * 更新菜单中控件查看按钮的文本
     */
    public static void updateViewTreeButton() {
        if (menuView != null && menuView instanceof LinearLayout) {
            LinearLayout menu = (LinearLayout) menuView;
            // viewTree 是第三个子视图（索引为2：start=0, stop=1, viewTree=2, exit=3）
            if (menu.getChildCount() > 2) {
                TextView viewTree = (TextView) menu.getChildAt(2);
                if (viewTree != null) {
                    viewTree.setText(ViewTreeOverlay.isEnabled() ? "关闭控件查看" : "开启控件查看");
                }
            }
        }
    }

    private static void showMenu(Context context) {
        if (menuView != null) return;
        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(Color.argb(230, 40, 40, 40));
        int pad = dp(context, 8);
        menu.setPadding(pad, pad, pad, pad);

        TextView start = buildMenuItem(context, MainScript.isRunning() ? "脚本已运行" : "启动脚本");
        TextView stop = buildMenuItem(context, "关闭脚本");
        TextView viewTree = buildMenuItem(context, ViewTreeOverlay.isEnabled() ? "关闭控件查看" : "开启控件查看");
        TextView exit = buildMenuItem(context, "退出悬浮窗");

        start.setOnClickListener(v -> {
            MainScript.start(context.getApplicationContext());
            updateMenuLabels(start);
        });
        stop.setOnClickListener(v -> {
            MainScript.stop();
            updateMenuLabels(start);
        });
        viewTree.setOnClickListener(v -> {
            // 先获取当前状态
            boolean wasEnabled = ViewTreeOverlay.isEnabled();
            // 切换状态
            ViewTreeOverlay.toggle(context);
            // 切换后，状态应该相反，所以更新按钮文本
            viewTree.setText(ViewTreeOverlay.isEnabled() ? "关闭控件查看" : "开启控件查看");
            // 如果开启了，刷新一次显示
            if (ViewTreeOverlay.isEnabled()) {
                ViewTreeOverlay.refresh();
            }
        });
        exit.setOnClickListener(v -> {
            hide();
        });

        menu.addView(start);
        menu.addView(stop);
        menu.addView(viewTree);
        menu.addView(exit);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        // 预先测量菜单以便更紧密贴合
        menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int measuredW = Math.max(1, menu.getMeasuredWidth());
        int measuredH = Math.max(1, menu.getMeasuredHeight());
        int[] initialPos = computeMenuPosition(context, bubbleView, bubbleParams, measuredW, measuredH);
        params.x = initialPos[0];
        params.y = initialPos[1];

        menuView = menu;
        menuParams = params;
        windowManager.addView(menuView, params);
    }

    private static void updateMenuLabels(TextView startLabel) {
        startLabel.setText(MainScript.isRunning() ? "脚本已运行" : "启动脚本");
    }

    private static TextView buildMenuItem(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        return tv;
    }

    private static void removeMenu() {
        if (windowManager != null && menuView != null) {
            windowManager.removeView(menuView);
            menuView = null;
            menuParams = null;
        }
    }

    private static int[] computeMenuPosition(Context context, View anchor, WindowManager.LayoutParams anchorParams) {
        // 当已添加菜单时，使用其真实尺寸；否则使用一个更小的预估值
        int mw = (menuView != null && menuView.getWidth() > 0) ? menuView.getWidth() : dp(context, 120);
        int mh = (menuView != null && menuView.getHeight() > 0) ? menuView.getHeight() : dp(context, 64);
        return computeMenuPosition(context, anchor, anchorParams, mw, mh);
    }

    private static int[] computeMenuPosition(Context context, View anchor, WindowManager.LayoutParams anchorParams, int menuW, int menuH) {
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        int ax = anchorParams != null ? anchorParams.x : 0;
        int ay = anchorParams != null ? anchorParams.y : 0;
        int aw = (anchor != null && anchor.getWidth() > 0) ? anchor.getWidth() : dp(context, 48);
        int ah = (anchor != null && anchor.getHeight() > 0) ? anchor.getHeight() : dp(context, 48);
        int margin = dp(context, 2); // 更紧贴

        boolean dockLeft = (ax + aw / 2) < screenW / 2;
        int mx = dockLeft ? ax + aw + margin : ax - menuW - margin;
        int my = ay + (ah - menuH) / 2; // 垂直居中对齐

        // 约束菜单不超出屏幕（简单限制）
        mx = Math.max(0, Math.min(mx, screenW - menuW));
        my = Math.max(0, Math.min(my, screenH - menuH));
        return new int[] { mx, my };
    }

    private static int dp(Context context, int v) {
        if (context == null && windowManager != null) {
            // 如果没有context，尝试从windowManager获取
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            return (int) (v * metrics.density + 0.5f);
        }
        if (context != null) {
            float d = context.getResources().getDisplayMetrics().density;
            return (int) (v * d + 0.5f);
        }
        return v; // 如果都无法获取，返回原值
    }
}
