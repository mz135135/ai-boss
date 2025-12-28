# AI Boss - Intelligent Android Automation Assistant

<div align="center">

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-purple.svg)](https://kotlinlang.org)

[中文文档](项目使用文档.md) | [English](README.md)

An intelligent Android automation assistant powered by Doubao AI, featuring offline voice recognition and natural language task automation.

</div>

## 📱 Overview

AI Boss is an Android application that combines AI intelligence with accessibility services to automate tasks on your device. Simply describe what you want to do in natural language (text or voice), and the AI will analyze the screen and execute the appropriate actions.

### ✨ Key Features

- 🎤 **Offline Voice Recognition** - Built-in Vosk engine for Chinese speech-to-text, no internet required
- 💬 **Chat Interface** - Describe tasks in natural language through an intuitive chat interface
- 🤖 **Smart Automation** - AI analyzes screen content and automatically performs clicks, inputs, swipes, etc.
- 📝 **Chat History** - Conversation history is automatically saved and persists across app restarts
- 🎯 **Task Completion Notifications** - Get notified when tasks are complete with copyable results
- 🔄 **Quick Retry** - Tap the refresh button on any message to retry that task
- 🎨 **Modern UI** - Built with Material Design 3 and Jetpack Compose

## 🚀 Quick Start

### Prerequisites

- Android 8.0 (API 26) or higher
- ~50MB free storage for the app and voice model

### Installation

1. **Download APK**
   ```bash
   # Build from source
   ./gradlew assembleDebug
   
   # Install to device
   ./gradlew installDebug
   ```
   Or download the latest APK from [Releases](https://github.com/mz135135/ai-boss/releases)

2. **Grant Permissions**
   - **Microphone** - Required for voice input
   - **Accessibility Service** - Required for automation
     - Go to Settings → Accessibility → Enable "AI Boss"
   - **Overlay Permission** - Required for floating control window (Android 6.0+)

3. **Configure API Key**
   - Copy `api.properties.example` to `api.properties`
   - Fill in your Doubao AI API credentials:
     ```properties
     DOUBAO_API_KEY=your_api_key_here
     DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
     DOUBAO_MODEL_ID=doubao-seed-1-6-flash-250828
     ```

### Usage

1. Open the app and tap the input field at the bottom
2. Type or speak your task, for example:
   - "Open Taobao and search for phones"
   - "Check today's weather"
   - "Like the first 5 videos on Douyin"
3. Tap send, and the AI will automatically execute the task

#### Voice Input

**Method 1: Press and Hold (Recommended)**
1. Press and hold the microphone icon 🎤
2. Start speaking
3. Release to stop and fill in the text

**Method 2: Tap to Record**
1. Tap the microphone icon to start recording
2. Speak while seeing real-time recognition results
3. Tap again to stop

> 💡 First launch will load the voice model (~3-5 seconds). You'll see "Voice recognition ready" when complete.

## 🏗️ Technical Architecture

### Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material Design 3
- **Database**: Room + SharedPreferences
- **Networking**: OkHttp + Gson
- **Async**: Kotlin Coroutines + Flow
- **Voice Recognition**: Vosk (offline Chinese model)
- **AI Engine**: Doubao Context API
- **System Services**: Accessibility Service
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

### Core Modules

#### 1. AI Client (`ai/DoubaoApiClient.kt`)
- Encapsulates Doubao Context API calls
- Supports context-aware conversations
- Automatic API authentication and error handling

#### 2. Voice Recognition (`voice/VoiceRecognizer.kt`)
- Vosk offline speech recognition engine
- Real-time speech-to-text
- Automatic Chinese space removal
- Press-and-hold or tap-to-record modes

#### 3. Accessibility Service (`service/MyAccessibilityService.kt`)
- Screen content capture and parsing
- Element finding and manipulation
- Supports click, input, and gesture operations

#### 4. Task Manager (`automation/TaskManager.kt`)
- AI-driven automation execution engine
- Intelligent decision-making system
- Action parsing and execution
- Progress callbacks and state management

### Project Structure

```
AIAutomation/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/aiautomation/
│   │   │   │   ├── ai/              # AI client
│   │   │   │   ├── automation/      # Automation engine
│   │   │   │   ├── data/model/      # Data models
│   │   │   │   ├── service/         # System services
│   │   │   │   ├── settings/        # App settings
│   │   │   │   ├── ui/              # UI components
│   │   │   │   └── voice/           # Voice recognition
│   │   │   ├── res/                 # Resources
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                    # Unit tests
│   │   └── androidTest/             # Instrumented tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── api.properties.example           # API config template
├── .gitignore
├── README.md
└── 项目使用文档.md                   # Chinese documentation
```

## 🔧 Development

### Building

```bash
# Clean project
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Install to device
./gradlew installDebug
```

### Dependencies

Key dependencies include:
- Jetpack Compose for UI
- Vosk for offline speech recognition
- OkHttp for networking
- Room for local database
- EasyFloat for floating windows

See `app/build.gradle.kts` for complete dependency list.

### Voice Model

The app uses `vosk-model-small-cn-0.22` (42MB) for Chinese recognition. For higher accuracy:
1. Download `vosk-model-cn-0.22.zip` (255MB) from [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models)
2. Extract and rename to `model-cn`
3. Replace `app/src/main/assets/model-cn`
4. Rebuild the app

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ❓ FAQ

**Q: Why is the first launch slow?**  
A: The voice model files (42MB) need to be extracted from assets on first run. Subsequent launches will be much faster.

**Q: How to improve voice recognition accuracy?**  
A: Keep a quiet environment, speak clearly at moderate speed, keep phone 20-30cm from mouth, or upgrade to the larger model.

**Q: Why is Accessibility Service required?**  
A: The app needs accessibility permissions to read screen content and perform automated operations, which is an Android system requirement for automation apps.

**Q: What if a task fails?**  
A: Check that accessibility permission is enabled, ensure network connectivity is stable, try describing the task more clearly, or check the floating window for AI reasoning process.

## 🔗 Resources

- [Doubao AI Documentation](https://www.volcengine.com/docs/82379)
- [Vosk Speech Recognition](https://alphacephei.com/vosk/)
- [Android Accessibility Guide](https://developer.android.com/guide/topics/ui/accessibility)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)

## 📊 Status

**Version**: 1.0.0  
**Build Status**: ✅ Active Development  
**APK Size**: ~15 MB

---

Made with ❤️ by the AI Boss team
