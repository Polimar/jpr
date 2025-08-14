package com.example.profileremover.core.model;

public record Profile(
        String localPath,
        String sid,
        String userName,
        boolean special,
        boolean loaded,
        String lastUseTime,
        Long sizeBytes,
        String displayName,
        String lastLogin
) {
    // Metodi di utility per la visualizzazione
    public String getFormattedLastUse() {
        if (lastUseTime == null || lastUseTime.trim().isEmpty()) {
            return "N/A";
        }
        
        try {
            // Gestisci diversi formati di data
            String dateStr = lastUseTime.trim();
            
            // Se è già nel formato "yyyy-MM-dd HH:mm" (da getRealFolderDate), usa quello
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
                return dateStr;
            }
            
            // Altrimenti parse WMIC timestamp: YYYYMMDDHHMMSS.ffffff+/-UUU
            if (dateStr.length() >= 14 && dateStr.matches("\\d{14}.*")) {
                String year = dateStr.substring(0, 4);
                String month = dateStr.substring(4, 6);
                String day = dateStr.substring(6, 8);
                String hour = dateStr.substring(8, 10);
                String minute = dateStr.substring(10, 12);
                
                return String.format("%s-%s-%s %s:%s", year, month, day, hour, minute);
            }
        } catch (Exception e) {
            // Se il parsing fallisce, ritorna il valore originale troncato se troppo lungo
            if (lastUseTime.length() > 16) {
                return lastUseTime.substring(0, 16);
            }
        }
        
        return lastUseTime;
    }
    
    public String getFormattedSize() {
        if (sizeBytes == null || sizeBytes == 0) {
            return "Calcolando...";
        }
        
        double gb = sizeBytes / (1024.0 * 1024.0 * 1024.0);
        if (gb < 0.1) {
            double mb = sizeBytes / (1024.0 * 1024.0);
            return String.format("%.1f MB", mb);
        }
        
        return String.format("%.2f GB", gb);
    }

    /**
     * Restituisce la dimensione in bytes per ordinamento corretto
     */
    public Long getSizeBytesForSorting() {
        return sizeBytes != null ? sizeBytes : 0L;
    }
    
    public String getDisplayNameOrUser() {
        return (displayName != null && !displayName.trim().isEmpty()) ? displayName : userName;
    }
    
    public String getLastLogin() {
        return (lastLogin != null && !lastLogin.trim().isEmpty()) ? lastLogin : "N/A";
    }
}


