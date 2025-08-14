# Profile Remover JavaFX - Versione EXE

## 🚀 Come Usare l'EXE

### Opzione 1: App-Image (Raccomandato)
1. Estrai `Profile-Remover-JavaFX-v1.0.0-EXE.zip`
2. Esegui `Profile-Remover-JavaFX.bat`
3. L'applicazione si avvierà automaticamente

### Opzione 2: Wrapper Batch
1. Assicurati che Java 21 sia installato
2. Esegui `Profile-Remover-JavaFX-EXE.bat`
3. L'applicazione si avvierà

## 📋 Requisiti

- **Java 21** con JavaFX (Eclipse Temurin JDK 21 o Oracle JDK 21)
- **Windows** (per esecuzione WMI)
- **Accesso amministrativo** alla macchina remota

## 🔧 Installazione Java

Se non hai Java 21:
1. Scarica Eclipse Temurin JDK 21 da: https://adoptium.net/
2. Installa e riavvia il sistema
3. Verifica con: `java -version`

## 🎯 Utilizzo

1. **Inserisci Host**: IP o hostname della macchina remota
2. **Configura Filtri**: 
   - ☑️ "Includi caricati" - profili attualmente in uso
   - ☑️ "Includi speciali" - profili di sistema
3. **Clicca "Inventario"**: Avvia la scansione
4. **Seleziona Profili**: Usa Ctrl+Click per selezione multipla
5. **Doppio Click**: Apre la cartella profilo in Explorer
6. **Elimina**: Rimuove i profili selezionati

## 🚨 Sicurezza

- Richiede credenziali amministrative
- Verifica sempre i profili prima dell'eliminazione
- Backup consigliato per profili importanti

## 🐛 Risoluzione Problemi

### Java non trovato
```
ERRORE: Impossibile avviare l'applicazione!
Verifica che Java 21 sia installato e nel PATH.
```
**Soluzione**: Installa Eclipse Temurin JDK 21

### Connessione WMI fallita
- Verifica credenziali amministrative
- Controlla firewall Windows
- Testa con `wmic /node:"host" computersystem get name`

### Profili non trovati
- Verifica percorso `C:\Users\`
- Controlla permessi di lettura
- Abilita "Includi caricati/speciali"

## 📄 Note Tecniche

- **WMI Direct**: Connessione remota senza PsExec
- **PowerShell**: Calcolo dimensioni e date reali
- **JavaFX**: Interfaccia moderna e responsive
- **Logging**: Tracciamento completo operazioni

## 👥 Autori

Sviluppato per la gestione profili utente enterprise.
