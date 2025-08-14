@echo off
echo Avvio Profile Remover...
java --module-path "C:\Program Files\Java\jdk-21\lib" --add-modules javafx.controls,javafx.fxml -jar target\profile-remover-javafx-executable.jar
pause
