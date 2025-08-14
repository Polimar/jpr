package com.example.profileremover.core.exec;



import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WmiDirect {
    private final Duration timeout;

    public WmiDirect(Duration timeout) {
        this.timeout = timeout;
    }

    public ProcessResult getRemoteUserProfiles(String host, Consumer<String> onOutput) throws Exception {
        // Usa WMIC direttamente con /node per accesso remoto via DCOM
        // Questo funziona senza WinRM e senza PsExec
        String command = String.format(
            "wmic /node:\"%s\" path win32_userprofile get LocalPath,SID,Special,Loaded,LastUseTime /format:csv",
            host
        );

        return executeLocalCommand(command, onOutput);
    }

    public ProcessResult countUserProfiles(String host, Consumer<String> onOutput) throws Exception {
        // Conta solo i profili utente, per il progresso
        String command = String.format(
            "wmic /node:\"%s\" path win32_userprofile where \"LocalPath like '%%Users%%'\" get LocalPath /format:csv",
            host
        );

        return executeLocalCommand(command, onOutput);
    }
    
    public ProcessResult getProfileSize(String host, String profilePath, Consumer<String> onOutput) throws Exception {
        // Ottieni la dimensione di una cartella tramite WMI
        // Usa PowerShell per calcolare la dimensione ricorsiva

        String command = String.format(
            "wmic /node:\"%s\" process call create \"powershell.exe -Command \\\"(Get-ChildItem -Path '%s' -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum\\\"\"",
            host, profilePath
        );

        return executeLocalCommand(command, onOutput);
    }

    public ProcessResult testConnection(String host, Consumer<String> onOutput) throws Exception {
        // Test semplice: ottieni informazioni del sistema remoto
        String command = String.format(
            "wmic /node:\"%s\" computersystem get Name,Manufacturer,Model /format:csv",
            host
        );

        return executeLocalCommand(command, onOutput);
    }

    public ProcessResult executeWmicQuery(String host, String wmicQuery, Consumer<String> onOutput) throws Exception {
        // Esegue una query WMIC generica sull'host remoto
        String command = String.format("wmic /node:\"%s\" %s", host, wmicQuery);
        return executeLocalCommand(command, onOutput);
    }

    public ProcessResult executeLocalCommand(String command, Consumer<String> onOutput) throws Exception {
        if (onOutput != null) {
            String prefix = command.contains("powershell") ? "PowerShell" : "WMIC";
            onOutput.accept(prefix + ": " + command);
        }

        // Usa cmd.exe per eseguire WMIC localmente che si connette via DCOM al remoto
        // Forza UTF-8 per evitare problemi di codifica
        List<String> cmd = List.of(
            "cmd.exe",
            "/c",
            "chcp 65001 >nul && " + command
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); // Combina stdout e stderr
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip righe vuote
                if (line.trim().isEmpty()) continue;
                
                output.append(line).append("\n");
                if (onOutput != null) {
                    onOutput.accept(line);
                }
            }
        }

        boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout after " + timeout.toSeconds() + " seconds");
        }

        return new ProcessResult(process.exitValue(), output.toString(), errors.toString());
    }

    public static class ProcessResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
