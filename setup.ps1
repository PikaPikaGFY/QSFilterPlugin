# QSFilterPlugin Gradle Wrapper 安装脚本
# 运行前提: 已安装 JDK 21+
#
# 用法:
#   1. 安装 Gradle: choco install gradle  (或从 https://gradle.org/install/ 下载)
#   2. 或者: 手动复制 gradle-wrapper.jar 到 gradle/wrapper/ 目录
#   3. 将 JDK 21 的 bin 目录加入 PATH
#
# 然后运行:
#   .\gradlew.bat build

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "  QSFilterPlugin - 构建指南" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Java
try {
    $javaVersion = & java -version 2>&1 | Select-String "version" | ForEach-Object { $_ -replace '.*version "([^"]+)".*', '$1' }
    Write-Host "[OK] Java 版本: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] 未找到 Java！请安装 JDK 21" -ForegroundColor Red
    Write-Host "  下载地址: https://adoptium.net/download/" -ForegroundColor Yellow
    exit 1
}

# 检查 Gradle
try {
    $gradleVersion = & gradle --version 2>&1 | Select-String "Gradle " | Select-Object -First 1
    Write-Host "[OK] $gradleVersion" -ForegroundColor Green

    # 生成 Wrapper
    Write-Host ""
    Write-Host "正在生成 Gradle Wrapper..." -ForegroundColor Yellow
    & gradle wrapper --gradle-version 8.10
    Write-Host "[OK] Gradle Wrapper 生成完成" -ForegroundColor Green
    Write-Host ""
    Write-Host "运行以下命令构建插件:" -ForegroundColor Cyan
    Write-Host "  .\gradlew.bat build" -ForegroundColor White
    Write-Host ""
    Write-Host "构建产物在: build\libs\QSFilterPlugin-1.0.0-SNAPSHOT.jar" -ForegroundColor Yellow
} catch {
    Write-Host "[WARN] Gradle 未安装" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "请选择以下方式之一安装 Gradle:" -ForegroundColor Cyan
    Write-Host "  方式1 (推荐): choco install gradle" -ForegroundColor White
    Write-Host "  方式2: 下载 https://gradle.org/install/" -ForegroundColor White
    Write-Host "  方式3: 使用 sdkman (需先安装): sdk install gradle 8.10" -ForegroundColor White
    Write-Host ""
    Write-Host "安装后重新运行本脚本即可。" -ForegroundColor Yellow
}
