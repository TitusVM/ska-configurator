package com.pki.model;

import java.io.File;

/**
 * Wraps an {@link SkaConfig} together with its source file and per-file state
 * (loaded version, dirty flag). Used by {@link SkaWorkspace} to manage
 * multiple SKA configurations simultaneously.
 */
public class SkaConfigEntry {

    private final SkaConfig config;
    private File sourceFile;
    private int loadedVersion;
    private boolean dirty;

    public SkaConfigEntry(SkaConfig config, File sourceFile) {
        this.config = config;
        this.sourceFile = sourceFile;
        this.loadedVersion = config.getVersion();
        this.dirty = false;
    }

    public SkaConfig getConfig() { return config; }

    public File getSourceFile() { return sourceFile; }
    public void setSourceFile(File sourceFile) { this.sourceFile = sourceFile; }

    public int getLoadedVersion() { return loadedVersion; }
    public void setLoadedVersion(int loadedVersion) { this.loadedVersion = loadedVersion; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }

    /**
     * Display label for UI selectors: "moduleName (filename)" or just filename.
     */
    public String getDisplayLabel() {
        String name = config.getModuleName();
        String file = sourceFile != null ? sourceFile.getName() : "new";
        if (name == null || name.isEmpty()) {
            return file;
        }
        return name + " (" + file + ")";
    }

    /**
     * Display label with dirty indicator.
     */
    public String getDisplayLabelWithDirty() {
        return dirty ? getDisplayLabel() + " *" : getDisplayLabel();
    }

    @Override
    public String toString() {
        return getDisplayLabelWithDirty();
    }
}
