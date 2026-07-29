@echo off
chcp 65001 >nul
cd /d "D:\MelodyGuessGame"
set "GIT=C:\Users\ZhuanZ\.workbuddy\vendor\PortableGit\mingw64\bin\git.exe"
echo ============================================
echo   MelodyGuessGame - Push to GitHub
echo ============================================
echo.
%GIT% remote remove origin 2>nul
%GIT% remote add origin https://github.com/bzmmj/MelodyGuessGame.git
%GIT% push -u origin main
echo.
echo ============================================
echo   Done! Check https://github.com/bzmmj/MelodyGuessGame/actions
echo ============================================
pause
