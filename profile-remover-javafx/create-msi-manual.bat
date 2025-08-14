@echo off
echo Creazione MSI manuale per Profile Remover JavaFX con WiX 6.0...

set WIX_PATH=C:\Program Files\WiX Toolset v6.0\bin
set JPACKAGE_PATH=C:\Program Files\Java\jdk-21\bin

echo Verifica WiX 6.0...
"%WIX_PATH%\wix.exe" --version

if %ERRORLEVEL% neq 0 (
    echo ERRORE: WiX 6.0 non trovato!
    pause
    exit /b 1
)

echo.
echo Creazione app-image prima...
"%JPACKAGE_PATH%\jpackage.exe" ^
  --type app-image ^
  --name "Profile Remover JavaFX" ^
  --app-version 1.0.0 ^
  --vendor "Polimar" ^
  --copyright "Copyright 2025 Polimar" ^
  --description "Applicazione per la gestione profili utente su macchine Windows remote" ^
  --icon icon.ico ^
  --main-class com.example.profileremover.MainApp ^
  --main-jar profile-remover-javafx-executable.jar ^
  --input target ^
  --dest dist

if %ERRORLEVEL% neq 0 (
    echo ERRORE: Creazione app-image fallita!
    pause
    exit /b 1
)

echo.
echo App-image creato. Ora creazione MSI con WiX 6.0...

REM Crea un file WXS per WiX 6.0
echo ^<?xml version="1.0" encoding="UTF-8"?^> > installer.wxs
echo ^<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi"^> >> installer.wxs
echo   ^<Product Id="*" Name="Profile Remover JavaFX" Language="1033" Version="1.0.0" Manufacturer="Polimar" UpgradeCode="PUT-GUID-HERE"^> >> installer.wxs
echo     ^<Package InstallerVersion="200" Compressed="yes" InstallScope="perUser" /^> >> installer.wxs
echo     ^<MajorUpgrade DowngradeErrorMessage="A newer version of [ProductName] is already installed." /^> >> installer.wxs
echo     ^<MediaTemplate /^> >> installer.wxs
echo     ^<Feature Id="ProductFeature" Title="Profile Remover JavaFX" Level="1"^> >> installer.wxs
echo       ^<ComponentGroupRef Id="ProductComponents" /^> >> installer.wxs
echo     ^</Feature^> >> installer.wxs
echo   ^</Product^> >> installer.wxs
echo   ^<Fragment^> >> installer.wxs
echo     ^<Directory Id="TARGETDIR" Name="SourceDir"^> >> installer.wxs
echo       ^<Directory Id="ProgramFilesFolder"^> >> installer.wxs
echo         ^<Directory Id="INSTALLFOLDER" Name="Profile Remover JavaFX" /^> >> installer.wxs
echo       ^</Directory^> >> installer.wxs
echo     ^</Directory^> >> installer.wxs
echo   ^</Fragment^> >> installer.wxs
echo   ^<Fragment^> >> installer.wxs
echo     ^<ComponentGroup Id="ProductComponents" Directory="INSTALLFOLDER"^> >> installer.wxs
echo       ^<Component Id="ApplicationShortcut" Directory="ProgramMenuDir"^> >> installer.wxs
echo         ^<Shortcut Id="ApplicationStartMenuShortcut" Name="Profile Remover JavaFX" Description="Profile Remover JavaFX" Target="[INSTALLFOLDER]Profile Remover JavaFX.exe" WorkingDirectory="INSTALLFOLDER" /^> >> installer.wxs
echo         ^<RemoveFolder Id="CleanUpShortCut" Directory="ProgramMenuDir" Deferred="yes" /^> >> installer.wxs
echo         ^<RegistryValue Root="HKCU" Key="Software\Microsoft\Profile Remover JavaFX" Name="installed" Type="integer" Value="1" KeyPath="yes" /^> >> installer.wxs
echo       ^</Component^> >> installer.wxs
echo     ^</ComponentGroup^> >> installer.wxs
echo   ^</Fragment^> >> installer.wxs
echo ^</Wix^> >> installer.wxs

echo File WXS creato. Ora compilazione con WiX 6.0...
"%WIX_PATH%\wix.exe" build installer.wxs -out "Profile-Remover-JavaFX-1.0.0.msi"

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ MSI creato con successo!
    echo File: Profile-Remover-JavaFX-1.0.0.msi
) else (
    echo.
    echo ❌ Errore nella creazione del MSI
)

pause
