@echo off
echo Creazione installer MSI per Profile Remover JavaFX...

"C:\Program Files\Java\jdk-21\bin\jpackage.exe" ^
  --type msi ^
  --name "Profile Remover JavaFX" ^
  --app-version 1.0.0 ^
  --vendor "Polimar" ^
  --copyright "Copyright 2025 Polimar" ^
  --description "Applicazione per la gestione profili utente su macchine Windows remote" ^
  --icon icon.png ^
  --main-class com.example.profileremover.MainApp ^
  --main-jar profile-remover-javafx-executable.jar ^
  --input target ^
  --dest dist ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-per-user-install

echo Installer creato in dist/
pause
