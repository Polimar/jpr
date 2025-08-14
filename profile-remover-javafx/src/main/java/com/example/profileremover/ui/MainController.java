package com.example.profileremover.ui;

import com.example.profileremover.core.AppContext;
import com.example.profileremover.core.exec.WmiDirect;
import com.example.profileremover.core.model.AppSettings;
import com.example.profileremover.core.model.Profile;
import com.example.profileremover.core.service.InventoryService;
import com.example.profileremover.core.service.ProfileDeletionService;
import com.example.profileremover.core.service.ProfileEnrichmentService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    @FXML private TextField hostField;
    @FXML private CheckBox includeLoadedCheckbox;
    @FXML private CheckBox includeSpecialCheckbox;
    @FXML private TableView<Profile> profilesTable;
    @FXML private TableColumn<Profile, String> displayNameColumn;
    @FXML private TableColumn<Profile, String> userNameColumn;
    @FXML private TableColumn<Profile, String> localPathColumn;
    @FXML private TableColumn<Profile, String> sizeColumn;
    @FXML private TableColumn<Profile, String> lastUseColumn;
    @FXML private TableColumn<Profile, String> lastLoginColumn;
    @FXML private TableColumn<Profile, Boolean> specialColumn;
    @FXML private TableColumn<Profile, Boolean> loadedColumn;
    @FXML private Button inventoryButton;
    @FXML private Button deleteSelectedButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    private final ObservableList<Profile> profiles = FXCollections.observableArrayList();
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupUI();
        
        updateStatus("Profile Remover avviato - Inserisci l'host remoto e clicca 'Inventario'");
    }

    private void setupTable() {
        // Configurazione colonne con callback per record
        displayNameColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDisplayNameOrUser()));
        userNameColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().userName()));
        localPathColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().localPath()));
        sizeColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFormattedSize()));
        
        // Ordinamento corretto per dimensioni (by bytes, non string)
        sizeColumn.setComparator((size1, size2) -> {
            // Estrai i bytes dalle stringhe per confronto numerico
            Long bytes1 = extractBytesFromSizeString(size1);
            Long bytes2 = extractBytesFromSizeString(size2);
            return bytes1.compareTo(bytes2);
        });
        
        // Abilita ordinamento per tutte le colonne
        sizeColumn.setSortable(true);
        lastUseColumn.setSortable(true);
        lastLoginColumn.setSortable(true);
        lastUseColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFormattedLastUse()));
        lastLoginColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLastLogin()));
        specialColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().special()).asObject());
        loadedColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().loaded()).asObject());

        // Checkbox per selezione multipla
        profilesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Binding della lista
        profilesTable.setItems(profiles);
        
        // Placeholder quando vuoto
        profilesTable.setPlaceholder(new Label("Nessun profilo trovato"));
        
        // Doppio clic per aprire Esplora Risorse
        profilesTable.setRowFactory(tv -> {
            TableRow<Profile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Profile profile = row.getItem();
                    openExplorer(profile);
                }
            });
            return row;
        });
    }

    private void setupUI() {
        // Disabilita pulsante eliminazione se nessuna selezione
        deleteSelectedButton.setDisable(true);
        
        // Abilita eliminazione solo quando ci sono elementi selezionati
        profilesTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                deleteSelectedButton.setDisable(profilesTable.getSelectionModel().getSelectedItems().isEmpty());
            }
        );
    }

    @FXML
    public void onInventory() {
        String host = hostField.getText().trim();
        if (!validateHost(host)) {
            showError("Host non valido", "Inserisci un indirizzo IP o hostname valido");
            return;
        }

        boolean includeLoaded = includeLoadedCheckbox.isSelected();
        boolean includeSpecial = includeSpecialCheckbox.isSelected();

        updateStatus("Inventario in corso su " + host + "...");
        setUIEnabled(false);
        profiles.clear();

        // Esegui inventario in background
        Task<List<Profile>> inventoryTask = new Task<>() {
            @Override
            protected List<Profile> call() throws Exception {
                AppSettings settings = AppContext.getSettings();
                
                // Step 0: Conta profili per la barra di progresso
                updateMessage("Conteggio profili...");
                updateProgressBar(0.0);
                
                int totalProfilesCount = 0;
                try {
                    WmiDirect wmiDirect = new WmiDirect(Duration.ofSeconds(settings.connectTimeoutSec));
                    var countResult = wmiDirect.countUserProfiles(host, null);
                    if (countResult.exitCode == 0) {
                        String[] lines = countResult.stdout.split("\n");
                        for (String line : lines) {
                            if (line.trim().contains("Users") && !line.trim().toLowerCase().contains("node")) {
                                totalProfilesCount++;
                            }
                        }
                    }
                } catch (Exception e) {
                    totalProfilesCount = 10; // Fallback
                }
                
                final int totalProfiles = Math.max(totalProfilesCount, 1);
                updateMessage("Trovati " + totalProfiles + " profili da elaborare");
                
                // Step 1: Ottieni profili base
                updateMessage("Connessione WMI...");
                updateProgressBar(0.1);
                InventoryService inventoryService = new InventoryService(Duration.ofSeconds(settings.connectTimeoutSec));
                List<Profile> baseProfiles = inventoryService.listProfiles(host, includeLoaded, includeSpecial, 
                    message -> Platform.runLater(() -> updateStatus(message))
                );
                
                updateProgressBar(0.3);
                
                // Step 2: Arricchisci con LDAP e dimensioni
                updateMessage("Arricchimento dati...");
                try {
                    ProfileEnrichmentService enrichmentService = new ProfileEnrichmentService(settings);
                    
                    // Callback per progresso
                    final int[] processedCount = {0};
                    List<Profile> enrichedProfiles = enrichmentService.enrichProfiles(host, baseProfiles,
                        message -> {
                            Platform.runLater(() -> {
                                updateStatus(message);
                                if (message.contains("Elaborazione profilo")) {
                                    processedCount[0]++;
                                    double progress = 0.3 + (0.7 * processedCount[0] / totalProfiles);
                                    updateProgressBar(Math.min(progress, 1.0), 
                                        String.format("Elaborazione profilo %d di %d", processedCount[0], totalProfiles));
                                }
                            });
                        }
                    );
                    enrichmentService.close();
                    updateProgressBar(1.0);
                    return enrichedProfiles;
                } catch (Exception e) {
                    // Se LDAP fallisce, continua senza arricchimento
                    updateMessage("LDAP non disponibile - continuazione senza arricchimento");
                    updateProgressBar(1.0);
                    return baseProfiles.stream()
                        .map(p -> new Profile(p.localPath(), p.sid(), p.userName(), p.special(), p.loaded(), p.lastUseTime(), null, null, null))
                        .collect(java.util.stream.Collectors.toList());
                }
            }
        };

        inventoryTask.setOnSucceeded(e -> {
            List<Profile> result = inventoryTask.getValue();
            Platform.runLater(() -> {
                profiles.addAll(result);
                updateStatus("Inventario completato: " + result.size() + " profili trovati");
                setUIEnabled(true);
            });
        });

        inventoryTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable exception = inventoryTask.getException();
                String errorMsg = "ERRORE durante inventario: " + 
                    (exception != null ? exception.getMessage() : "Errore sconosciuto");
                updateStatus(errorMsg);
                showError("Errore Inventario", errorMsg);
                setUIEnabled(true);
            });
        });

        new Thread(inventoryTask).start();
    }

    @FXML
    public void onDeleteSelected() {
        List<Profile> selectedProfiles = profilesTable.getSelectionModel().getSelectedItems()
                .stream().collect(Collectors.toList());
        
        if (selectedProfiles.isEmpty()) {
            showError("Selezione vuota", "Seleziona almeno un profilo da eliminare");
            return;
        }

        // Conferma eliminazione
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Conferma Eliminazione");
        confirmation.setHeaderText("Eliminare " + selectedProfiles.size() + " profili?");
        confirmation.setContentText("Questa operazione è irreversibile!\n\nProfili da eliminare:\n" +
            selectedProfiles.stream()
                .map(p -> "- " + p.getDisplayNameOrUser() + " (" + p.localPath() + ")")
                .collect(Collectors.joining("\n")));

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        String host = hostField.getText().trim();
        updateStatus("Eliminazione " + selectedProfiles.size() + " profili in corso...");
        setUIEnabled(false);

        // Esegui eliminazione in background
        Task<Void> deleteTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AppSettings settings = AppContext.getSettings();
                ProfileDeletionService deletionService = new ProfileDeletionService(Duration.ofSeconds(settings.execTimeoutSec));
                
                deletionService.deleteProfiles(host, selectedProfiles, 
                    message -> Platform.runLater(() -> updateStatus(message))
                );
                return null;
            }
        };

        deleteTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                updateStatus("Eliminazione completata con successo");
                
                // Rimuovi i profili eliminati dalla tabella
                profiles.removeAll(selectedProfiles);
                
                setUIEnabled(true);
                showInfo("Eliminazione Completata", 
                    selectedProfiles.size() + " profili eliminati con successo");
            });
        });

        deleteTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable exception = deleteTask.getException();
                String errorMsg = "ERRORE durante eliminazione: " + 
                    (exception != null ? exception.getMessage() : "Errore sconosciuto");
                updateStatus(errorMsg);
                showError("Errore Eliminazione", errorMsg);
                setUIEnabled(true);
            });
        });

        new Thread(deleteTask).start();
    }



    /**
     * Valida l'host inserito
     */
    private boolean validateHost(String host) {
        return host != null && !host.trim().isEmpty() && 
               (host.matches("^[a-zA-Z0-9.-]+$") || 
                host.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"));
    }

    /**
     * Abilita/disabilita i controlli UI
     */
    private void setUIEnabled(boolean enabled) {
        inventoryButton.setDisable(!enabled);
        deleteSelectedButton.setDisable(!enabled || profilesTable.getSelectionModel().getSelectedItems().isEmpty());
        hostField.setDisable(!enabled);
        includeLoadedCheckbox.setDisable(!enabled);
        includeSpecialCheckbox.setDisable(!enabled);
        progressBar.setVisible(!enabled);
        progressLabel.setVisible(!enabled);
        if (enabled) {
            progressBar.setProgress(0);
            progressLabel.setText("");
        }
    }

    /**
     * Aggiorna la status bar
     */
    private void updateStatus(String message) {
        if (statusLabel != null) {
            Platform.runLater(() -> statusLabel.setText(message));
        }
        
        // Log anche sulla console per debug
        System.out.println(message);
    }

    /**
     * Aggiorna la barra di progresso con percentuale
     */
    private void updateProgressBar(double progress, String message) {
        Platform.runLater(() -> {
            if (progressBar != null && progressLabel != null) {
                progressBar.setProgress(progress);
                int percentage = (int)(progress * 100);
                progressLabel.setText(percentage + "% - " + message);
            }
        });
    }

    private void updateProgressBar(double progress) {
        Platform.runLater(() -> {
            if (progressBar != null) {
                progressBar.setProgress(progress);
                int percentage = (int)(progress * 100);
                if (progressLabel != null) {
                    progressLabel.setText(percentage + "%");
                }
            }
        });
    }

    /**
     * Mostra dialogo di errore
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Mostra dialogo informativo
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Apre Esplora Risorse al percorso del profilo
     */
    private void openExplorer(Profile profile) {
        try {
            String host = hostField.getText().trim();
            String username = profile.userName();
            String uncPath = String.format("\\\\%s\\C$\\Users\\%s", host, username);
            
            // Prova ad aprire con explorer
            ProcessBuilder pb = new ProcessBuilder("explorer.exe", uncPath);
            pb.start();
            
            updateStatus("Apertura Esplora Risorse: " + uncPath);
            
        } catch (IOException e) {
            logger.error("Errore apertura Esplora Risorse", e);
            showError("Errore", "Impossibile aprire Esplora Risorse: " + e.getMessage());
        }
    }

    /**
     * Estrae i bytes da una stringa di dimensione per ordinamento corretto
     */
    private Long extractBytesFromSizeString(String sizeStr) {
        if (sizeStr == null || sizeStr.equals("Calcolando...")) {
            return 0L;
        }
        
        try {
            // Parse "1.25 GB" o "150.0 MB"
            String[] parts = sizeStr.split(" ");
            if (parts.length == 2) {
                double value = Double.parseDouble(parts[0]);
                String unit = parts[1].toUpperCase();
                
                switch (unit) {
                    case "GB":
                        return (long)(value * 1024 * 1024 * 1024);
                    case "MB":
                        return (long)(value * 1024 * 1024);
                    case "KB":
                        return (long)(value * 1024);
                    default:
                        return (long)value;
                }
            }
        } catch (NumberFormatException e) {
            // Fallback per errori di parsing
        }
        
        return 0L;
    }
}