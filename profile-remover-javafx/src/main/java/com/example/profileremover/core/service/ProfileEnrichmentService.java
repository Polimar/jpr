package com.example.profileremover.core.service;

import com.example.profileremover.core.exec.WmiDirect;
import com.example.profileremover.core.model.AppSettings;
import com.example.profileremover.core.model.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProfileEnrichmentService {
    private final WmiDirect wmiDirect;
    private static final Logger logger = LoggerFactory.getLogger(ProfileEnrichmentService.class);

    public ProfileEnrichmentService(AppSettings settings) throws Exception {
        this.wmiDirect = new WmiDirect(Duration.ofSeconds(settings.execTimeoutSec));
    }

    /**
     * Arricchisce i profili con username corretto, nome completo locale e dimensioni
     */
    public List<Profile> enrichProfiles(String host, List<Profile> profiles, Consumer<String> onLog) {
        List<Profile> enrichedProfiles = new ArrayList<>();
        
        int total = profiles.size();
        int current = 0;
        
        for (Profile profile : profiles) {
            current++;
            onLog.accept(String.format("Elaborazione profilo %d/%d: %s", current, total, profile.userName()));
            try {
                // Estrai username corretto dal LocalPath
                String correctUserName = extractUsernameFromPath(profile.localPath());
                
                // Ottieni nome completo dalla macchina locale
                String displayName = getLocalUserDisplayName(host, correctUserName, onLog);
                
                // Ottieni ultima connessione utente
                String lastLogin = getLastLoginTime(host, correctUserName, onLog);
                
                Long sizeBytes = getProfileSizeFromWmi(host, profile.localPath(), onLog);
                String realLastModified = getRealFolderDate(host, profile.localPath(), onLog);
                
                // Correggi i backslash nel localPath
                String correctedPath = fixLocalPath(profile.localPath());
                
                Profile enrichedProfile = new Profile(
                    correctedPath,
                    profile.sid(),
                    correctUserName, // Username corretto dal path
                    profile.special(),
                    profile.loaded(),
                    realLastModified != null ? realLastModified : profile.lastUseTime(),
                    sizeBytes,
                    displayName, // Nome completo dalla macchina
                    lastLogin
                );
                
                enrichedProfiles.add(enrichedProfile);
                
            } catch (Exception e) {
                onLog.accept("Errore arricchimento profilo " + profile.userName() + ": " + e.getMessage());
                logger.warn("Failed to enrich profile: " + profile.userName(), e);
                
                // Aggiungi comunque il profilo con dati parziali
                String correctedPath = fixLocalPath(profile.localPath());
                String correctUserName = extractUsernameFromPath(profile.localPath());
                Profile partialProfile = new Profile(
                    correctedPath,
                    profile.sid(),
                    correctUserName,
                    profile.special(),
                    profile.loaded(),
                    profile.lastUseTime(),
                    null,
                    null,
                    null
                );
                enrichedProfiles.add(partialProfile);
            }
        }
        
        return enrichedProfiles;
    }

    /**
     * Estrae l'username dalla parte finale del LocalPath
     */
    private String extractUsernameFromPath(String localPath) {
        if (localPath == null || localPath.trim().isEmpty()) {
            return "unknown";
        }
        
        String path = localPath.trim();
        // Se contiene backslash, prendi l'ultima parte
        if (path.contains("\\")) {
            return path.substring(path.lastIndexOf('\\') + 1);
        }
        
        // Se è del tipo "C:Usersusername", estrai username
        if (path.toLowerCase().startsWith("c:users") && path.length() > 7) {
            return path.substring(7); // Era 8, doveva essere 7
        }
        
        return path;
    }

    /**
     * Ottiene il nome completo dell'utente dalla macchina locale
     */
    private String getLocalUserDisplayName(String host, String userName, Consumer<String> onLog) {
        try {
            onLog.accept("🔍 Ricerca nome completo per: " + userName);
            
            // Usa WMI per ottenere informazioni utente
            String command = String.format(
                "wmic /node:\"%s\" useraccount where \"name='%s'\" get fullname /format:csv",
                host, userName
            );
            
            var result = wmiDirect.executeLocalCommand(command, null);
            
            if (result.exitCode == 0 && result.stdout != null) {
                String[] lines = result.stdout.split("\n");
                for (String line : lines) {
                    if (line.contains(",") && !line.toLowerCase().contains("fullname")) {
                        String[] parts = line.split(",");
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            String fullName = parts[1].trim();
                            onLog.accept("✅ Nome completo trovato: " + userName + " -> " + fullName);
                            return fullName;
                        }
                    }
                }
            }
            
            // Fallback: prova con net user
            String netCommand = String.format(
                "wmic /node:\"%s\" process call create \"net user %s\"",
                host, userName
            );
            
            var netResult = wmiDirect.executeLocalCommand(netCommand, null);
            if (netResult.exitCode == 0) {
                onLog.accept("✅ Nome utente valido: " + userName);
                return userName + " (Utente Locale)";
            }
            
        } catch (Exception e) {
            onLog.accept("❌ Errore ricerca nome per " + userName + ": " + e.getMessage());
            logger.warn("Failed to get display name for user: " + userName, e);
        }
        
        return null; // Usa solo username
    }

    /**
     * Ottiene l'ultima connessione dell'utente
     */
    private String getLastLoginTime(String host, String userName, Consumer<String> onLog) {
        try {
            onLog.accept("🔍 Ricerca ultimo login per: " + userName);
            
            // Prova con WMI useraccount
            String command = String.format(
                "wmic /node:\"%s\" useraccount where \"name='%s'\" get lastlogin /format:csv",
                host, userName
            );
            
            var result = wmiDirect.executeLocalCommand(command, null);
            
            if (result.exitCode == 0 && result.stdout != null) {
                String[] lines = result.stdout.split("\n");
                for (String line : lines) {
                    if (line.contains(",") && !line.toLowerCase().contains("lastlogin")) {
                        String[] parts = line.split(",");
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            String lastLogin = parts[1].trim();
                            onLog.accept("✅ Ultimo login trovato: " + userName + " -> " + lastLogin);
                            return formatLastLogin(lastLogin);
                        }
                    }
                }
            }
            
            // Fallback: prova con eventi di sistema
            String eventCommand = String.format(
                "wmic /node:\"%s\" process call create \"wevtutil qe Security /q:\"*[System[EventID=4624 and EventData[Data[@Name='TargetUserName']='%s']]]\" /c:1 /f:text\"",
                host, userName
            );
            
            var eventResult = wmiDirect.executeLocalCommand(eventCommand, null);
            if (eventResult.exitCode == 0 && eventResult.stdout != null) {
                onLog.accept("✅ Evento login trovato per: " + userName);
                return "Evento trovato";
            }
            
        } catch (Exception e) {
            onLog.accept("❌ Errore ricerca ultimo login per " + userName + ": " + e.getMessage());
            logger.warn("Failed to get last login for user: " + userName, e);
        }
        
        return "N/A";
    }
    
    /**
     * Formatta la data di ultimo login
     */
    private String formatLastLogin(String lastLogin) {
        if (lastLogin == null || lastLogin.trim().isEmpty()) {
            return "N/A";
        }
        
        // Se è già nel formato corretto, ritorna
        if (lastLogin.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
            return lastLogin;
        }
        
        // Se è un timestamp WMI, converti
        if (lastLogin.matches("\\d{14}.*")) {
            try {
                String year = lastLogin.substring(0, 4);
                String month = lastLogin.substring(4, 6);
                String day = lastLogin.substring(6, 8);
                String hour = lastLogin.substring(8, 10);
                String minute = lastLogin.substring(10, 12);
                return String.format("%s-%s-%s %s:%s", year, month, day, hour, minute);
            } catch (Exception e) {
                return lastLogin;
            }
        }
        
        return lastLogin;
    }

    private Long getProfileSizeFromWmi(String host, String profilePath, Consumer<String> onLog) {
        try {
            onLog.accept("Calcolo dimensione reale: " + profilePath);
            
            // Prima correggi i backslash nel path
            String fixedPath = fixLocalPath(profilePath);
            onLog.accept("Path corretto: " + fixedPath);
            
            // Fix del path per Windows remoto
            String remotePath = fixedPath.replace("C:", "C$");
            String uncPath = "\\\\" + host + "\\" + remotePath;
            
            onLog.accept("UNC Path: " + uncPath);
            
            // Usa PowerShell per calcolare dimensione tramite UNC
            String psCommand = String.format(
                "powershell.exe -Command \"try { (Get-ChildItem -Path '%s' -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum } catch { 0 }\"",
                uncPath
            );
            
            var result = wmiDirect.executeLocalCommand(psCommand, null); // Nessun log PS
            
            if (result.exitCode == 0 && result.stdout != null && !result.stdout.trim().isEmpty()) {
                String output = result.stdout.trim();
                
                try {
                    Long size = Long.parseLong(output);
                    if (size > 0) {
                        double gb = size / (1024.0 * 1024.0 * 1024.0);
                        onLog.accept("Dimensione reale: " + String.format("%.2f GB", gb));
                        return size;
                    }
                } catch (NumberFormatException e) {
                    onLog.accept("Errore parsing dimensione: " + output);
                }
            }
            
        } catch (Exception e) {
            onLog.accept("Errore calcolo dimensione per " + profilePath + ": " + e.getMessage());
            logger.warn("Failed to get profile size: " + profilePath, e);
        }
        
        // Fallback: ritorna null per mostrare "Calcolando..."
        return null;
    }

    /**
     * Ottiene la data di ultima modifica reale della cartella (non da WMI)
     */
    private String getRealFolderDate(String host, String profilePath, Consumer<String> onLog) {
        try {
            onLog.accept("Calcolo data reale: " + profilePath);
            
            // Prima correggi i backslash nel path
            String fixedPath = fixLocalPath(profilePath);
            onLog.accept("Path corretto per data: " + fixedPath);
            
            // Fix del path per Windows remoto
            String remotePath = fixedPath.replace("C:", "C$");
            String uncPath = "\\\\" + host + "\\" + remotePath;
            
            // PowerShell per ottenere LastWriteTime della cartella
            String psCommand = String.format(
                "powershell.exe -Command \"try { (Get-Item -Path '%s' -ErrorAction SilentlyContinue).LastWriteTime.ToString('yyyy-MM-dd HH:mm') } catch { 'N/A' }\"",
                uncPath
            );
            
            var result = wmiDirect.executeLocalCommand(psCommand, null); // Nessun log PS
            
            if (result.exitCode == 0 && result.stdout != null && !result.stdout.trim().isEmpty()) {
                String dateResult = result.stdout.trim();
                onLog.accept("Data reale trovata: " + dateResult);
                
                if (!dateResult.equals("N/A") && !dateResult.isEmpty()) {
                    return dateResult;
                }
            }
            
        } catch (Exception e) {
            onLog.accept("Errore calcolo data per " + profilePath + ": " + e.getMessage());
            logger.warn("Failed to get real folder date: " + profilePath, e);
        }
        
        return null; // Fallback al valore WMI originale
    }

    private String fixLocalPath(String localPath) {
        if (localPath == null) {
            return null;
        }
        
        // Se mancano i backslash, li ricostruiamo
        if (localPath.startsWith("C:Users") && !localPath.contains("\\")) {
            // Formato: C:Usersusername -> C: + Users + username
            String username = localPath.substring(7); // Rimuovi "C:Users"
            return "C:\\Users\\" + username;
        }
        if (localPath.startsWith("C:Windows") && !localPath.contains("\\")) {
            // Formato: C:WindowsServiceProfiles... -> C: + Windows + ServiceProfiles...
            String remainder = localPath.substring(9); // Rimuovi "C:Windows"
            return "C:\\Windows\\" + remainder.replace("ServiceProfiles", "ServiceProfiles\\")
                                              .replace("LocalService", "LocalService")
                                              .replace("NetworkService", "NetworkService")
                                              .replace("system32config", "system32\\config\\");
        }
        
        // Se già ha i backslash, lasciamo com'è
        return localPath;
    }

    public void close() {
        // Nessun LDAP da chiudere
    }
}