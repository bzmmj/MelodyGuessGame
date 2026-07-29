@echo off
cd /d D:\MelodyGuessGame
"C:\Users\ZhuanZ\.workbuddy\vendor\PortableGit\mingw64\bin\git.exe" remote remove origin 2>nul
"C:\Users\ZhuanZ\.workbuddy\vendor\PortableGit\mingw64\bin\git.exe" remote add origin https://github.com/bzmmj/MelodyGuessGame.git
"C:\Users\ZhuanZ\.workbuddy\vendor\PortableGit\mingw64\bin\git.exe" push -u origin main --force
pause
