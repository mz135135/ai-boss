# Release 构建指南

本文档说明如何创建和发布 AI Boss 的 Release 版本。

## 生成签名文件

### 1. 创建 Keystore

使用以下命令生成签名文件：

```bash
keytool -genkey -v -keystore release.keystore -alias ai-boss -keyalg RSA -keysize 2048 -validity 10000
```

命令说明：
- `-keystore release.keystore` - 生成的文件名
- `-alias ai-boss` - 密钥别名
- `-validity 10000` - 有效期 10000 天（约 27 年）

### 2. 填写信息

运行命令后，系统会提示你输入：
- Keystore 密码（至少 6 个字符）
- 密钥密码（可以与 Keystore 密码相同）
- 姓名、组织等信息

### 3. 配置签名

1. 复制配置模板：
   ```bash
   cp keystore.properties.example keystore.properties
   ```

2. 编辑 `keystore.properties`，填入实际信息：
   ```properties
   storeFile=release.keystore
   storePassword=你的keystore密码
   keyAlias=ai-boss
   keyPassword=你的key密码
   ```

⚠️ **安全提示**：
- `release.keystore` 和 `keystore.properties` 已在 `.gitignore` 中，不会被提交到 Git
- 请妥善保管这两个文件，丢失后无法更新应用

## 构建 Release APK

配置完成后，运行：

```bash
./gradlew assembleRelease
```

生成的 APK 位于：
```
app/build/outputs/apk/release/app-release.apk
```

## 构建特性

Release 版本包含以下优化：
- ✅ 代码混淆（ProGuard）
- ✅ 资源压缩
- ✅ 移除调试日志
- ✅ APK 签名

## 版本发布

1. **测试 Release 版本**
   ```bash
   ./gradlew installRelease
   ```

2. **生成签名的 APK**
   ```bash
   ./gradlew bundleRelease  # 生成 AAB（推荐用于 Google Play）
   ./gradlew assembleRelease  # 生成 APK
   ```

3. **检查 APK 签名**
   ```bash
   jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
   ```

4. **发布到 GitHub Releases**
   - 在 GitHub 仓库创建新的 Release
   - 上传 `app-release.apk`
   - 填写版本说明

## 版本号管理

在 `app/build.gradle.kts` 中更新版本：

```kotlin
defaultConfig {
    versionCode = 1     // 每次发布递增（用于 Google Play 版本控制）
    versionName = "1.0" // 显示给用户的版本号
}
```

版本号规则：
- `versionCode`: 整数，必须递增
- `versionName`: 字符串，建议使用语义化版本（如 1.0.0）

## 故障排除

### 问题：构建失败 "Keystore file not found"

**解决方案**：确保 `keystore.properties` 和 `release.keystore` 文件存在且路径正确。

### 问题：签名验证失败

**解决方案**：检查密码是否正确，或重新生成 keystore。

### 问题：无法覆盖安装

**解决方案**：Debug 和 Release 版本使用不同的签名，需要先卸载旧版本。

## 备份建议

建议将签名文件备份到安全的地方：
- ✅ 加密的云存储
- ✅ 离线硬盘
- ❌ 不要提交到 Git
- ❌ 不要放在公开位置
