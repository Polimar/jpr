@echo off
echo Creazione MSI per Profile Remover JavaFX con WiX 6.0...

set WIX_PATH=C:\Program Files\WiX Toolset v6.0\bin
set JPACKAGE_PATH=C:\Program Files\Java\jdk-21\bin

echo Verifica WiX 6.0...
"%WIX_PATH%\wix.exe" --version

if %ERRORLEVEL% neq 0 (
    echo ERRORE: WiX 6.0 non trovato o non funzionante!
    pause
    exit /b 1
)

echo.
echo Creazione MSI installer...
"%JPACKAGE_PATH%\jpackage.exe" ^
  --type msi ^
  --name "Profile Remover JavaFX" ^
  --app-version 1.0.0 ^
  --vendor "Polimar" ^
  --copyright "Copyright 2025 Polimar" ^
  --description "Applicazione per la gestione profili utente su macchine Windows remote" ^
  --icon icon.ico ^
  --main-class com.example.profileremover.MainApp ^
  --main-jar profile-remover-javafx-executable.jar ^
  --input target ^
  --dest dist ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-per-user-install

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ MSI creato con successo in dist/
    echo File: dist\Profile Remover JavaFX-1.0.0.msi
) else (
    echo.
    echo ❌ Errore nella creazione del MSI
)

pause
