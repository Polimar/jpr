# Profile Remover JavaFX

Applicazione JavaFX per la gestione e rimozione di profili utente su macchine Windows remote.

## 🚀 Funzionalità

- **Inventario profili**: Lista tutti i profili utente su una macchina remota
- **Ordinamento intelligente**: Dimensioni, date, username
- **Nome completo locale**: Ricerca informazioni utente dalla macchina
- **Ultimo login**: Tracciamento accessi utente
- **Doppio clic explorer**: Apertura cartella profilo in Esplora Risorse
- **Barra progresso**: Indicatore avanzamento con percentuale
- **Eliminazione sicura**: Rimozione profili selezionati

## 📋 Requisiti

- **Java 21** con JavaFX (Eclipse Temurin JDK 21 o Oracle JDK 21)
- **Windows** (per esecuzione WMI)
- **Accesso amministrativo** alla macchina remota

## 🛠️ Installazione

### Opzione 1: JAR Eseguibile (Raccomandato)

1. Scarica `profile-remover-javafx-executable.jar`
2. Esegui uno degli script:
   - **Windows**: `run.bat`
   - **PowerShell**: `run.ps1`

### Opzione 2: Da Sorgente

```bash
# Clona il repository
git clone https://github.com/Polimar/jpr.git
cd jpr/profile-remover-javafx

# Compila e avvia
mvn clean package -DskipTests
mvn javafx:run
```

## 🎯 Utilizzo

1. **Inserisci Host**: IP o hostname della macchina remota
2. **Configura Filtri**: 
   - ☑️ "Includi caricati" - profili attualmente in uso
   - ☑️ "Includi speciali" - profili di sistema
3. **Clicca "Inventario"**: Avvia la scansione
4. **Seleziona Profili**: Usa Ctrl+Click per selezione multipla
5. **Doppio Click**: Apre la cartella profilo in Explorer
6. **Elimina**: Rimuove i profili selezionati

## 📊 Colonne Tabella

- **Nome Completo**: Nome utente dalla macchina locale
- **UserName**: Username estratto dal path
- **LocalPath**: Percorso completo del profilo
- **Dimensione**: Spazio occupato (ordinamento numerico)
- **Ultimo Accesso**: Data ultima modifica cartella
- **Ultimo Login**: Data ultimo accesso utente
- **Special**: Profilo di sistema
- **Loaded**: Profilo attualmente caricato

## 🔧 Configurazione

L'applicazione utilizza WMI (Windows Management Instrumentation) per:
- Connessione remota via DCOM
- Esecuzione comandi PowerShell
- Accesso file system via UNC paths

## 🚨 Sicurezza

- Richiede credenziali amministrative
- Verifica sempre i profili prima dell'eliminazione
- Backup consigliato per profili importanti

## 📝 Note Tecniche

- **WMI Direct**: Connessione remota senza PsExec
- **PowerShell**: Calcolo dimensioni e date reali
- **JavaFX**: Interfaccia moderna e responsive
- **Logging**: Tracciamento completo operazioni

## 🐛 Risoluzione Problemi

### JavaFX non trovato
```bash
# Installa Eclipse Temurin JDK 21
# Oppure usa Maven
mvn javafx:run
```

### Connessione WMI fallita
- Verifica credenziali amministrative
- Controlla firewall Windows
- Testa con `wmic /node:"host" computersystem get name`

### Profili non trovati
- Verifica percorso `C:\Users\`
- Controlla permessi di lettura
- Abilita "Includi caricati/speciali"

## 📄 Licenza

Progetto interno - Uso aziendale.

## 👥 Autori

Sviluppato per la gestione profili utente enterprise.
