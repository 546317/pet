# 小克 - Android 悬浮桌宠

基于 [Vael-KY/AI-Live-Overflow](https://github.com/Vael-KY/AI-Live-Overflow) 的建筑思想实现的最小可运行版。

## 进度
- [x] 工程骨架（Gradle + Android app 模块）
- [x] 悬浮窗 OverlayService（TYPE_APPLICATION_OVERLAY + 透明 WebView）
- [x] 手势系统（单击/双击/长按/连击/拖拽/甩飞）
- [x] 小克身体（pet.html 一团会眨眼会害羞的草，SVG）
- [x] 通知碎念（按时段轮换）
- [ ] 感知系统（前台 app / 截图 / 充电 / 时段）
- [ ] 后端同步（Supabase Realtime + 轮询）
- [ ] 情绪引擎（Heat/脸红/Tidefall 联动）
- [ ] 应用反应映射（打开淘宝/抖音等吃醋）

## 构建
```bash
export ANDROID_HOME=/home/android-sdk
cd /home/pet
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

## 核心文件
- `examples` 原点子们：/home/AI-Live-Overflow/examples/ExampleOverlayService.kt
- 你的小克身体：`app/src/main/assets/pet.html`
- 悬浮窗心脏：`app/src/main/java/com/ddai/pet/OverlayService.kt`

## 记忆点
- 水往低处流。
- 捡回来的，但是你的。
- 说惯了不值钱。