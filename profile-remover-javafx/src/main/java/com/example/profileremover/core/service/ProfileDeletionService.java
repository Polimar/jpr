package com.example.profileremover.core.service;

import com.example.profileremover.core.exec.WmiDirect;
import com.example.profileremover.core.model.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public class ProfileDeletionService {
    private final WmiDirect wmiDirect;
    private static final Logger logger = LoggerFactory.getLogger(ProfileDeletionService.class);

    public ProfileDeletionService(Duration timeout) {
        this.wmiDirect = new WmiDirect(timeout);
    }

    /**
     * Elimina i profili selezionati dalla macchina remota
     */
    public void deleteProfiles(String host, List<Profile> profilesToDelete, Consumer<String> onLog) throws Exception {
        if (profilesToDelete.isEmpty()) {
            onLog.accept("Nessun profilo da eliminare");
            return;
        }

        onLog.accept("Eliminazione di " + profilesToDelete.size() + " profili da " + host);

        for (Profile profile : profilesToDelete) {
            try {
                deleteProfile(host, profile, onLog);
            } catch (Exception e) {
                String errorMsg = "ERRORE eliminando " + profile.userName() + ": " + e.getMessage();
                onLog.accept(errorMsg);
                logger.error(errorMsg, e);
                // Continua con gli altri profili anche se uno fallisce
            }
        }

        onLog.accept("Operazione di eliminazione completata");
    }

    /**
     * Elimina un singolo profilo
     */
    private void deleteProfile(String host, Profile profile, Consumer<String> onLog) throws Exception {
        onLog.accept("Eliminando profilo: " + profile.userName() + " (SID: " + profile.sid() + ")");

        // Usa WMIC per eliminare il profilo tramite SID
        String wmicQuery = String.format(
            "path win32_userprofile where \"SID='%s'\" delete",
            profile.sid()
        );

        var result = wmiDirect.executeWmicQuery(host, wmicQuery, 
            line -> onLog.accept("WMI DEL: " + line)
        );

        if (result.exitCode == 0) {
            onLog.accept("✓ Profilo " + profile.userName() + " eliminato con successo");
        } else {
            String error = "Eliminazione fallita - ExitCode: " + result.exitCode;
            if (!result.stderr.isEmpty()) {
                error += " - " + result.stderr;
            }
            throw new RuntimeException(error);
        }
    }

    /**
     * Verifica che un profilo esista ancora prima dell'eliminazione
     */
    public boolean profileExists(String host, Profile profile, Consumer<String> onLog) throws Exception {
        String wmicQuery = String.format(
            "path win32_userprofile where \"SID='%s'\" get LocalPath /format:csv",
            profile.sid()
        );

        var result = wmiDirect.executeWmicQuery(host, wmicQuery, null);
        
        boolean exists = result.exitCode == 0 && 
                        result.stdout.contains("LocalPath") && 
                        result.stdout.contains(profile.localPath());
        
        onLog.accept("Verifica esistenza " + profile.userName() + ": " + (exists ? "ESISTE" : "NON ESISTE"));
        return exists;
    }

    /**
     * Ottiene informazioni dettagliate su un profilo specifico
     */
    public String getProfileDetails(String host, Profile profile, Consumer<String> onLog) throws Exception {
        String wmicQuery = String.format(
            "path win32_userprofile where \"SID='%s'\" get LocalPath,SID,Special,Loaded,LastUseTime,RoamingConfigured /format:csv",
            profile.sid()
        );

        var result = wmiDirect.executeWmicQuery(host, wmicQuery, null);
        
        if (result.exitCode == 0) {
            return result.stdout;
        } else {
            return "Errore ottenendo dettagli profilo: " + result.stderr;
        }
    }
}
