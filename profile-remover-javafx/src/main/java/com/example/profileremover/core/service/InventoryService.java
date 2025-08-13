package com.example.profileremover.core.service;

import com.example.profileremover.core.exec.PsExecRunner;
import com.example.profileremover.core.model.Profile;
import com.opencsv.CSVReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class InventoryService {
    private final PsExecRunner runner;

    public InventoryService(PsExecRunner runner) {
        this.runner = runner;
    }

    public List<Profile> listProfiles(String host, boolean includeLoaded, boolean includeSpecial,
                                      Consumer<String> onLog) throws Exception {
        var res = runner.runWmic(host,
                "path win32_userprofile get LocalPath,SID,Special,Loaded,LastUseTime /format:csv",
                line -> onLog.accept("OUT: " + line),
                line -> onLog.accept("ERR: " + line)
        );
        onLog.accept("ExitCode=" + res.exitCode());

        List<Profile> out = new ArrayList<>();
        try (CSVReader r = new CSVReader(new StringReader(res.stdout()))) {
            String[] row;
            boolean headerSkipped = false;
            while ((row = r.readNext()) != null) {
                if (row.length < 6) continue;
                if (!headerSkipped && row[0].equalsIgnoreCase("Node")) {
                    headerSkipped = true;
                    continue;
                }
                String lastUse = row[1];
                boolean loaded = Boolean.parseBoolean(Optional.ofNullable(row[2]).orElse("false"));
                String localPath = row[3];
                String sid = row[4];
                boolean special = Boolean.parseBoolean(Optional.ofNullable(row[5]).orElse("false"));
                if (localPath == null || !localPath.toLowerCase().startsWith("c\\\\users\\")) continue;
                if (!includeLoaded && loaded) continue;
                if (!includeSpecial && special) continue;

                String folder = localPath.substring(localPath.lastIndexOf('\\') + 1);
                String userName = folder;
                out.add(new Profile(localPath, sid, userName, special, loaded, lastUse, null));
            }
        }
        return out;
    }
}


