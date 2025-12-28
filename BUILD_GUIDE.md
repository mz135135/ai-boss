# 编译指南

本文档介绍如何使用一键编译脚本快速构建 AI Boss 应用。

## 📝 前提条件

- macOS 或 Linux 系统
- 已安装 JDK 17
- 已安装 Android SDK（如使用 install 命令）

## 🚀 快速开始

### 1. 基本用法

```bash
# 编译 Debug 版本（最常用）
./build.sh

# 或明确指定
./build.sh debug
```

### 2. 所有命令

```bash
# 显示帮助信息
./build.sh help

# 编译 Debug 版本
./build.sh debug

# 编译 Release 版本
./build.sh release

# 编译并直接安装到连接的设备
./build.sh install

# 清理构建缓存
./build.sh clean

# 运行单元测试
./build.sh test

# 运行代码检查（Lint）
./build.sh lint

# 完整构建流程（清理+测试+Lint+编译）
./build.sh all
```

## 📦 构建产物位置

编译成功后，APK 文件位于：

- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release.apk`

## 🔧 常见场景

### 场景 1: 快速开发测试

```bash
# 编译并安装到手机
./build.sh install

# 运行后查看日志
adb logcat | grep "AIAutomation"
```

### 场景 2: 发布准备

```bash
# 完整构建流程
./build.sh all

# 单独构建 Release
./build.sh release
```

### 场景 3: 调试问题

```bash
# 清理后重新构建
./build.sh clean
./build.sh debug

# 运行测试查看报告
./build.sh test
# 会自动打开测试报告（macOS）
```

### 场景 4: 代码质量检查

```bash
# 运行 Lint 检查
./build.sh lint
# 会自动打开 Lint 报告（macOS）
```

## 🎨 输出示例

脚本使用彩色输出，便于识别：

- 🔵 **[INFO]** - 信息提示
- 🟢 **[SUCCESS]** - 成功消息
- 🟡 **[WARNING]** - 警告信息
- 🔴 **[ERROR]** - 错误消息

## ⚙️ 自动检查

脚本会自动检查：

1. ✅ 是否存在 `api.properties` 文件
2. ✅ gradlew 是否存在
3. ✅ gradlew 是否有执行权限
4. ✅ （install 模式）是否连接了 Android 设备

## 💡 提示

### 首次运行

如果是首次运行，脚本会自动创建 `api.properties`：

```bash
./build.sh
# 提示: 请编辑 api.properties 填入你的 API Key
# 然后: vim api.properties
```

### 多设备安装

如果连接了多台设备，install 命令会显示设备数量并安装到所有设备：

```bash
./build.sh install
# 检测到 2 台设备
# 应用安装成功！
```

### 测试报告

运行 `./build.sh test` 后，脚本会：
1. 执行所有单元测试
2. 生成 HTML 测试报告
3. 在 macOS 上自动打开报告

### Lint 报告

运行 `./build.sh lint` 后，脚本会：
1. 执行代码质量检查
2. 生成 HTML Lint 报告
3. 在 macOS 上自动打开报告

## ⚠️ 注意事项

1. **Release 构建**需要签名配置（`keystore.properties`），否则生成未签名 APK
2. **install 命令**需要先启用设备的 USB 调试
3. 脚本使用 `set -e`，遇到错误会自动终止

## 🐛 故障排除

### 问题：权限被拒绝

```bash
# 解决方案
chmod +x build.sh
./build.sh
```

### 问题：找不到 adb

```bash
# macOS - 安装 Android Platform Tools
brew install --cask android-platform-tools

# 或添加到 PATH
export PATH=$PATH:~/Library/Android/sdk/platform-tools
```

### 问题：Gradle 构建失败

```bash
# 清理后重试
./build.sh clean
./build.sh debug
```

### 问题：设备未检测到

```bash
# 检查设备连接
adb devices

# 重启 adb 服务
adb kill-server
adb start-server
```

## 📚 相关文档

- [项目使用文档](项目使用文档.md) - 应用使用指南
- [RELEASE.md](RELEASE.md) - Release 构建和签名
- [CONTRIBUTING.md](CONTRIBUTING.md) - 贡献指南
- [README.md](README.md) - 项目介绍

## 🔗 手动构建

如果你更喜欢手动构建：

```bash
# 清理
./gradlew clean

# 编译 Debug
./gradlew assembleDebug

# 编译 Release
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug

# 运行测试
./gradlew test

# 运行 Lint
./gradlew lint
```

---

💡 **推荐工作流**：开发时使用 `./build.sh install`，发布前使用 `./build.sh all`
