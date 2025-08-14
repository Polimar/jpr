@echo off
echo Creazione app-image per Profile Remover JavaFX...

"C:\Program Files\Java\jdk-21\bin\jpackage.exe" ^
  --type app-image ^
  --name "Profile Remover JavaFX" ^
  --app-version 1.0.0 ^
  --vendor "Polimar" ^
  --copyright "Copyright 2025 Polimar" ^
  --description "Applicazione per la gestione profili utente su macchine Windows remote" ^
  --icon icon.png ^
  --main-class com.example.profileremover.MainApp ^
  --main-jar profile-remover-javafx-executable.jar ^
  --input target ^
  --dest dist

echo App-image creato in dist/
pause
