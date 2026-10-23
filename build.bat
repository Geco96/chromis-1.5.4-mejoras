@echo off
rem Compila las clases modificadas y genera:
rem   dist\chromispos.jar  (src\ + src-common\ sobre original\chromispos.jar)
rem   dist\admin.jar       (src-admin\ + src-common\ sobre original-admin\admin.jar)
rem   dist\restaurar.jar   (restaurador de respaldos, se abre con doble clic)
rem Requiere JDK 11 o superior en el PATH.
setlocal enabledelayedexpansion
cd /d "%~dp0"
if not exist dist mkdir dist

set JAVAFX=
for %%f in (tools\javafx-11.0.2\*.jar) do set JAVAFX=!JAVAFX!;%%f

call :build chromispos original\chromispos.jar "" src src-common || goto :error
call :build admin original-admin\admin.jar "%JAVAFX%" src-admin src-common || goto :error
java tools\RestoreJar.java build\admin\classes original\chromispos.jar original-admin\admin.jar dist\restaurar.jar || goto :error
echo Listo: dist\chromispos.jar, dist\admin.jar y dist\restaurar.jar
exit /b 0

:build
set NAME=%~1
set ORIGINAL=%~2
set EXTRA=%~3
set OUT=build\%NAME%
if exist %OUT% rmdir /s /q %OUT%
mkdir %OUT%\classes
if exist %OUT%\sources.txt del %OUT%\sources.txt
for %%d in (%4 %5) do (
    for /r %%d %%f in (*.java) do echo %%f>> %OUT%\sources.txt
)
javac --release 11 -g -nowarn -encoding UTF-8 -proc:none -cp "%ORIGINAL%;%EXTRA%" -d %OUT%\classes @%OUT%\sources.txt
if errorlevel 1 exit /b 1
for %%d in (%4 %5) do xcopy /s /y /q %%d\*.* %OUT%\classes\ /exclude:tools\xcopy-exclude.txt >nul
java tools\PatchJar.java %ORIGINAL% %OUT%\classes dist\%NAME%.jar
if errorlevel 1 exit /b 1
exit /b 0

:error
echo ERROR al compilar
exit /b 1
