package com.app.pldscript;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ScriptsFragment extends Fragment {
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scripts, container, false);
        
        TextView textView = view.findViewById(R.id.text_scripts);
        textView.setText("脚本管理页面\n\n这里可以管理您的自动化脚本");
        
        return view;
    }
}

