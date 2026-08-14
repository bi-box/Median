@echo off
setlocal
set "ROOT=%~dp0"
set "WRAPPER_JAR=%ROOT%gradle\wrapper\gradle-wrapper.jar"
if exist "%WRAPPER_JAR%" (
  java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set "VERSION=8.13"
set "EXPECTED_SHA256=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "BOOTSTRAP=%GRADLE_USER_HOME%\wrapper\median-bootstrap"
set "ZIP=%BOOTSTRAP%\gradle-%VERSION%-bin.zip"
set "DIST=%BOOTSTRAP%\gradle-%VERSION%"
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BOOTSTRAP%" mkdir "%BOOTSTRAP%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip='%ZIP%'; if (!(Test-Path $zip)) { Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile ($zip+'.part'); Move-Item -Force ($zip+'.part') $zip }; $actual=(Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant(); if ($actual -ne '%EXPECTED_SHA256%') { Remove-Item -Force $zip; throw 'Gradle distribution checksum mismatch' }; if (Test-Path '%DIST%') { Remove-Item -Recurse -Force '%DIST%' }; Expand-Archive -Force $zip '%BOOTSTRAP%'"
  if errorlevel 1 exit /b 1
)
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
