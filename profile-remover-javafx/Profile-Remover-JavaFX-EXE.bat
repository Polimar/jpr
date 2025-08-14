@echo off
title Profile Remover JavaFX v1.0.0
color 0A

echo ========================================
echo    Profile Remover JavaFX v1.0.0
echo ========================================
echo.

cd /d "%~dp0"

if not exist "target\profile-remover-javafx-executable.jar" (
    echo ERRORE: JAR non trovato!
    echo Esegui prima: mvn clean package -DskipTests
    pause
    exit /b 1
)

echo Avvio applicazione...
echo.

java --module-path "C:\Program Files\Java\jdk-21\lib" --add-modules javafx.controls,javafx.fxml -jar "target\profile-remover-javafx-executable.jar"

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERRORE: Impossibile avviare l'applicazione!
    echo Verifica che Java 21 sia installato e nel PATH.
    pause
)
