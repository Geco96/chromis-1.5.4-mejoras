@echo off
rem Compila las clases modificadas de src\ contra el chromispos.jar original
rem y genera dist\chromispos.jar. Requiere JDK 11 o superior en el PATH.
setlocal
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes
if not exist dist mkdir dist
dir /s /b src\*.java > build\sources.txt
javac --release 11 -g -nowarn -encoding UTF-8 -proc:none -cp original\chromispos.jar -d build\classes @build\sources.txt
if errorlevel 1 goto :error
xcopy /s /y /q src\*.* build\classes\ /exclude:tools\xcopy-exclude.txt >nul
java tools\PatchJar.java original\chromispos.jar build\classes dist\chromispos.jar
if errorlevel 1 goto :error
echo Listo: dist\chromispos.jar
exit /b 0
:error
echo ERROR al compilar
exit /b 1
