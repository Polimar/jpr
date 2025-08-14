@echo off
title Profile Remover JavaFX
cd /d "%~dp0"
cd "dist\Profile Remover JavaFX"

echo Avvio Profile Remover JavaFX...
echo.

if exist "runtime\bin\java.exe" (
    "runtime\bin\java.exe" --module-path "runtime\lib" --add-modules javafx.controls,javafx.fxml -cp "app\profile-remover-javafx-1.0.0.jar" com.example.profileremover.MainApp
) else (
    echo ERRORE: Java runtime non trovato!
    echo Assicurati che l'applicazione sia stata installata correttamente.
    pause
)
