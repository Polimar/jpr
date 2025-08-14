package com.example.profileremover.core.service;

import com.example.profileremover.core.exec.WmiDirect;
import com.example.profileremover.core.model.Profile;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.time.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class InventoryService {
    private final WmiDirect wmiDirect;
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    public InventoryService(Duration timeout) {
        this.wmiDirect = new WmiDirect(timeout);
    }

    /**
     * Ottiene la lista dei profili utente dalla macchina remota
     */
    public List<Profile> listProfiles(String host, boolean includeLoaded, boolean includeSpecial,
                                      Consumer<String> onLog) throws Exception {
        
        onLog.accept("Connessione WMI a " + host + "...");
        
        var result = wmiDirect.getRemoteUserProfiles(host, 
            line -> onLog.accept("WMI: " + line)
        );
        
        onLog.accept("Comando completato - ExitCode: " + result.exitCode);
        
        if (result.exitCode != 0) {
            throw new RuntimeException("WMI fallito - ExitCode: " + result.exitCode + 
                                     (result.stderr.isEmpty() ? "" : " - " + result.stderr));
        }

        List<Profile> profiles = parseWmiCsvOutput(result.stdout, includeLoaded, includeSpecial, onLog);
        onLog.accept("Trovati " + profiles.size() + " profili utente");
        
        return profiles;
    }

    /**
     * Parsa l'output CSV di WMIC per estrarre i profili
     */
    private List<Profile> parseWmiCsvOutput(String csvData, boolean includeLoaded, 
                                          boolean includeSpecial, Consumer<String> onLog) throws Exception {
        List<Profile> profiles = new ArrayList<>();
        
        if (csvData == null || csvData.trim().isEmpty()) {
            onLog.accept("WARN: Output WMI vuoto");
            return profiles;
        }

        try (CSVReader reader = new CSVReader(new StringReader(csvData))) {
            String[] row;
            boolean headerFound = false;
            List<String> headers = null;
            int idxPath = -1, idxSid = -1, idxSpecial = -1, idxLoaded = -1, idxLastUse = -1;

            while ((row = reader.readNext()) != null) {
                // Skip righe vuote
                if (row.length == 0 || (row.length == 1 && row[0].trim().isEmpty())) {
                    continue;
                }

                if (!headerFound) {
                    // Cerca l'header che contiene "LocalPath"
                    if (Arrays.asList(row).contains("LocalPath")) {
                        headers = Arrays.asList(row);
                        idxPath = headers.indexOf("LocalPath");
                        idxSid = headers.indexOf("SID");
                        idxSpecial = headers.indexOf("Special");
                        idxLoaded = headers.indexOf("Loaded");
                        idxLastUse = headers.indexOf("LastUseTime");
                        headerFound = true;
                        onLog.accept("Header CSV trovato con " + headers.size() + " colonne");
                        continue;
                    }
                    continue;
                }

                // Processa i dati del profilo
                if (row.length < headers.size() || idxPath == -1 || idxSid == -1) {
                    continue;
                }

                String localPath = row[idxPath] != null ? row[idxPath].trim() : "";
                String sid = row[idxSid] != null ? row[idxSid].trim() : "";

                // Skip profili vuoti o non validi  
                if (localPath.isEmpty() || sid.isEmpty()) {
                    onLog.accept("Skip profilo vuoto: localPath='" + localPath + "' sid='" + sid + "'");
                    continue;
                }
                
                // Verifica se è un profilo utente (più flessibile)
                if (!localPath.toLowerCase().contains("users") && !localPath.toLowerCase().contains("\\users\\")) {
                    onLog.accept("Skip profilo non utente: " + localPath);
                    continue;
                }

                // Skip prima riga se contiene il nome del nodo (tipico in WMIC)
                if (localPath.toLowerCase().contains("node") || sid.toLowerCase().contains("node")) {
                    continue;
                }

                boolean loaded = false;
                boolean special = false;
                String lastUseTime = "";

                if (idxLoaded > -1 && row.length > idxLoaded && row[idxLoaded] != null) {
                    String loadedStr = row[idxLoaded].trim().toLowerCase();
                    loaded = "true".equals(loadedStr) || "1".equals(loadedStr);
                }

                if (idxSpecial > -1 && row.length > idxSpecial && row[idxSpecial] != null) {
                    String specialStr = row[idxSpecial].trim().toLowerCase();
                    special = "true".equals(specialStr) || "1".equals(specialStr);
                }

                if (idxLastUse > -1 && row.length > idxLastUse && row[idxLastUse] != null) {
                    lastUseTime = row[idxLastUse].trim();
                }

                // Estrai username dal path
                String userName = localPath.substring(localPath.lastIndexOf('\\') + 1);

                // Debug info
                onLog.accept("Profilo: " + userName + " - loaded=" + loaded + " special=" + special + 
                           " (includeLoaded=" + includeLoaded + " includeSpecial=" + includeSpecial + ")");

                // Applica filtri
                if (!includeLoaded && loaded) {
                    onLog.accept("Skip profilo caricato: " + localPath);
                    continue;
                }
                if (!includeSpecial && special) {
                    onLog.accept("Skip profilo speciale: " + localPath);
                    continue;
                }



                Profile profile = new Profile(localPath, sid, userName, special, loaded, 
                                            lastUseTime, null, null, null);
                profiles.add(profile);
                
                onLog.accept("✓ Aggiunto profilo: " + userName);
            }

        } catch (Exception e) {
            onLog.accept("ERRORE parsing CSV: " + e.getMessage());
            logger.error("Errore parsing CSV WMI", e);
            throw e;
        }

        return profiles;
    }


}