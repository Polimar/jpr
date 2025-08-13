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
        List<String> cmd = List.of(
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
                paths.stream().map(p -> "'" + p.replace("'", "''") + "'").reduce((a, b) -> a + "," + b).orElse("") + ")";
        return fn + "\n" + call + "\n";
    }
}


