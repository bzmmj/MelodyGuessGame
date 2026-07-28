@echo off
REM 美乐蒂猜猜乐 - 一键推送到 GitHub（方案B 云端构建）
cd /d "%~dp0"
echo ============================================
echo   美乐蒂猜猜乐 - 推送到 GitHub
echo ============================================
set /p REPO_URL=请粘贴你的 GitHub 仓库 HTTPS 地址（形如 https://github.com/用户名/仓库名.git）:
git remote remove origin 2>nul
git remote add origin %REPO_URL%
git push -u origin main
echo.
echo ============================================
echo   如果提示输入密码，请填写 GitHub Personal Access Token（不是账号密码）
echo   没有 Token？去 GitHub -> Settings -> Developer settings -> Personal access tokens 创建一个（勾 repo 权限）
echo ============================================
pause
