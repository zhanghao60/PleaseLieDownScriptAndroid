package com.app.pldscript;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.main.script.MainScript;

public class HomeFragment extends Fragment {
    private ImageView btnStartScript;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        btnStartScript = view.findViewById(R.id.btn_start_script);
        btnStartScript.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MainScript.isRunning()) {
                    MainScript.stop();
                    Toast.makeText(getContext(), "脚本已停止", Toast.LENGTH_SHORT).show();
                } else {
                    // 检查悬浮窗权限
                    if (!android.provider.Settings.canDrawOverlays(getContext())) {
                        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
                        startActivity(intent);
                        Toast.makeText(getContext(), "请先开启悬浮窗权限", Toast.LENGTH_LONG).show();
                        return;
                    }
                    // 检查无障碍服务
                    if (PLDScript.getInstance() == null) {
                        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                        Toast.makeText(getContext(), "请先开启无障碍服务", Toast.LENGTH_LONG).show();
                        return;
                    }
                    // 显示悬浮窗
                    FloatWindow.show(getContext());
                    Toast.makeText(getContext(), "悬浮窗已显示", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateButtonState();
    }

    private void updateButtonState() {
        if (btnStartScript != null) {
            // 可以根据脚本运行状态更新按钮外观
            btnStartScript.setSelected(MainScript.isRunning());
        }
    }
}

