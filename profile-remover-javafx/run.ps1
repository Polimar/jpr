# Script per avviare Profile Remover
Write-Host "Avvio Profile Remover..." -ForegroundColor Green

# Cerca JavaFX nel sistema
$javaFxPath = $null

# Possibili percorsi JavaFX
$possiblePaths = @(
    "C:\Program Files\Java\jdk-21\lib",
    "C:\Program Files\Java\jre-21\lib",
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.3-hotspot\lib",
    "C:\Program Files\OpenJDK\jdk-21\lib"
)

foreach ($path in $possiblePaths) {
    if (Test-Path $path) {
        $javaFxPath = $path
        Write-Host "JavaFX trovato in: $path" -ForegroundColor Yellow
        break
    }
}

if ($javaFxPath) {
    Write-Host "Avvio applicazione..." -ForegroundColor Green
    java --module-path "$javaFxPath" --add-modules javafx.controls,javafx.fxml -jar target\profile-remover-javafx-executable.jar
} else {
    Write-Host "ERRORE: JavaFX non trovato!" -ForegroundColor Red
    Write-Host "Assicurati di avere Java 21 con JavaFX installato." -ForegroundColor Yellow
    Write-Host "Possibili soluzioni:" -ForegroundColor Yellow
    Write-Host "1. Installa Eclipse Temurin JDK 21" -ForegroundColor White
    Write-Host "2. Installa Oracle JDK 21" -ForegroundColor White
    Write-Host "3. Usa Maven: mvn javafx:run" -ForegroundColor White
}

Read-Host "Premi Enter per chiudere"
