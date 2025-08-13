# Profile Remover – Guida completa JavaFX (Windows)

**Obiettivo:** realizzare un’app desktop Windows (installabile e “portable”/self‑contained) in **Java 21 + JavaFX 21** che replica il comportamento dell’app attuale (Python + PySide6):

* Inventario profili remoti via **PsExec** e **WMIC**.
* Eliminazione sicura con **PowerShell** (`Remove-CimInstance`) su profili non speciali e non caricati.
* **Log live** dei comandi eseguiti e audit su file.
* **Impostazioni persistenti** (percorso PSTools, timeouts, LDAP/LDAPS, host allow‑list, ecc.).
* Packaging **.exe / .msi** con **jpackage/jlink**, runtime Java incluso.

> **Nota legale:** PsExec e PSTools sono di Microsoft Sysinternals (EULA). Verifica i termini d’uso e distribuzione. Prevedi uno step di **accettazione EULA** al primo avvio.

---

## 1) Requisiti e scelte

* **JDK:** Temurin/OpenJDK 21 (x64 Windows).
* **JavaFX:** 21 (SDK + runtime modulari) – tema moderno via CSS o libreria (opz. **Atlantafx**).
* **Build:** Maven + `javafx-maven-plugin` (opp. Gradle equivalente).
* **Librerie:**

  * **OpenCSV** (parsing WMIC CSV).
  * **SLF4J + Logback** (logging su file + appender live per UI).
  * **UnboundID LDAP SDK** (LDAP/LDAPS semplice e robusto).
  * **Apache Commons Exec** *(opz.)* o **ProcessBuilder** nativo per processi esterni.
  * **ControlsFX** *(opz.)* per controlli UI aggiuntivi.
* **Distribuzione:** `jlink` per runtime ridotto + `jpackage` per `.exe`/`.msi`.
* **Target OS:** Windows 10/11 (host locale **Windows**; la macchina da cui lanci PsExec deve essere Windows).

---

## 2) Struttura del progetto (Maven, single‑module)

```
profile-remover-javafx/
  pom.xml
  src/
    main/java/com/example/profileremover/
      MainApp.java
      ui/ (controller, view model)
      view/ (FXML, CSS)
      core/ (services, runner, ldap, model)
    main/resources/
      application.properties (defaults)
      view/*.fxml
      css/app.css
      pstools/ (opz.: segnaposto, EULA.txt)
```

> In alternativa, puoi usare **multi‑module** (`app`, `core`, `infra`). Per iniziare, un singolo modulo è più veloce.

---

## 3) POM Maven (JavaFX + dipendenze)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>profile-remover-javafx</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <javafx.version>21.0.3</javafx.version>
    <slf4j.version>2.0.13</slf4j.version>
    <logback.version>1.5.6</logback.version>
  </properties>

  <dependencies>
    <!-- JavaFX (usa classifier :win per runtime Windows) -->
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-controls</artifactId>
      <version>${javafx.version}</version>
    </dependency>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-fxml</artifactId>
      <version>${javafx.version}</version>
    </dependency>

    <!-- CSV -->
    <dependency>
      <groupId>com.opencsv</groupId>
      <artifactId>opencsv</artifactId>
      <version>5.9</version>
    </dependency>

    <!-- LDAP -->
    <dependency>
      <groupId>com.unboundid</groupId>
      <artifactId>unboundid-ldapsdk</artifactId>
      <version>6.0.11</version>
    </dependency>

    <!-- Logging -->
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>${slf4j.version}</version>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>${logback.version}</version>
    </dependency>

    <!-- Processi esterni (opz.) -->
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-exec</artifactId>
      <version>1.4.0</version>
      <optional>true</optional>
    </dependency>

    <!-- UI extra (opz.) -->
    <dependency>
      <groupId>org.controlsfx</groupId>
      <artifactId>controlsfx</artifactId>
      <version>11.2.1</version>
      <optional>true</optional>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-maven-plugin</artifactId>
        <version>0.0.8</version>
        <configuration>
          <mainClass>com.example.profileremover.MainApp</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## 4) Modello dati

```java
// core/model/Profile.java
public record Profile(
    String localPath,
    String sid,
    String userName, // arricchito via LDAP, fallback a DOMAIN\\user oppure folder
    boolean special,
    boolean loaded,
    String lastUseTime, // WMIC stringa raw; opz. parsing a Instant
    Long sizeBytes // opz. calcolo asincrono
) {}

// core/model/AppSettings.java
public class AppSettings {
  public String pstoolsDir = "C\\\\PSTools"; // o risolto da UI
  public int connectTimeoutSec = 30;
  public int execTimeoutSec = 180;
  public String ldapHost = "";
  public int ldapPort = 636;
  public String ldapBindDn = "";
  public String ldapPassword = ""; // considera Windows Credential Locker invece di plain text
  public String baseDn = "";
  public String displayAttr = "displayName";
  public String[] allowedHosts = new String[]{}; // allow‑list opzionale
}
```

Persistenza impostazioni: JSON in `%LOCALAPPDATA%/ProfileRemover/settings.json` (via Jackson) con cifratura opzionale delle credenziali.

---

## 5) UI JavaFX – wireframe e pattern

* **Scene principale** a layout responsive (BorderPane + VBox/HBox):

  * *Toolbar superiore*: campo `Host`, checkbox *Includi caricati*, *Includi speciali*, pulsanti **Inventaria**, **Elimina selezionati**, **Impostazioni**.
  * *Tabella centrale* (`TableView<Profile>`): colonne `UserName`, `LocalPath`, `Special`, `Loaded`, `LastUseTime`, `Size (GB)` *(opz.)* con selezione multipla.
  * *Pannello Log* (TextArea non editabile) a scomparsa/dock con **log live**.
  * *Status bar* con spinner/progresso, esito ultimo comando.
* **Dialog Impostazioni**: percorso `pstoolsDir`, timeouts, LDAP/LDAPS (host/porta/bindDN/pwd/baseDN/displayAttr), allow‑list host.
* **Stile**: CSS moderno (dark/light). Valuta **Atlantafx** per un look & feel contemporaneo.
* **Threading**: operazioni lunghe in **`Task`/`Service` JavaFX**; aggiornamenti UI via `Platform.runLater`.

---

## 6) Runner PsExec/WMIC e Delete (Java)

### 6.1 Helper di processo (timeout + log streaming)

```java
// core/exec/ProcessRunner.java
package com.example.profileremover.core.exec;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ProcessRunner {
  public record Result(int exitCode, String stdout, String stderr) {}

  public static Result run(
      List<String> command,
      File workingDir,
      Duration timeout,
      Charset charset,
      Consumer<String> onStdout,
      Consumer<String> onStderr
  ) throws IOException, InterruptedException, TimeoutException {
    ProcessBuilder pb = new ProcessBuilder(command);
    if (workingDir != null) pb.directory(workingDir);
    Process p = pb.start();

    var ex = Executors.newFixedThreadPool(2);
    var outFut = ex.submit(() -> readStream(p.getInputStream(), charset, onStdout));
    var errFut = ex.submit(() -> readStream(p.getErrorStream(), charset, onStderr));

    boolean finished = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    if (!finished) {
      p.destroyForcibly();
      ex.shutdownNow();
      throw new TimeoutException("Process timeout");
    }
    String out = outFut.get(100, TimeUnit.MILLISECONDS);
    String err = errFut.get(100, TimeUnit.MILLISECONDS);
    ex.shutdown();
    return new Result(p.exitValue(), out, err);
  }

  private static String readStream(InputStream is, Charset cs, Consumer<String> sink) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, cs))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line).append('\n');
        if (sink != null) sink.accept(line);
      }
      return sb.toString();
    }
  }
}
```

### 6.2 PsExecRunner – WMIC inventory

```java
// core/exec/PsExecRunner.java
package com.example.profileremover.core.exec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class PsExecRunner {
  private final File psExec;
  private final Duration connectTimeout;
  private final Duration execTimeout;

  public PsExecRunner(File psExec, Duration connectTimeout, Duration execTimeout) {
    this.psExec = psExec;
    this.connectTimeout = connectTimeout;
    this.execTimeout = execTimeout;
  }

  public ProcessRunner.Result runWmic(
      String host,
      String wmicArgs,
      Consumer<String> onStdout,
      Consumer<String> onStderr
  ) throws Exception {
    var cmd = List.of(
      psExec.getAbsolutePath(),
      "\\\\" + host,
      "-accepteula",
      "-nobanner",
      "-s",
      "-n", String.valueOf(connectTimeout.toSeconds()),
      "cmd", "/c", "wmic " + wmicArgs
    );
    try {
      return ProcessRunner.run(cmd, psExec.getParentFile(), execTimeout, StandardCharsets.UTF_8, onStdout, onStderr);
    } catch (TimeoutException te) {
      throw te;
    }
  }
}
```

### 6.3 PowerShell delete (EncodedCommand)

```java
// core/exec/PowerShellDelete.java
package com.example.profileremover.core.exec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

public class PowerShellDelete {
  private final File psExec;
  private final Duration connectTimeout;
  private final Duration execTimeout;

  public PowerShellDelete(File psExec, Duration connectTimeout, Duration execTimeout) {
    this.psExec = psExec;
    this.connectTimeout = connectTimeout;
    this.execTimeout = execTimeout;
  }

  public ProcessRunner.Result deletePaths(String host, List<String> localPaths,
      Consumer<String> onStdout, Consumer<String> onStderr) throws Exception {

    String script = buildScript(localPaths);
    String b64 = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));

    var cmd = List.of(
      psExec.getAbsolutePath(),
      "\\\\" + host,
      "-accepteula",
      "-nobanner",
      "-s",
      "-n", String.valueOf(connectTimeout.toSeconds()),
      "powershell",
      "-NoProfile",
      "-NonInteractive",
      "-ExecutionPolicy", "Bypass",
      "-EncodedCommand", b64
    );

    return ProcessRunner.run(cmd, psExec.getParentFile(), execTimeout,
      StandardCharsets.UTF_8, onStdout, onStderr);
  }

  private String buildScript(List<String> paths) {
    // Funzione sicura: rimuove profili non speciali e non caricati
    String fn = """
function Remove-UserProfileSafe {
  param([string[]]$Paths)
  $ErrorActionPreference = 'Stop'
  foreach ($p in $Paths) {
    $prof = Get-CimInstance -ClassName Win32_UserProfile | Where-Object { $_.LocalPath -ieq $p }
    if (-not $prof) { Write-Output \"SKIP: not found $p\"; continue }
    if ($prof.Special -eq $true) { Write-Output \"SKIP: special $p\"; continue }
    if ($prof.Loaded -eq $true)  { Write-Output \"SKIP: loaded $p\"; continue }
    Write-Output \"DELETE: $p\"
    Remove-CimInstance -InputObject $prof -ErrorAction Stop
  }
}
""";
    String call = "Remove-UserProfileSafe -Paths @(" +
      paths.stream().map(p -> \"'\" + p.replace(\"'\", \"''\") + \"'\").reduce((a,b)->a+","+b).orElse("") + ")";
    return fn + "\n" + call + "\n";
  }
}
```

---

## 7) InventoryService: esegue WMIC e parse CSV

```java
// core/service/InventoryService.java
package com.example.profileremover.core.service;

import com.example.profileremover.core.exec.PsExecRunner;
import com.example.profileremover.core.model.Profile;
import com.opencsv.CSVReader;
import java.io.StringReader;
import java.util.*;
import java.util.function.Consumer;

public class InventoryService {
  private final PsExecRunner runner;

  public InventoryService(PsExecRunner runner) { this.runner = runner; }

  public List<Profile> listProfiles(String host, boolean includeLoaded, boolean includeSpecial,
                                    Consumer<String> onLog) throws Exception {
    var res = runner.runWmic(host,
      "path win32_userprofile get LocalPath,SID,Special,Loaded,LastUseTime /format:csv",
      line -> onLog.accept("OUT: "+line),
      line -> onLog.accept("ERR: "+line)
    );
    onLog.accept("ExitCode=" + res.exitCode());

    // WMIC CSV header: Node,LastUseTime,Loaded,LocalPath,SID,Special
    List<Profile> out = new ArrayList<>();
    try (CSVReader r = new CSVReader(new StringReader(res.stdout()))) {
      String[] row;
      boolean headerSkipped = false;
      while ((row = r.readNext()) != null) {
        if (row.length < 6) continue;
        if (!headerSkipped && row[0].equalsIgnoreCase("Node")) { headerSkipped = true; continue; }
        String node = row[0];
        String lastUse = row[1];
        boolean loaded = Boolean.parseBoolean(Optional.ofNullable(row[2]).orElse("false"));
        String localPath = row[3];
        String sid = row[4];
        boolean special = Boolean.parseBoolean(Optional.ofNullable(row[5]).orElse("false"));
        if (localPath == null || !localPath.toLowerCase().startsWith("c\\\\users\\")) continue;
        if (!includeLoaded && loaded) continue;
        if (!includeSpecial && special) continue;

        String folder = localPath.substring(localPath.lastIndexOf('\\')+1);
        String userName = folder; // enrichment LDAP in seguito
        out.add(new Profile(localPath, sid, userName, special, loaded, lastUse, null));
      }
    }
    return out;
  }
}
```

---

## 8) LDAP enrichment (opzionale ma consigliato)

```java
// core/service/LdapService.java
package com.example.profileremover.core.service;

import com.unboundid.ldap.sdk.*;

public class LdapService implements AutoCloseable {
  private final LDAPConnection connection;

  public LdapService(String host, int port, String bindDn, String password) throws LDAPException {
    this.connection = new LDAPConnection(host, port, bindDn, password);
  }

  public String findDisplayNameBySam(String baseDn, String samAccountName, String displayAttr) throws LDAPSearchException {
    String filter = "(&(objectClass=user)(sAMAccountName=" + samAccountName + "))";
    SearchRequest req = new SearchRequest(baseDn, SearchScope.SUB, filter, displayAttr);
    SearchResult res = connection.search(req);
    if (res.getEntryCount() == 0) return samAccountName;
    return res.getSearchEntries().get(0).getAttributeValue(displayAttr);
  }

  @Override public void close() { if (connection != null) connection.close(); }
}
```

Arricchimento `userName`: se `C:\\Users\\alice` ⇒ `samAccountName = "alice"` ⇒ query LDAP; fallback al `folder` se non trovato.

> **LDAPS**: valida i certificati in produzione (truststore Java). Evita disabilitazioni di verifica.

---

## 9) UI – FXML di base e controller (inventario + log live)

**FXML (semplificato):**

```xml
<BorderPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml" fx:controller="com.example.profileremover.ui.MainController">
  <top>
    <HBox spacing="8" styleClass="toolbar">
      <TextField fx:id="hostField" promptText="Host" />
      <CheckBox fx:id="includeLoaded" text="Includi caricati" />
      <CheckBox fx:id="includeSpecial" text="Includi speciali" />
      <Button text="Inventaria" onAction="#onInventory" />
      <Button text="Elimina selezionati" onAction="#onDelete" />
      <Button text="Impostazioni" onAction="#onSettings" />
    </HBox>
  </top>
  <center>
    <TableView fx:id="table">
      <!-- definisci colonne in codice o qui -->
    </TableView>
  </center>
  <bottom>
    <VBox>
      <Label text="Log" />
      <TextArea fx:id="logArea" editable="false" prefRowCount="8" />
    </VBox>
  </bottom>
</BorderPane>
```

**Controller (estratto):**

```java
// ui/MainController.java
package com.example.profileremover.ui;

import com.example.profileremover.core.exec.PsExecRunner;
import com.example.profileremover.core.model.Profile;
import com.example.profileremover.core.service.InventoryService;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.time.Duration;
import java.util.List;

public class MainController {
  @FXML private TextField hostField; @FXML private CheckBox includeLoaded; @FXML private CheckBox includeSpecial;
  @FXML private TableView<Profile> table; @FXML private TextArea logArea;

  private final ObservableList<Profile> data = FXCollections.observableArrayList();

  @FXML public void initialize() {
    table.setItems(data);
    // TODO: definire TableColumns e cellValueFactory
  }

  @FXML public void onInventory() {
    String host = hostField.getText().trim();
    appendLog("Inventory on " + host);

    var runner = new PsExecRunner(new File("C:/PSTools/PsExec64.exe"), Duration.ofSeconds(30), Duration.ofSeconds(180));
    var service = new InventoryService(runner);

    Task<List<Profile>> task = new Task<>() {
      @Override protected List<Profile> call() throws Exception {
        return service.listProfiles(host, includeLoaded.isSelected(), includeSpecial.isSelected(), line -> appendLogAsync(line));
      }
    };

    task.setOnSucceeded(e -> { data.setAll(task.getValue()); appendLog("OK: " + data.size() + " profili"); });
    task.setOnFailed(e -> appendLog("ERR: " + task.getException()));
    new Thread(task, "inventory").start();
  }

  private void appendLog(String s) { logArea.appendText(s + "\n"); }
  private void appendLogAsync(String s) { Platform.runLater(() -> appendLog(s)); }

  @FXML public void onDelete() { /* analogo: costruisci lista selezionati e chiama PowerShellDelete */ }
  @FXML public void onSettings() { /* mostra dialog impostazioni e salva su file */ }
}
```

---

## 10) Log live + file di audit

* **Appender UI**: crea un appender Logback custom che pubblica le righe su `ObservableList<String>` o direttamente su `TextArea` via `Platform.runLater`.
* **File di log**: `%LOCALAPPDATA%/ProfileRemover/logs/app.log` con rotazione giornaliera.

**`logback.xml` (in `src/main/resources/`):**

```xml
<configuration>
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOCALAPPDATA}/ProfileRemover/logs/app.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>${LOCALAPPDATA}/ProfileRemover/logs/app.%d{yyyy-MM-dd}.log</fileNamePattern>
      <maxHistory>14</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>%date %-5level %logger - %msg%n</pattern>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="FILE"/>
  </root>
</configuration>
```

---

## 11) Sicurezza e hardening

* **Privilegi**: esegui l’app con utente che abbia privilegi admin sui target remoti (necessari per PsExec).
* **UAC**: valuta un manifest *requireAdministrator* nell’eseguibile tramite `jpackage`.
* **Allow‑list host**: verifica che l’host richiesto sia in elenco; regex `^[A-Za-z0-9._-]+$`.
* **Comandi whitelisted**: costruisci solo i comandi previsti (WMIC / PowerShell EncodedCommand).
* **LDAPS**: in produzione *obbligatorio*; gestisci truststore (import CA aziendale nel cacerts o in un truststore dedicato).
* **Audit**: logga comando (senza password), host, esito, exit code, tempi.

---

## 12) Esperienza d’uso (UX)

* **Conferma eliminazione** con lista percorsi e badge "loaded/special" (bloccati).
* **Indicatore stato**: spinner e step (Connesso → WMIC → Parsing → Completato/Errore).
* **Esportazione**: pulsanti *Esporta CSV/JSON* dei profili inventariati.
* **Tema**: dark/light toggle; font leggibile (Segoe UI).

---

## 13) Fallback in assenza di WMIC (opzionale)

Se WMIC non è disponibile sul target, puoi fare inventory via PowerShell CIM:

```powershell
Get-CimInstance Win32_UserProfile | Select-Object LocalPath,SID,Special,Loaded,LastUseTime | ConvertTo-Csv -NoTypeInformation
```

Adatta `PsExecRunner` per eseguire PowerShell e parsare CSV come sopra.

---

## 14) Persistenza impostazioni

```java
// core/persist/SettingsStore.java
package com.example.profileremover.core.persist;

import com.example.profileremover.core.model.AppSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;

public class SettingsStore {
  private final Path path = Path.of(System.getenv("LOCALAPPDATA"), "ProfileRemover", "settings.json");
  private final ObjectMapper om = new ObjectMapper();

  public AppSettings load() {
    try { if (Files.exists(path)) return om.readValue(Files.readString(path), AppSettings.class); } catch (Exception ignored) {}
    return new AppSettings();
  }
  public void save(AppSettings s) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, om.writerWithDefaultPrettyPrinter().writeValueAsString(s));
  }
}
```

---

## 15) Packaging: jlink + jpackage (Windows)

### 15.1 Runtime ridotto

Esempio (da Maven o script):

```bash
jlink --no-header-files --no-man-pages \
  --add-modules java.base,java.logging,java.xml,javafx.controls,javafx.fxml \
  --output build/runtime
```

### 15.2 Installer `.exe` / `.msi`

```bash
jpackage \
  --name "Profile Remover" \
  --app-version 1.0.0 \
  --type exe \
  --input target \
  --main-jar profile-remover-javafx-1.0.0.jar \
  --main-class com.example.profileremover.MainApp \
  --icon assets/icon.ico \
  --vendor "Acme Corp" \
  --win-dir-chooser \
  --win-menu --win-menu-group "Acme" \
  --win-shortcut \
  --runtime-image build/runtime \
  --dest dist
```

Varianti:

* `--type msi` per MSI (integrazione con WiX facoltativa per opzioni avanzate).
* `--win-per-user-install` se vuoi installazione per utente corrente.
* `--win-console` per console visibile (debug).
* **Includere PSTools**: copia `PsExec64.exe` nella cartella dell’app (es. `app\pstools\`). In app leggi `pstoolsDir` dalle impostazioni e, se vuoto, usa `./pstools` relativa all’eseguibile.

---

## 16) Accettazione EULA PsExec al primo avvio

* Verifica se esiste la chiave di registro `HKCU\Software\Sysinternals\PsExec\EulaAccepted`.
* In assenza, esegui in background: `PsExec64.exe -accepteula` e mostra una notifica che l’utente ha accettato i termini.

*(Puoi farlo con `reg.exe` o leggendo il registry via JNA; più semplice: tentare il primo comando con `-accepteula` come da runner.)*

---

## 17) Flussi principali (step‑by‑step)

### 17.1 Inventory

1. Validare `host` (regex + allow‑list).
2. Costruire comando WMIC tramite `PsExecRunner` (sempre `cmd /c wmic ... /format:csv`).
3. Streammare stdout/stderr nel pannello log.
4. Parsare CSV in `Profile[]`.
5. Arricchire `userName` via LDAP (best‑effort, non bloccante: es. `CompletableFuture` per ogni riga e aggiornamento incrementale della tabella).

### 17.2 Delete

1. Prendere selezione dalla tabella; bloccare/filtrare profili `loaded=true` o `special=true` (anche lato codice PowerShell sono già protetti).
2. Mostrare dialog di conferma con elenco path.
3. Costruire script `Remove-UserProfileSafe` + invocazione con `-EncodedCommand` (UTF‑16LE + Base64).
4. Streammare log; evidenziare `DELETE:` / `SKIP:`.

### 17.3 Calcolo dimensioni (opz.)

* Job asincrono per `sizeBytes` (PowerShell `Get-ChildItem -Recurse | Measure-Object -Sum Length`). Mostrare colonna *Size (GB)* quando disponibile.

---

## 18) Styling moderno (CSS)

Esempio rapido (dark):

```css
.root { -fx-font-family: "Segoe UI"; -fx-base: #1f1f1f; -fx-background: #121212; }
.text-area { -fx-control-inner-background: #181818; }
.table-view { -fx-background-color: transparent; }
.button { -fx-background-radius: 8; -fx-padding: 8 14; }
```

Oppure usa **Atlantafx** per preset moderni (card, chip, badge).

---

## 19) Considerazioni di rete/permessi

* **PsExec** richiede SMB/RPC aperti e privilegi admin sul target; se necessario, supporta credenziali (`-u DOMAIN\\user -p *****`).
* **Firewall**: documenta le porte e troubleshooting (RPC 135, SMB 445, dinamiche DCOM).
* **ExecutionPolicy**: usi `-ExecutionPolicy Bypass` per sicurezza operativa (script è in memoria via EncodedCommand).

---

## 20) Troubleshooting

* **Exit code ≠ 0**: mostra stderr e suggerisci controlli rete/permessi.
* **WMIC non risponde**: aumenta `connectTimeout`/`execTimeout`; prova fallback PowerShell CIM.
* **LDAP fallisce**: continua senza enrichment, mostra warning; controlla certificati per LDAPS.
* **UI che si blocca**: assicurati che tutto giri su `Task`/`Service` e non sul thread FX.

---

## 21) Checklist di accettazione

* [ ] Inventario stabile su host valido; dati coerenti con WMIC.
* [ ] Rimozione sicura con guardie (non loaded/special) e log chiari `DELETE/SKIP`.
* [ ] Log live visibili e log file con rotazione.
* [ ] Impostazioni persistenti; PsExec path configurabile (bundle locale di default).
* [ ] Build prod unica (`jpackage`) con runtime incluso; icona, shortcut, disinstallazione funzionanti.
* [ ] Opz.: calcolo dimensioni on‑demand; esport CSV/JSON.

---

## 22) Comandi rapidi

```bash
# Dev run
mvn clean javafx:run

# Build jar
mvn -DskipTests package

# Crea runtime ridotto (esempio)
jlink --no-header-files --no-man-pages \
  --add-modules java.base,java.logging,java.xml,javafx.controls,javafx.fxml \
  --output build/runtime

# Crea installer exe
jpackage --name "Profile Remover" --type exe \
  --input target --main-jar profile-remover-javafx-1.0.0.jar \
  --main-class com.example.profileremover.MainApp \
  --icon assets/icon.ico --vendor "Acme Corp" \
  --runtime-image build/runtime --dest dist
```

---

## 23) Prossimi passi consigliati

1. Inizializzare repo Maven con POM qui sopra.
2. Aggiungere FXML + Controller base e CSS.
3. Implementare `InventoryService` e collegarlo alla UI (log live e tabella).
4. Implementare `PowerShellDelete` + dialog conferma.
5. Integrare `SettingsStore` e dialog impostazioni.
6. Packaging con `jpackage` + inclusione `pstools/`.
7. Test in rete reale (host di prova) e hardening.

---

*Questo documento è pensato come spec + starter kit per costruire un’app desktop JavaFX moderna, portabile e allineata al flusso operativo esistente.*
