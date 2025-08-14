@echo off
echo Creazione MSI per Profile Remover JavaFX con WiX 6.0...

set WIX_PATH=C:\Program Files\WiX Toolset v6.0\bin

echo Verifica WiX 6.0...
"%WIX_PATH%\wix.exe" --version

if %ERRORLEVEL% neq 0 (
    echo ERRORE: WiX 6.0 non trovato!
    pause
    exit /b 1
)

echo.
echo Creazione MSI con WiX 6.0...

REM Prima crea l'app-image se non esiste
if not exist "dist\Profile Remover JavaFX" (
    echo Creazione app-image...
    "C:\Program Files\Java\jdk-21\bin\jpackage.exe" --type app-image --name "Profile Remover JavaFX" --app-version 1.0.0 --vendor "Polimar" --copyright "Copyright 2025 Polimar" --description "Applicazione per la gestione profili utente su macchine Windows remote" --icon icon.ico --main-class com.example.profileremover.MainApp --main-jar profile-remover-javafx-executable.jar --input target --dest dist
)

echo.
echo Compilazione MSI con WiX 6.0...
"%WIX_PATH%\wix.exe" build installer.wxs -out "Profile-Remover-JavaFX-1.0.0.msi"

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ MSI creato con successo!
    echo File: Profile-Remover-JavaFX-1.0.0.msi
) else (
    echo.
    echo ❌ Errore nella creazione del MSI
    echo Controlla che il file installer.wxs sia corretto
)

pause
