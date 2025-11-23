# Atmos

本项目是一个本地部署 FastVLM 视觉语言模型的 Demo，包含 iOS/macOS 服务端和 Android 客户端。

## 项目结构

- **Server**: Swift 编写的服务端，集成 FastVLM 和 YOLO 目标检测
- **Atmos**: Android 客户端应用

## FastVLM 模型部署

本项目支持三种不同规模的 FastVLM 模型，可根据设备性能和需求选择：

### 模型选项

| 模型规模 | 量化方式 | 下载链接 |
|---------|---------|---------|
| 0.5B | FP16 | [下载](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_0.5b_stage3_llm.fp16.zip) |
| 1.5B | INT8 | [下载](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_1.5b_stage3_llm.int8.zip) |
| 7B | INT4 | [下载](https://ml-site.cdn-apple.com/datasets/fastvlm/llava-fastvithd_7b_stage3_llm.int4.zip) |

### 模型部署步骤

1. 根据需要下载上述模型之一
2. 解压下载的 zip 文件
3. 将解压后的文件夹重命名为 `model`
4. 将 `model` 文件夹放置在 `Server/Server/Module/FastVLM/` 目录下

最终目录结构应为：
```
Server/Server/Module/FastVLM/model/
```

## 服务端部署

### 环境要求

- macOS 或 iOS 设备
- Xcode
- Swift 5.0+

### 运行步骤

1. 按照上述步骤部署 FastVLM 模型
2. 打开 `Server/Server.xcodeproj`
3. 在 Xcode 中构建并运行项目

## Android 客户端

### 环境要求

- Android Studio
- Gradle

### 运行步骤

1. 打开 `Atmos` 文件夹
2. 使用 Android Studio 打开项目
3. 同步 Gradle 依赖
4. 构建并运行应用

## 功能特性

- 本地部署的视觉语言模型推理
- YOLO 目标检测集成
- 跨平台支持（iOS/macOS 服务端 + Android 客户端）