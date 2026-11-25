<div align="center">

# Atmos — Rokid Glasses

**面向视障人群的 Rokid Glasses 实时氛围感知系统 — 基于本地 Apple FastVLM、YOLO 与 Swift Vapor 推理服务。**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Client](https://img.shields.io/badge/Client-Kotlin%20%7C%20Android%20%2F%20Rokid-7F52FF.svg)](#系统架构)
[![Server](https://img.shields.io/badge/Server-Swift%20%7C%20Vapor%20%7C%20MLX-F05138.svg)](#系统架构)
[![Models](https://img.shields.io/badge/Models-FastVLM%20%2B%20YOLO-10B981.svg)](#fastvlm-模型部署)

[English](README.md) | **简体中文**

</div>

---

## 项目背景

智能眼镜可以为视障人群提供低负担的环境感知辅助，但纯云端视觉方案常面临延迟高、依赖外网以及隐私泄露等问题。

**Atmos** 将 Rokid Glasses / Android 摄像头端与局域网内的 **Apple Silicon 本地推理服务端** 结合，构建了双频协同感知链路：

1. **高频目标检测链路（每 0.2 秒）**：压缩画面发送至 `POST /yolo`，通过本地 `YOLODetector` 实时识别视野物体并支持空间校准叠加。
2. **氛围与场景播报链路（每 5 秒）**：抽帧发送至 `POST /detect`，由本地运行的 **Apple FastVLM**（`MLXLMCommon`）生成自然语言环境描述并返回音频（`base64` MP3），眼镜端通过 `MediaPlayer` 直接播报。

## 系统架构

```text
┌─────────────────────────────────────┐         局域网 HTTP (:8080)      ┌──────────────────────────────────────────┐
│   Rokid Glasses / Android 客户端     │ ───────────────────────────────► │   macOS 菜单栏服务端 (Swift + Vapor)      │
│   (Atmos/ · Kotlin · Compose)       │                                  │   (Server/ · SwiftUI · MLXLMCommon)      │
│                                     │  POST /yolo   (每 0.2s)          │                                          │
│  • Camera2 采集与图像压缩            │ ◄──── 目标框 JSON ─────────────  │  • YOLODetector（实时目标检测）           │
│  • DetectionOverlayView 叠加层       │                                  │  • FastVLMModel（0.5B / 1.5B / 7B 本地）  │
│  • CalibrationDialog 视野校准        │  POST /detect (每 5.0s)          │  • 实时画面预览与菜单栏状态监控            │
│  • MediaPlayer 语音氛围播报          │ ◄──── { result, audio } ───────  │                                          │
└─────────────────────────────────────┘                                  └──────────────────────────────────────────┘
```

## 项目结构

```text
Atmos_Rokid/
├── Atmos/                          # Android / Rokid Glasses 客户端（Kotlin、Jetpack Compose、minSdk 28）
│   └── app/src/main/java/com/example/atmos/
│       ├── MainActivity.kt         # Camera2 会话与界面生命周期
│       ├── network/
│       │   ├── YoloUploader.kt     # 高频上传图像到 /yolo
│       │   └── FastVLMUploader.kt  # 定时上传图像到 /detect 并播放语音反馈
│       ├── detection/              # 检测结果模型与叠加层绘制
│       └── ui/CalibrationDialog.kt # 视野校准弹窗
└── Server/                         # macOS SwiftUI MenuBarExtra + Vapor HTTP 服务端（端口 8080）
    └── Server/
        ├── App/                    # ServerApp、AppState、实时画面 ContentView
        ├── Module/VaporServer.swift# /yolo 与 /detect 接口实现
        ├── Module/FastVLM/         # Apple FastVLM MLX 封装（权重置于 model/）
        └── Module/YOLO/            # YOLODetector 目标检测模块
```

## FastVLM 模型部署

根据本机内存与延迟需求选择并下载 Apple 官方 FastVLM 权重：

| 模型规模 | 量化方式 | 下载链接 |
| --- | --- | --- |
| **0.5B**（最快） | FP16 | [llava-fastvithd_0.5b_stage3_llm.fp16.zip](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_0.5b_stage3_llm.fp16.zip) |
| **1.5B**（均衡） | INT8 | [llava-fastvithd_1.5b_stage3_llm.int8.zip](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_1.5b_stage3_llm.int8.zip) |
| **7B**（细节最强） | INT4 | [llava-fastvithd_7b_stage3_llm.int4.zip](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_7b_stage3_llm.int4.zip) |

解压后将目录重命名为 `model`，放置于：

```text
Server/Server/Module/FastVLM/model/
```

## 快速开始

### 1. 启动本地推理服务端（macOS）

- **环境要求**：Apple Silicon Mac、Xcode 15+、Swift 5.9+
- 打开 `Server/Server.xcodeproj`，编译运行 `Server` scheme。
- 启动后菜单栏 `Atmos Server` 状态指示灯变绿，服务监听 `0.0.0.0:8080`。

### 2. 运行 Android / Rokid Glasses 客户端

- **环境要求**：Android Studio、JDK 11+、Android SDK（minSdk 28，compileSdk 36）
- 将 `Atmos/app/src/main/java/com/example/atmos/network/FastVLMUploader.kt` 与 `YoloUploader.kt` 中的默认服务端地址修改为你 Mac 的局域网 IP（`http://<your-mac-ip>:8080/...`）。
- 使用 Android Studio 打开 `Atmos/`，同步 Gradle 并在同一局域网下的 Rokid Glasses 或 Android 设备上运行。

## 后续方向与参与贡献

这是一个面向无障碍场景的可运行原型，非常欢迎社区一起完善：

- **动态服务发现与配置页**：通过 mDNS / Bonjour 或设置页配置服务端地址，替代代码内默认 IP。
- **低延迟流式传输**：用 WebSocket / WebRTC 替代轮询式 HTTP 上传。
- **空间音频与避障分级**：为近距离障碍物增加方向感提示音与优先级调度。
- **端侧 TTS 与多语种播报**：降低语音生成延迟并支持英文/粤语/普通话切换。

欢迎阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 并提交 PR！

## 开源协议

基于 [MIT License](LICENSE) 开源。
