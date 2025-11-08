package com.app.pldscript;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    private Switch switchAccessibility;
    private Switch switchOverlay;
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        switchAccessibility = view.findViewById(R.id.switch_accessibility);
        switchOverlay = view.findViewById(R.id.switch_overlay);
        
        // 更新开关状态
        updateSwitchStates();
        
        // 无障碍服务开关
        switchAccessibility.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && PLDScript.getInstance() == null) {
                    // 打开无障碍服务设置
                    openAccessibilitySettings();
                }
            }
        });
        
        // 悬浮窗权限开关
        switchOverlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && !Settings.canDrawOverlays(getContext())) {
                    // 打开悬浮窗权限设置
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getContext().getPackageName()));
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                    Toast.makeText(getContext(),
                            "请在设置中开启\"显示在其他应用上层\"权限",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSwitchStates();
    }

    private void updateSwitchStates() {
        // 更新无障碍服务开关状态
        boolean accessibilityEnabled = PLDScript.getInstance() != null;
        switchAccessibility.setChecked(accessibilityEnabled);
        
        // 更新悬浮窗权限开关状态
        boolean overlayEnabled = Settings.canDrawOverlays(getContext());
        switchOverlay.setChecked(overlayEnabled);
    }

    private void openAccessibilitySettings() {
        try {
            Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            details.putExtra("android.intent.extra.accessibility_component_name",
                    new ComponentName(getContext(), PLDScript.class).flattenToString());
            details.setData(Uri.fromParts("package", getContext().getPackageName(), null));
            startActivity(details);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(fallback);
            Toast.makeText(getContext(),
                    "请在列表中找到本应用并开启无障碍服务",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            updateSwitchStates();
        }
    }
}

