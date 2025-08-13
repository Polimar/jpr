package com.example.profileremover.core;

import com.example.profileremover.core.model.AppSettings;
import com.example.profileremover.core.persist.SettingsStore;

import java.io.File;
import java.nio.file.Path;

public final class AppContext {
    private static final SettingsStore settingsStore = new SettingsStore();
    private static AppSettings settings = settingsStore.load();

    private AppContext() {}

    public static AppSettings getSettings() {
        return settings;
    }

    public static void saveSettings(AppSettings s) throws Exception {
        settings = s;
        settingsStore.save(s);
    }

    public static File resolvePsExec() {
        // 1) From settings.pstoolsDir
        File fromSettings = tryDir(settings.pstoolsDir);
        if (fromSettings != null) return fromSettings;
        // 2) ./pstools relative to working dir
        File fromLocal = tryDir(Path.of(System.getProperty("user.dir"), "pstools").toString());
        if (fromLocal != null) return fromLocal;
        // 3) C:\PSTools
        File fromDefault = tryDir("C:/PSTools");
        if (fromDefault != null) return fromDefault;
        // 4) Fallback to name in PATH (less reliable)
        return new File("PsExec64.exe");
    }

    private static File tryDir(String dir) {
        if (dir == null || dir.isBlank()) return null;
        File d = new File(dir);
        if (!d.exists()) return null;
        File x64 = new File(d, "PsExec64.exe");
        if (x64.exists()) return x64;
        File x86 = new File(d, "PsExec.exe");
        if (x86.exists()) return x86;
        return null;
    }
}


