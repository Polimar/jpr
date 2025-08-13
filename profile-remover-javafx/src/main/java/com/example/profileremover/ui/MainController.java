package com.example.profileremover.ui;

import com.example.profileremover.core.AppContext;
import com.example.profileremover.core.exec.PowerShellDelete;
import com.example.profileremover.core.exec.PsExecRunner;
import com.example.profileremover.core.model.AppSettings;
import com.example.profileremover.core.model.Profile;
import com.example.profileremover.core.service.InventoryService;
import com.example.profileremover.core.service.LdapService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.stage.Stage;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {
    @FXML private TextField hostField;
    @FXML private CheckBox includeLoaded;
    @FXML private CheckBox includeSpecial;
    @FXML private TableView<Profile> table;
    @FXML private TextArea logArea;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;
    @FXML private Button btnInventory;
    @FXML private Button btnDelete;
    @FXML private Button btnSettings;

    private final ObservableList<Profile> data = FXCollections.observableArrayList();
    private final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    public void initialize() {
        table.setItems(data);
        UiAppender.setListener(this::appendLogAsync);
        TableColumn<Profile, String> colUser = new TableColumn<>("UserName");
        colUser.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().userName()));
        TableColumn<Profile, String> colPath = new TableColumn<>("LocalPath");
        colPath.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().localPath()));
        TableColumn<Profile, String> colSpecial = new TableColumn<>("Special");
        colSpecial.setCellValueFactory(c -> new ReadOnlyStringWrapper(Boolean.toString(c.getValue().special())));
        TableColumn<Profile, String> colLoaded = new TableColumn<>("Loaded");
        colLoaded.setCellValueFactory(c -> new ReadOnlyStringWrapper(Boolean.toString(c.getValue().loaded())));
        TableColumn<Profile, String> colLastUse = new TableColumn<>("LastUse");
        colLastUse.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().lastUseTime()));
        table.getColumns().setAll(colUser, colPath, colSpecial, colLoaded, colLastUse);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @FXML
    public void onInventory() {
        String host = hostField.getText().trim();
        if (!validateHost(host)) return;
        appendLog("Inventory on " + host);

        AppSettings s = AppContext.getSettings();
        File psexec = AppContext.resolvePsExec();
        PsExecRunner runner = new PsExecRunner(psexec,
                Duration.ofSeconds(s.connectTimeoutSec), Duration.ofSeconds(s.execTimeoutSec));
        InventoryService service = new InventoryService(runner);

        Task<List<Profile>> task = new Task<>() {
            @Override
            protected List<Profile> call() throws Exception {
                setBusy(true, "Inventario in corso...");
                return service.listProfiles(host, includeLoaded.isSelected(), includeSpecial.isSelected(),
                        MainController.this::appendLogAsync);
            }
        };

        task.setOnSucceeded(e -> {
            data.setAll(task.getValue());
            appendLog("OK: " + data.size() + " profili");
            setBusy(false, "Pronto");
            maybeEnrichLdap();
        });
        task.setOnFailed(e -> {
            appendLog("ERR: " + task.getException());
            logger.error("Inventory error", task.getException());
            setBusy(false, "Errore inventario");
        });
        new Thread(task, "inventory").start();
    }

    @FXML
    public void onDelete() {
        List<Profile> selected = table.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            appendLog("Nessuna selezione");
            return;
        }
        List<String> targets = selected.stream()
                .filter(p -> !p.loaded() && !p.special())
                .map(Profile::localPath)
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            appendLog("Niente da eliminare (tutti loaded/special)");
            return;
        }
        String host = hostField.getText().trim();
        if (!validateHost(host)) return;

        // Conferma
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Conferma eliminazione");
        confirm.setHeaderText("Eliminare i profili selezionati?");
        confirm.setContentText(String.join("\n", targets));
        var res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        AppSettings s = AppContext.getSettings();
        File psexec = AppContext.resolvePsExec();
        PowerShellDelete deleter = new PowerShellDelete(psexec,
                Duration.ofSeconds(s.connectTimeoutSec), Duration.ofSeconds(s.execTimeoutSec));
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                setBusy(true, "Eliminazione in corso...");
                deleter.deletePaths(host, targets, MainController.this::appendLogAsync, MainController.this::appendLogAsync);
                return null;
            }
        };
        task.setOnSucceeded(e -> { appendLog("Delete completato"); setBusy(false, "Pronto"); });
        task.setOnFailed(e -> {
            appendLog("ERR delete: " + task.getException());
            logger.error("Delete error", task.getException());
            setBusy(false, "Errore delete");
        });
        new Thread(task, "delete").start();
    }

    @FXML
    public void onSettings() {
        Stage st = (Stage) ((Scene) table.getScene()).getWindow();
        Dialogs.showSettings(st);
    }

    private void appendLog(String s) {
        logArea.appendText(s + "\n");
    }

    private void appendLogAsync(String s) {
        Platform.runLater(() -> appendLog(s));
    }

    private boolean validateHost(String host) {
        if (host == null || host.isBlank()) {
            appendLog("Host vuoto");
            return false;
        }
        if (!Pattern.matches("^[A-Za-z0-9._-]+$", host)) {
            appendLog("Host non valido");
            return false;
        }
        String[] allowed = AppContext.getSettings().allowedHosts;
        if (allowed != null && allowed.length > 0) {
            boolean ok = java.util.Arrays.stream(allowed).anyMatch(h -> h.equalsIgnoreCase(host));
            if (!ok) {
                appendLog("Host non in allow-list");
                return false;
            }
        }
        return true;
    }

    private void setBusy(boolean busy, String status) {
        Platform.runLater(() -> {
            progress.setVisible(busy);
            statusLabel.setText(status);
            btnInventory.setDisable(busy);
            btnDelete.setDisable(busy);
            btnSettings.setDisable(busy);
        });
    }

    private void maybeEnrichLdap() {
        AppSettings s = AppContext.getSettings();
        if (s.ldapHost == null || s.ldapHost.isBlank() || s.baseDn == null || s.baseDn.isBlank()) return;
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() {
                try (LdapService ldap = new LdapService(s.ldapHost, s.ldapPort, s.ldapBindDn, s.ldapPassword)) {
                    for (int i = 0; i < data.size(); i++) {
                        Profile p = data.get(i);
                        String folder = p.localPath().substring(p.localPath().lastIndexOf('\\') + 1);
                        String display = ldap.findDisplayNameBySam(s.baseDn, folder, s.displayAttr);
                        if (!display.equals(folder)) {
                            int idx = i;
                            Platform.runLater(() -> data.set(idx, new Profile(p.localPath(), p.sid(), display, p.special(), p.loaded(), p.lastUseTime(), p.sizeBytes())));
                        }
                    }
                } catch (Exception e) {
                    logger.warn("LDAP enrichment failed: {}", e.toString());
                }
                return null;
            }
        };
        new Thread(t, "ldap-enrich").start();
    }
}


