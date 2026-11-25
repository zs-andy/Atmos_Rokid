<div align="center">

# Atmos — Rokid Glasses

**Real-time ambient perception system for visually impaired users on Rokid Glasses, powered by on-device Apple FastVLM, YOLO, and a Swift Vapor inference server.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Client](https://img.shields.io/badge/Client-Kotlin%20%7C%20Android%20%2F%20Rokid-7F52FF.svg)](#architecture)
[![Server](https://img.shields.io/badge/Server-Swift%20%7C%20Vapor%20%7C%20MLX-F05138.svg)](#architecture)
[![Models](https://img.shields.io/badge/Models-FastVLM%20%2B%20YOLO-10B981.svg)](#fastvlm-model-setup)

**English** | [简体中文](README.zh-CN.md)

</div>

---

## Why Atmos?

Smart glasses can act as a quiet pair of eyes for people with visual impairments — but cloud-only vision pipelines introduce latency, connectivity dependence, and privacy concerns.

**Atmos** pairs an Android/Rokid Glasses camera client with a **local Apple Silicon inference server** over LAN, combining two complementary perception loops:

1. **Fast spatial detection loop (~5 FPS / every 0.2s)**: Streams compressed camera frames to `POST /yolo` for real-time object detection (`YOLODetector`) and spatial overlay/calibration.
2. **Ambient scene narration loop (every 5s)**: Sends key frames to `POST /detect` where local **Apple FastVLM** (`MLXLMCommon`) generates natural-language scene descriptions paired with spoken audio (`base64` MP3 played directly on the glasses).

## Architecture

```text
┌─────────────────────────────────────┐         LAN HTTP (:8080)         ┌──────────────────────────────────────────┐
│   Rokid Glasses / Android Client    │ ───────────────────────────────► │   macOS Menu Bar Server (Swift + Vapor)  │
│   (Atmos/ · Kotlin · Compose)       │                                  │   (Server/ · SwiftUI · MLXLMCommon)      │
│                                     │  POST /yolo   (every 0.2s)       │                                          │
│  • Camera2 capture & compression    │ ◄──── bounding boxes JSON ─────  │  • YOLODetector (real-time objects)      │
│  • DetectionOverlayView             │                                  │  • FastVLMModel (0.5B / 1.5B / 7B local) │
│  • CalibrationDialog                │  POST /detect (every 5.0s)       │  • Live frame preview & status menu bar  │
│  • MediaPlayer voice feedback       │ ◄──── { result, audio } ───────  │                                          │
└─────────────────────────────────────┘                                  └──────────────────────────────────────────┘
```

## Project Structure

```text
Atmos_Rokid/
├── Atmos/                          # Android / Rokid Glasses client (Kotlin, Jetpack Compose, minSdk 28)
│   └── app/src/main/java/com/example/atmos/
│       ├── MainActivity.kt         # Camera2 session & UI lifecycle
│       ├── network/
│       │   ├── YoloUploader.kt     # High-frequency frame upload to /yolo
│       │   └── FastVLMUploader.kt  # Periodic scene caption & audio playback from /detect
│       ├── detection/              # Detection models & overlay view
│       └── ui/CalibrationDialog.kt # Spatial alignment calibration
└── Server/                         # macOS SwiftUI MenuBarExtra + Vapor HTTP server (:8080)
    └── Server/
        ├── App/                    # ServerApp, AppState, live preview ContentView
        ├── Module/VaporServer.swift# /yolo and /detect HTTP endpoints
        ├── Module/FastVLM/         # Apple FastVLM MLX wrapper (place weights in model/)
        └── Module/YOLO/            # YOLODetector implementation
```

## FastVLM Model Setup

Download one of Apple's pre-quantized FastVLM checkpoints based on your Mac's memory and latency budget:

| Model Size | Precision | Download |
| --- | --- | --- |
| **0.5B** (fastest) | FP16 | [llava-fastvithd_0.5b_stage3_llm.fp16.zip](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_0.5b_stage3_llm.fp16.zip) |
| **1.5B** (balanced) | INT8 | [llava-fastvithd_1.5b_stage3_llm.int8.zip](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_1.5b_stage3_llm.int8.zip) |
| **7B** (richest) | INT4 | [llava-fastvithd_7b_stage3_llm.int4.zip](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_7b_stage3_llm.int4.zip) |

Unzip the archive, rename the extracted folder to `model`, and place it at:

```text
Server/Server/Module/FastVLM/model/
```

## Getting Started

### 1. Start the Local Inference Server (macOS)

- **Requirements**: Apple Silicon Mac, Xcode 15+, Swift 5.9+
- Open `Server/Server.xcodeproj` in Xcode and run the `Server` scheme.
- Once running, the menu bar icon (`Atmos Server`) shows green and listens on `0.0.0.0:8080`.

### 2. Run the Android / Rokid Glasses Client

- **Requirements**: Android Studio, JDK 11+, Android SDK (minSdk 28, compileSdk 36)
- Update the default server host IP in `Atmos/app/src/main/java/com/example/atmos/network/FastVLMUploader.kt` and `YoloUploader.kt` to match your Mac's LAN IP (`http://<your-mac-ip>:8080/...`).
- Open `Atmos/` in Android Studio, sync Gradle, and deploy to your Rokid Glasses or Android test device on the same Wi-Fi network.

## Roadmap & Ways to Contribute

This project started as a working hackathon prototype — contributions are very welcome! High-impact improvements include:

- **In-app server discovery / settings UI** (mDNS/Bonjour or editable IP preference instead of hardcoded defaults).
- **Streaming transport** (WebSocket / WebRTC frame transport to cut per-request HTTP overhead).
- **Spatial audio & hazard prioritization** (directional audio cues for approaching obstacles).
- **On-device TTS fallback** and multilingual voice prompts.

See [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

## License

Licensed under the [MIT License](LICENSE).
