#!/bin/bash

# =============================================================================
# AI Boss 一键编译脚本
# =============================================================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 打印分割线
print_separator() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# 显示帮助信息
show_help() {
    echo "AI Boss 一键编译脚本"
    echo ""
    echo "用法: ./build.sh [选项]"
    echo ""
    echo "选项:"
    echo "  debug       编译 Debug 版本（默认）"
    echo "  release     编译 Release 版本"
    echo "  install     编译并安装到设备"
    echo "  clean       清理构建缓存"
    echo "  test        运行单元测试"
    echo "  lint        运行代码检查"
    echo "  all         执行完整构建流程（清理+测试+编译）"
    echo "  help        显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  ./build.sh              # 编译 Debug 版本"
    echo "  ./build.sh release      # 编译 Release 版本"
    echo "  ./build.sh install      # 编译并安装到设备"
    echo "  ./build.sh all          # 完整构建流程"
}

# 检查环境
check_environment() {
    print_info "检查编译环境..."
    
    # 检查 api.properties
    if [ ! -f "api.properties" ]; then
        print_warning "未找到 api.properties 文件"
        print_info "正在从模板创建..."
        if [ -f "api.properties.example" ]; then
            cp api.properties.example api.properties
            print_warning "请编辑 api.properties 填入你的 API Key"
            print_info "vim api.properties"
            exit 1
        else
            print_error "未找到 api.properties.example 模板"
            exit 1
        fi
    fi
    
    # 检查 Gradle
    if [ ! -f "./gradlew" ]; then
        print_error "未找到 gradlew，请确保在项目根目录运行"
        exit 1
    fi
    
    # 设置 gradlew 执行权限
    chmod +x ./gradlew
    
    print_success "环境检查完成"
}

# 清理构建
clean_build() {
    print_separator
    print_info "清理构建缓存..."
    ./gradlew clean
    print_success "清理完成"
}

# 编译 Debug
build_debug() {
    print_separator
    print_info "开始编译 Debug 版本..."
    ./gradlew assembleDebug
    
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        print_success "Debug APK 编译成功！"
        print_info "文件路径: $APK_PATH"
        print_info "文件大小: $APK_SIZE"
    else
        print_error "APK 文件未生成"
        exit 1
    fi
}

# 编译 Release
build_release() {
    print_separator
    print_info "开始编译 Release 版本..."
    
    # 检查签名配置
    if [ ! -f "keystore.properties" ]; then
        print_warning "未找到 keystore.properties，将生成未签名的 Release APK"
        print_info "如需签名，请参考 RELEASE.md 配置签名"
    fi
    
    ./gradlew assembleRelease
    
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        print_success "Release APK 编译成功！"
        print_info "文件路径: $APK_PATH"
        print_info "文件大小: $APK_SIZE"
        
        # 检查签名
        if [ -f "keystore.properties" ]; then
            print_info "APK 已签名"
        else
            print_warning "APK 未签名，无法直接安装"
        fi
    else
        print_error "APK 文件未生成"
        exit 1
    fi
}

# 安装到设备
install_app() {
    print_separator
    print_info "编译并安装到设备..."
    
    # 检查设备连接
    if ! command -v adb &> /dev/null; then
        print_error "未找到 adb 命令，请安装 Android SDK Platform Tools"
        exit 1
    fi
    
    DEVICE_COUNT=$(adb devices | grep -v "List" | grep "device" | wc -l)
    if [ "$DEVICE_COUNT" -eq 0 ]; then
        print_error "未检测到 Android 设备"
        print_info "请连接设备并启用 USB 调试"
        exit 1
    fi
    
    print_info "检测到 $DEVICE_COUNT 台设备"
    ./gradlew installDebug
    print_success "应用安装成功！"
}

# 运行测试
run_tests() {
    print_separator
    print_info "运行单元测试..."
    ./gradlew test
    print_success "测试完成"
    
    # 查找测试报告
    REPORT_PATH="app/build/reports/tests/testDebugUnitTest/index.html"
    if [ -f "$REPORT_PATH" ]; then
        print_info "测试报告: $REPORT_PATH"
        
        # macOS 自动打开报告
        if [[ "$OSTYPE" == "darwin"* ]]; then
            print_info "正在打开测试报告..."
            open "$REPORT_PATH"
        fi
    fi
}

# 运行 Lint
run_lint() {
    print_separator
    print_info "运行代码检查..."
    ./gradlew lint
    print_success "Lint 检查完成"
    
    # 查找 Lint 报告
    REPORT_PATH="app/build/reports/lint-results-debug.html"
    if [ -f "$REPORT_PATH" ]; then
        print_info "Lint 报告: $REPORT_PATH"
        
        # macOS 自动打开报告
        if [[ "$OSTYPE" == "darwin"* ]]; then
            print_info "正在打开 Lint 报告..."
            open "$REPORT_PATH"
        fi
    fi
}

# 完整构建流程
build_all() {
    print_separator
    print_info "执行完整构建流程..."
    
    clean_build
    run_tests
    run_lint
    build_debug
    build_release
    
    print_separator
    print_success "✨ 所有任务完成！"
}

# 显示构建摘要
show_summary() {
    print_separator
    print_success "🎉 构建完成！"
    print_separator
    
    echo ""
    echo "📦 构建产物："
    
    # Debug APK
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        DEBUG_SIZE=$(du -h "app/build/outputs/apk/debug/app-debug.apk" | cut -f1)
        echo "  ✓ Debug APK:   app/build/outputs/apk/debug/app-debug.apk ($DEBUG_SIZE)"
    fi
    
    # Release APK
    if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
        RELEASE_SIZE=$(du -h "app/build/outputs/apk/release/app-release.apk" | cut -f1)
        echo "  ✓ Release APK: app/build/outputs/apk/release/app-release.apk ($RELEASE_SIZE)"
    fi
    
    echo ""
    echo "📱 安装到设备："
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "📚 更多信息："
    echo "  使用文档: 项目使用文档.md"
    echo "  发布指南: RELEASE.md"
    echo ""
}

# =============================================================================
# 主程序
# =============================================================================

# 显示标题
clear
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "           AI Boss - 一键编译脚本"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 检查参数
ACTION=${1:-debug}

case "$ACTION" in
    help|-h|--help)
        show_help
        exit 0
        ;;
    debug)
        check_environment
        build_debug
        show_summary
        ;;
    release)
        check_environment
        build_release
        show_summary
        ;;
    install)
        check_environment
        install_app
        ;;
    clean)
        clean_build
        ;;
    test)
        check_environment
        run_tests
        ;;
    lint)
        check_environment
        run_lint
        ;;
    all)
        check_environment
        build_all
        show_summary
        ;;
    *)
        print_error "未知选项: $ACTION"
        echo ""
        show_help
        exit 1
        ;;
esac

print_separator
print_success "✅ 完成"
