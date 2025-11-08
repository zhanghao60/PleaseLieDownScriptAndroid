package com.app.pldscript;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.graphics.Path;
import android.accessibilityservice.GestureDescription;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.ArrayList;
import android.os.Build;


public class PLDScript extends AccessibilityService {
    //单例模式
    private static PLDScript instance;
    //TAG
    private static final String TAG = "PLDScript";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 当监听到指定事件时（如窗口变化、按钮点击等），在此处理逻辑
        // 例如：获取当前窗口内容、模拟点击等
        
        // 如果控件树查看器已启用，在窗口内容变化时自动刷新
        if (ViewTreeOverlay.isEnabled()) {
            int eventType = event.getEventType();
            // 监听窗口内容变化、窗口状态变化等事件
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                ViewTreeOverlay.refresh();
            }
        }
    }

    @Override
    public void onInterrupt() {
        // 服务被中断时调用（如系统回收资源）
    }
    
    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        // 监听按键事件（需要设置canRequestFilterKeyEvents="true"）
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        
        Log.d(TAG, String.format("按键事件: keyCode=%d, action=%d", keyCode, action));
        
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK 
                && action == android.view.KeyEvent.ACTION_UP) {
            // 检查是否有信息窗口、树形视图窗口或菜单正在显示
            boolean hasInfo = ViewTreeOverlay.hasInfoWindow();
            boolean hasTree = ViewTreeOverlay.hasTreeWindow();
            boolean hasMenu = ViewTreeOverlay.hasNodeMenu();
            
            Log.d(TAG, String.format("返回键按下 - 信息窗口: %s, 树形视图: %s, 菜单: %s, 控件查看已启用: %s", 
                hasInfo, hasTree, hasMenu, ViewTreeOverlay.isEnabled()));
            
            // 如果信息窗口、树形视图窗口或菜单正在显示，先关闭它们
            if (hasInfo || hasTree || hasMenu) {
                Log.d(TAG, "检测到返回键，关闭所有查看窗口");
                ViewTreeOverlay.hideInfoWindow();
                ViewTreeOverlay.hideTreeWindow();
                ViewTreeOverlay.hideNodeMenu();
                return true; // 消费返回键事件，阻止默认行为
            }
            
            // 如果控件查看功能已启用（但没有信息窗口显示），则完全退出查看控件状态
            if (ViewTreeOverlay.isEnabled()) {
                Log.d(TAG, "检测到返回键，完全退出查看控件状态");
                ViewTreeOverlay.hide(this); // PLDScript 继承自 AccessibilityService，本身就是一个 Context
                return true; // 消费返回键事件，阻止默认行为
            }
        }
        return super.onKeyEvent(event);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility service connected");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        Log.d(TAG, "Accessibility service unbound");
        instance = null;
        return super.onUnbind(intent);
    }

    /**
     * 获取服务实例
     */
    public static PLDScript getInstance() {
        return instance;
    }

    
    // ==================== 功能实现 ====================
    /**
     * 点击操作 - 根据坐标点击
     * @param x X坐标
     * @param y Y坐标
     * @param duration 持续时间（毫秒）
     * @return 是否成功
     */
    public static boolean Click(int x, int y, int duration) {
        try {
            //检查无障碍服务
            if (instance == null) {
                Log.e(TAG, "无障碍服务未初始化");
                return false;
            }

            Log.d(TAG, "准备执行点击: (" + x + ", " + y + "), 持续时间: " + duration + "ms");

            //构建点击路径
            Path path = new Path();
            path.moveTo(x, y);

            //创建手势描述
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();

            //执行手势
            boolean success = instance.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d(TAG, "✅ 点击操作完成: (" + x + ", " + y + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w(TAG, "❌ 点击操作被取消: (" + x + ", " + y + ")");
                    Log.w(TAG, "可能原因: 1.权限不足 2.坐标无效 3.系统限制 4.服务状态异常");
                }
            }, null);

            Log.d(TAG, "点击操作调度结果: " + success + " at (" + x + ", " + y + ")");
            return success;
        } catch (Exception e) {
            Log.e(TAG, "坐标(" + x + ", " + y + ")点击操作失败", e);
            return false;
        }
    }



    /**
     * 等待时间
     * @param milliseconds 等待时间（毫秒）
     */
    public static void Sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Log.e(TAG, "等待时间中断", e);
        }
    }


    /**
     * 滑动操作
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 结束X坐标
     * @param endY 结束Y坐标
     * @param duration 持续时间（毫秒）
     * @return 是否成功
     */
    public static boolean Swipe(int startX, int startY, int endX, int endY, int duration) {
        try {
            //检查无障碍服务
            if (instance == null) {
                Log.e(TAG, "无障碍服务未初始化");
                return false;
            }

            //构建滑动路径
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);

            //创建手势描述
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();

            //执行手势
            boolean success = instance.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d(TAG, "滑动操作完成: from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w(TAG, "滑动操作被取消: from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
                }
            }, null);

            Log.d(TAG, "滑动操作: " + success + " from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
            return success;
        } catch (Exception e) {
            Log.e(TAG, "执行滑动操作失败", e);
            return false;
        }
    }


    /**
     * 控件点击
     * @param node 目标节点
     * @return 是否成功
     */
    public static boolean NodeClick(AccessibilityNodeInfo node) {
        try {
            //检查无障碍服务
            if (instance == null) {
                Log.e(TAG, "无障碍服务未初始化");
                return false;
            }
            //检查控件是否为空
            if (node == null) {
                Log.e(TAG, "控件为空，无法点击");
                return false;
            }
            
            //检查控件是否可点击
            if (!node.isClickable()) {
                Log.w(TAG, "控件不可点击");
                return false;
            }
            
            //执行点击
            boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (success) {
                Log.d(TAG, "控件点击成功");
                return true;
            } else {
                Log.w(TAG, "控件点击失败");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "点击控件失败", e);
            return false;
        }
    }
  



    /**
     * 输入文本
     * @param text 文本
     * @param node 节点
     * @return 是否成功
     */
    public static boolean InputText(String text, AccessibilityNodeInfo node) {
        try {
            if (node == null || text == null) {
                Log.w(TAG, "输入文本失败：节点或文本为空");
                return false;
            }
            
            // 创建Bundle并设置文本参数
            android.os.Bundle arguments = new android.os.Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            
            // 执行输入文本操作
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        } catch (Exception e) {
            Log.e(TAG, "输入文本失败", e);
            return false;
        }
    }



    /**
     * 返回操作，模拟系统的返回按键
     * @return 是否成功
     */
    public static boolean GoBack() {
        try {
            //检查无障碍服务
            if (instance == null) {
                Log.e(TAG, "无障碍服务未初始化");
                return false;
            }
            
            //使用系统级全局操作执行返回
            boolean success = instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            
            if (success) {
                Log.d(TAG, "返回操作成功");
                return true;
            } else {
                Log.w(TAG, "返回操作失败，尝试使用按键事件");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "执行返回操作失败", e);
            return false;
        }
    }



    /**
     * 获取当前页面所有节点
     * @return 节点列表（原生无障碍节点信息）
     */
    public static List<AccessibilityNodeInfo> GetAllNodes() {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        try {
            //检查无障碍服务
            if (instance == null) {
                Log.e(TAG, "无障碍服务未初始化");
                return nodes;
            }
            
            AccessibilityNodeInfo root = instance.getRootInActiveWindow();
            if (root == null) {
                Log.e(TAG, "无法获取根节点");
                return nodes;
            }
            
            // 使用栈进行非递归遍历
            java.util.Stack<AccessibilityNodeInfo> stack = new java.util.Stack<>();
            stack.push(root);
            
            while (!stack.isEmpty()) {
                AccessibilityNodeInfo node = stack.pop();
                if (node != null) {
                    // 添加当前节点
                    nodes.add(AccessibilityNodeInfo.obtain(node));
                    
                    // 将子节点压入栈
                    int childCount = node.getChildCount();
                    for (int i = childCount - 1; i >= 0; i--) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            stack.push(child);
                        }
                    }
                }
            }
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "获取控件列表失败", e);
        }
        return nodes;
    }
  

    /**
     * 根据viewId查找节点列表
     * @param nodes 节点列表
     * @param viewId 目标viewId
     * @return 找到的节点列表，如果没有找到返回空列表
     */
    public static List<AccessibilityNodeInfo> FindAllNodesByViewId(List<AccessibilityNodeInfo> nodes, String viewId) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (nodes == null || viewId == null) return result;
        
        for (AccessibilityNodeInfo node : nodes) {
            if (node == null) continue;
            
            String nodeViewId = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                try {
                    nodeViewId = node.getViewIdResourceName();
                } catch (Exception ignore) {
                }
            }
            
            if (viewId.equals(nodeViewId)) {
                result.add(node);
            }
        }
        
        return result;
    }
    

    /**
     * 根据文本内容查找节点列表
     * @param nodes 节点列表
     * @param text 目标文本
     * @return 找到的节点列表，如果没有找到返回空列表
     */
    public static List<AccessibilityNodeInfo> FindAllNodesByText(List<AccessibilityNodeInfo> nodes, String text) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (nodes == null || text == null) return result;
        
        for (AccessibilityNodeInfo element : nodes) {
            if (element == null) continue;
            
            CharSequence nodeText = element.getText();
            if (nodeText != null && text.equals(nodeText.toString())) {
                result.add(element);
            }
            // 也检查contentDescription
            CharSequence desc = element.getContentDescription();
            if (desc != null && text.equals(desc.toString())) {
                result.add(element);
            }
        }
        return result;
    }
    

    /**
     * 根据类名查找节点列表
     * @param nodes 节点列表
     * @param className 目标类名（支持部分匹配）
     * @return 找到的节点列表
     */
    public static List<AccessibilityNodeInfo> FindAllNodesByClassName(List<AccessibilityNodeInfo> nodes, String className) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (nodes == null || className == null) return result;
        
        for (AccessibilityNodeInfo element : nodes) {
            if (element == null) continue;
            
            CharSequence elementClassName = element.getClassName();
            if (elementClassName != null && elementClassName.toString().contains(className)) {
                result.add(element);
            }
        }
        
        return result;
    }


    /**
     * 批量回收AccessibilityNodeInfo列表中的所有节点
     * @param nodes 要回收的节点列表
     */
    public static void recycleNodes(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) return;
        
        for (AccessibilityNodeInfo element : nodes) {
            try {
                if (element != null) {
                    element.recycle();
                }
            } catch (Exception e) {
                Log.w(TAG, "回收节点时发生异常", e);
            }
        }
        nodes.clear();
    }





}
