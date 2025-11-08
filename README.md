# PleaseLieDownScriptAndroid

一个基于 Android 无障碍服务的自动化脚本工具，提供悬浮窗控制、控件树查看和脚本执行功能。

## 📱 功能特性

### 核心功能
- **无障碍服务支持**：利用 Android AccessibilityService 实现自动化操作
- **悬浮窗控制**：提供可拖动的悬浮气泡和菜单，方便快速操作
- **控件树查看器**：可视化查看屏幕上的 UI 控件，支持：
  - 实时标注所有控件的边界框
  - 点击控件查看详细信息（ID、类名、层级、属性等）
  - 查看完整的控件树结构
  - 使用 Android 原生属性名称显示控件信息

### 界面功能
- **底部导航**：包含首页、脚本、设置三个主要模块
- **主界面**：提供启动悬浮窗的入口
- **脚本管理**：管理和执行自动化脚本
- **设置界面**：应用配置和选项

## 🛠️ 技术栈

- **开发语言**：Java
- **最低 SDK 版本**：API 25 (Android 7.1)
- **目标 SDK 版本**：API 36 (Android 14)
- **编译 SDK 版本**：API 36
- **主要框架**：
  - AndroidX AppCompat
  - Material Design Components
  - AndroidX Activity
  - ConstraintLayout

## 📋 系统要求

- Android 7.1 (API 25) 或更高版本
- 需要授予以下权限：
  - **悬浮窗权限**：用于显示悬浮气泡和菜单
  - **无障碍服务权限**：用于访问和控制其他应用的 UI

## 🚀 安装和使用

### 安装步骤

1. 克隆或下载本项目
2. 使用 Android Studio 打开项目
3. 连接 Android 设备或启动模拟器
4. 编译并安装应用

### 使用说明

1. **启动应用**
   - 打开应用后，在主界面点击"启动"按钮
   - 首次使用需要授予悬浮窗权限和无障碍服务权限

2. **启用无障碍服务**
   - 进入系统设置 → 无障碍 → 找到 "PLDScript"
   - 开启无障碍服务

3. **使用悬浮窗**
   - 启动后会在屏幕上显示一个悬浮气泡
   - 点击气泡可以打开菜单
   - 菜单中包含启动/停止、脚本管理、控件查看等功能

4. **控件树查看功能**
   - 在悬浮窗菜单中点击"开启控件查看"
   - 屏幕上会显示所有控件的边界框
   - 点击任意控件可以查看详细信息
   - 点击"退出查看"或按返回键可以退出查看模式

## 📁 项目结构

```
app/src/main/java/com/app/pldscript/
├── MainActivity.java          # 主活动，包含底部导航
├── HomeFragment.java           # 首页 Fragment
├── ScriptsFragment.java        # 脚本管理 Fragment
├── SettingsFragment.java       # 设置 Fragment
├── PLDScript.java              # 无障碍服务主类
├── FloatWindow.java            # 悬浮窗管理类
└── ViewTreeOverlay.java        # 控件树查看器

app/src/main/java/com/main/script/
└── MainScript.java             # 主脚本类
```

## 🔧 开发说明

### 编译项目

```bash
# 使用 Gradle 编译
./gradlew assembleDebug

# 或使用 Android Studio 直接运行
```

### 主要类说明

- **PLDScript**：继承自 `AccessibilityService`，处理无障碍服务相关逻辑，监听按键事件
- **FloatWindow**：管理悬浮气泡和菜单的显示、隐藏和交互
- **ViewTreeOverlay**：实现控件树的可视化显示，包括边界框绘制、控件信息展示等

### 权限配置

应用需要以下权限（已在 AndroidManifest.xml 中声明）：
- `SYSTEM_ALERT_WINDOW`：显示悬浮窗
- `BIND_ACCESSIBILITY_SERVICE`：绑定无障碍服务

## 📝 更新日志

### v1.0 (2025-01-XX)
- 初始版本发布
- 实现基础的无障碍服务功能
- 实现悬浮窗控制
- 实现控件树查看器
- 支持控件信息详细查看
- 支持控件树结构查看

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用开源许可证，具体许可证信息请查看 LICENSE 文件。

## ⚠️ 注意事项

- 使用无障碍服务需要用户手动在系统设置中开启
- 悬浮窗权限需要在系统设置中手动授予
- 本工具仅供学习和研究使用，请勿用于非法用途
- 使用自动化功能时请遵守相关法律法规和应用服务条款

## 📞 联系方式

如有问题或建议，请通过 GitHub Issues 反馈。

---

**请躺下脚本 Android 版** - 让自动化更简单
