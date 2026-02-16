package com.pki.gui;

import com.pki.io.CsvImporter;
import com.pki.io.SkaXmlReader;
import com.pki.io.SkaXmlWriter;
import com.pki.model.SkaConfig;
import com.pki.model.SkaConfigEntry;
import com.pki.model.SkaWorkspace;
import com.pki.model.User;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

/**
 * Main application window. Contains the menu bar, a tabbed pane for
 * global config / sections / users, and a status bar.
 */
public class MainFrame extends JFrame {

    /**
     * Creates a JFileChooser with shell-folder integration disabled.
     * On Windows the default JFileChooser queries mapped network drives,
     * shell extensions and thumbnail providers which can take 10-15 seconds.
     * Setting {@code FileChooser.useShellFolder} to {@code false} avoids this.
     */
    private static JFileChooser fastFileChooser(File startDir) {
        // Disable Windows shell folder integration before construction
        UIManager.put("FileChooser.useShellFolder", Boolean.FALSE);
        JFileChooser chooser = new JFileChooser(startDir);
        return chooser;
    }

    private static JFileChooser fastFileChooser(String path) {
        return fastFileChooser(new File(path));
    }

    private SkaConfig config;
    private File currentFile;
    private boolean dirty = false;
    private int loadedVersion = -1;  // version at load time, -1 = new file

    private final SkaWorkspace workspace = new SkaWorkspace();
    private File workspaceFolder;  // non-null when a folder was opened
    private Boolean loadEnvironmentIntegration = null; // null = not asked yet this session
    private String sessionEnvironmentName = ""; // free-text env label for filename

    private final GlobalConfigPanel globalConfigPanel;
    private final SectionPanel organizationPanel;
    private final SectionPanel skaPlusPanel;
    private final SectionPanel skaModifyPanel;
    private final KeysProtoPanel keysProtoPanel;
    private final UsersPanel usersPanel;
    private final JTabbedPane tabbedPane;
    private final JLabel statusBar;
    private final JRadioButton prodRadio;
    private final JRadioButton integrationRadio;
    private final JComboBox<String> skaSelector;
    private final JLabel skaSelectorLabel;
    private boolean skaSelectorUpdating = false;  // guard against listener re-entry

    public MainFrame() {
        super("SKA Configurator");
        this.config = new SkaConfig();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doExit();
            }
        });
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Menu bar
        setJMenuBar(createMenuBar());

        // Environment toggle toolbar
        JToolBar envToolBar = new JToolBar();
        envToolBar.setFloatable(false);
        envToolBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        envToolBar.add(new JLabel("Environment:  "));
        prodRadio = new JRadioButton("Prod", true);
        integrationRadio = new JRadioButton("Integration");
        prodRadio.setFont(prodRadio.getFont().deriveFont(Font.BOLD));
        integrationRadio.setFont(integrationRadio.getFont().deriveFont(Font.BOLD));
        ButtonGroup envGroup = new ButtonGroup();
        envGroup.add(prodRadio);
        envGroup.add(integrationRadio);
        prodRadio.addActionListener(e -> onEnvironmentChanged());
        integrationRadio.addActionListener(e -> onEnvironmentChanged());
        envToolBar.add(prodRadio);
        envToolBar.add(Box.createHorizontalStrut(8));
        envToolBar.add(integrationRadio);
        envToolBar.add(Box.createHorizontalStrut(24));
        JLabel envHint = new JLabel("(affects which userID column is used from CSV / written to XML)");
        envHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        envToolBar.add(envHint);

        // SKA selector (visible only in folder mode)
        envToolBar.add(Box.createHorizontalStrut(24));
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setMaximumSize(new Dimension(2, 28));
        envToolBar.add(sep);
        envToolBar.add(Box.createHorizontalStrut(12));
        skaSelectorLabel = new JLabel("SKA: ");
        skaSelectorLabel.setFont(skaSelectorLabel.getFont().deriveFont(Font.BOLD));
        skaSelectorLabel.setVisible(false);
        envToolBar.add(skaSelectorLabel);
        skaSelector = new JComboBox<>();
        skaSelector.setMaximumSize(new Dimension(400, 28));
        skaSelector.setVisible(false);
        skaSelector.addActionListener(e -> {
            if (skaSelectorUpdating) return;
            int idx = skaSelector.getSelectedIndex();
            if (idx >= 0) switchToEntry(idx);
        });
        envToolBar.add(skaSelector);

        add(envToolBar, BorderLayout.NORTH);

        // Tabbed pane
        tabbedPane = new JTabbedPane();
        globalConfigPanel = new GlobalConfigPanel();
        organizationPanel = new SectionPanel();
        skaPlusPanel = new SectionPanel();
        skaModifyPanel = new SectionPanel();
        keysProtoPanel = new KeysProtoPanel();
        usersPanel = new UsersPanel();
        usersPanel.setStatusCallback(this::setStatus);
        usersPanel.setDirtyCallback(this::markDirty);
        usersPanel.setRefreshCallback(this::loadModelIntoUI);

        // Wire user list supplier so operation panels can pick users
        java.util.function.Supplier<java.util.List<User>> userSupplier = () -> config.getUsers();
        organizationPanel.setUserListSupplier(userSupplier);
        skaPlusPanel.setUserListSupplier(userSupplier);
        skaModifyPanel.setUserListSupplier(userSupplier);
        keysProtoPanel.setUserListSupplier(userSupplier);

        tabbedPane.addTab("Global Config", globalConfigPanel);
        tabbedPane.addTab("Organization", organizationPanel);
        tabbedPane.addTab("SKA Plus", skaPlusPanel);
        tabbedPane.addTab("SKA Modify", skaModifyPanel);
        tabbedPane.addTab("Keys", keysProtoPanel);
        tabbedPane.addTab("Users", usersPanel);

        // Update Keys tab label dynamically when user types in the keys child name field
        globalConfigPanel.setKeysChildNameChangeListener(name -> {
            int idx = tabbedPane.indexOfComponent(keysProtoPanel);
            if (idx >= 0) {
                tabbedPane.setTitleAt(idx, name.isEmpty() ? "Keys" : "Keys (" + name + ")");
            }
        });
        add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        statusBar = new JLabel("  Ready — No file loaded");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        add(statusBar, BorderLayout.SOUTH);

        loadModelIntoUI();
    }

    // --- Public API for later phases ---

    public SkaConfig getConfig() { return config; }

    public JTabbedPane getTabbedPane() { return tabbedPane; }

    public SkaWorkspace getWorkspace() { return workspace; }

    public boolean isWorkspaceMode() { return workspaceFolder != null; }

    /**
     * Switch the active entry in the workspace selector (called from 10.3 combo box).
     * Collects UI into the current model, then loads the new entry.
     */
    public void switchToEntry(int index) {
        if (index < 0 || index >= workspace.getEntries().size()) return;
        if (index == workspace.getActiveIndex()) return;

        // Save current UI state into current model
        collectUIIntoModel();

        workspace.setActiveIndex(index);
        SkaConfigEntry entry = workspace.getActiveEntry();
        this.config = entry.getConfig();
        this.currentFile = entry.getSourceFile();
        this.dirty = entry.isDirty();
        this.loadedVersion = entry.getLoadedVersion();

        loadModelIntoUI();
        refreshSkaSelector();
        setStatus("Switched to: " + entry.getDisplayLabel());
    }

    public void setStatus(String message) {
        statusBar.setText("  " + message);
    }

    // --- Environment toggle ---

    private void onEnvironmentChanged() {
        boolean isIntegration = integrationRadio.isSelected();
        config.setIntegrationEnvironment(isIntegration);
        usersPanel.setIntegrationEnvironment(isIntegration);
        String env = isIntegration ? "Integration" : "Prod";
        setStatus("Environment switched to " + env + " — userID column updated");
        markDirty();
    }

    // --- Menu bar ---

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');

        JMenuItem newItem = new JMenuItem("New Configuration");
        newItem.setAccelerator(KeyStroke.getKeyStroke("control N"));
        newItem.addActionListener(e -> doNew());

        JMenuItem openItem = new JMenuItem("Open SKA XML…");
        openItem.setAccelerator(KeyStroke.getKeyStroke("control O"));
        openItem.addActionListener(e -> doOpen());
        JMenuItem openFolderItem = new JMenuItem("Open SKA Folder\u2026");
        openFolderItem.setAccelerator(KeyStroke.getKeyStroke("control shift O"));
        openFolderItem.addActionListener(e -> doOpenFolder());
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
        saveItem.addActionListener(e -> doSave());

        JMenuItem saveAsItem = new JMenuItem("Save As…");
        saveAsItem.addActionListener(e -> doSaveAs());

        JMenuItem saveAllItem = new JMenuItem("Save All");
        saveAllItem.setAccelerator(KeyStroke.getKeyStroke("control shift S"));
        saveAllItem.addActionListener(e -> doSaveAll());

        JMenuItem importCsvItem = new JMenuItem("Import Users from CSV…");
        importCsvItem.setAccelerator(KeyStroke.getKeyStroke("control I"));
        importCsvItem.addActionListener(e -> doImportCsv());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> doExit());

        fileMenu.add(newItem);
        fileMenu.addSeparator();
        fileMenu.add(openItem);
        fileMenu.add(openFolderItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.add(saveAllItem);
        fileMenu.addSeparator();
        fileMenu.add(importCsvItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        return menuBar;
    }

    // --- File operations ---

    private void doNew() {
        if (!confirmDiscardChanges("Create New")) return;

        this.config = new SkaConfig();
        this.currentFile = null;
        this.dirty = false;
        this.loadedVersion = -1;

        workspace.clear();
        workspaceFolder = null;
        SkaConfigEntry entry = new SkaConfigEntry(config, null);
        workspace.addEntry(entry);

        loadModelIntoUI();
        refreshSkaSelector();
        setStatus("New configuration created");
    }

    private void doOpen() {
        if (!confirmDiscardChanges("Open File")) return;

        JFileChooser chooser = fastFileChooser(".");
        chooser.setDialogTitle("Open SKA XML Configuration");
        chooser.setFileFilter(new FileNameExtensionFilter("XML files (*.xml)", "xml"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            boolean isIntegration = promptLoadEnvironment();

            SkaXmlReader reader = new SkaXmlReader();
            this.config = reader.read(file);
            applyLoadEnvironmentToUsers(config.getUsers(), isIntegration);
            config.setIntegrationEnvironment(isIntegration);

            this.currentFile = file;
            this.dirty = false;
            this.loadedVersion = config.getVersion();

            workspace.clear();
            workspaceFolder = null;
            SkaConfigEntry entry = new SkaConfigEntry(config, file);
            entry.setLoadedVersion(loadedVersion);
            workspace.addEntry(entry);

            loadModelIntoUI();
            refreshSkaSelector();
            setStatus("Loaded: " + file.getName() + "  (" + config.getUsers().size() + " users)");

            promptCsvVerification();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doOpenFolder() {
        if (!confirmDiscardChanges("Open Folder")) return;

        JFileChooser chooser = fastFileChooser(".");
        chooser.setDialogTitle("Open Folder of SKA XML Configurations");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File folder = chooser.getSelectedFile();
        File[] xmlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "No XML files found in:\n" + folder.getAbsolutePath(),
                    "Open Folder", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        workspace.clear();
        workspaceFolder = folder;
        java.util.Arrays.sort(xmlFiles, java.util.Comparator.comparing(File::getName));

        boolean isIntegration = promptLoadEnvironment();

        SkaXmlReader reader = new SkaXmlReader();
        int errorCount = 0;
        StringBuilder errors = new StringBuilder();

        for (File f : xmlFiles) {
            try {
                SkaConfig cfg = reader.read(f);
                applyLoadEnvironmentToUsers(cfg.getUsers(), isIntegration);
                cfg.setIntegrationEnvironment(isIntegration);
                SkaConfigEntry entry = new SkaConfigEntry(cfg, f);
                workspace.addEntry(entry);
            } catch (Exception ex) {
                errorCount++;
                errors.append("  \u2022 ").append(f.getName()).append(": ").append(ex.getMessage()).append("\n");
            }
        }

        if (workspace.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Could not parse any XML files in:\n" + folder.getAbsolutePath()
                            + "\n\n" + errors,
                    "Open Folder", JOptionPane.ERROR_MESSAGE);
            // Fall back to empty state
            doNew();
            return;
        }

        workspace.rebuildMasterUserPool();

        // Activate the first entry
        SkaConfigEntry active = workspace.getActiveEntry();
        this.config = active.getConfig();
        this.currentFile = active.getSourceFile();
        this.dirty = false;
        this.loadedVersion = active.getLoadedVersion();

        loadModelIntoUI();
        refreshSkaSelector();
        int total = workspace.getEntries().size();
        String msg = "Opened folder: " + folder.getName() + " — " + total + " SKA file(s)"
                + ", " + workspace.getMasterUserPool().size() + " unique user(s)";
        if (errorCount > 0) {
            msg += " (" + errorCount + " file(s) failed to parse)";
            JOptionPane.showMessageDialog(this,
                    errorCount + " file(s) could not be parsed:\n\n" + errors,
                    "Parse Warnings", JOptionPane.WARNING_MESSAGE);
        }
        setStatus(msg);

        promptCsvVerification();
    }

    /**
     * Prompt for the load environment (Prod or Integration).
     * Remembered for the session — only asks once.
     */
    private boolean promptLoadEnvironment() {
        if (loadEnvironmentIntegration != null) {
            return loadEnvironmentIntegration;
        }
        int ans = JOptionPane.showOptionDialog(this,
                "Which environment were the UserIDs in these SKA files written for?\n\n"
                        + "This determines which UserID field the XML value is loaded into.",
                "Load Environment",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new String[]{"Production", "Integration"}, "Production");
        loadEnvironmentIntegration = (ans == 1);
        return loadEnvironmentIntegration;
    }

    /**
     * After reading XML, move the userId value into the correct field
     * based on the chosen environment. The XML only has one userId attribute;
     * we need to assign it to Prod or Integration accordingly.
     */
    private void applyLoadEnvironmentToUsers(List<User> users, boolean isIntegration) {
        if (!isIntegration) return; // Prod is the default — userId already in the right field
        for (User u : users) {
            String xmlUserId = u.getUserId();
            if (xmlUserId != null && !xmlUserId.isEmpty()) {
                u.setUserIdIntegration(xmlUserId);
                u.setUserId(""); // clear Prod field — XML value was Integration
            }
        }
    }

    /**
     * Offer to verify loaded user IDs against a CSV export.
     * Shows a file chooser; user can cancel to skip.
     */
    private void promptCsvVerification() {
        int ans = JOptionPane.showConfirmDialog(this,
                "Verify user IDs against a CSV asset export?\n\n"
                        + "This will compare loaded UserIDs with the CSV source of truth\n"
                        + "and report any inconsistencies.",
                "CSV Verification", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) return;

        JFileChooser chooser = fastFileChooser(workspaceFolder != null ? workspaceFolder : new File("."));
        chooser.setDialogTitle("Select CSV Asset Export for Verification");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            CsvImporter importer = new CsvImporter();
            List<User> csvUsers = importer.importUsers(chooser.getSelectedFile());

            // Build CSV lookup by CN
            var csvByCn = new java.util.LinkedHashMap<String, User>();
            for (User u : csvUsers) {
                if (!u.getCn().isEmpty()) csvByCn.put(u.getCn(), u);
            }

            // Collect all loaded users (master pool or single config)
            List<User> loadedUsers = workspace.getEntries().size() > 1
                    ? workspace.getMasterUserPool()
                    : config.getUsers();

            StringBuilder report = new StringBuilder();
            int mismatches = 0;
            List<String> missingUsers = new java.util.ArrayList<>();

            for (User loaded : loadedUsers) {
                User csv = csvByCn.get(loaded.getCn());
                if (csv == null) {
                    String label = loaded.getCn();
                    if (loaded.getName() != null && !loaded.getName().isEmpty()) {
                        label += " (" + loaded.getName() + ")";
                    }
                    missingUsers.add(label);
                    continue;
                }

                // Check Prod userId
                String loadedProd = loaded.getUserId();
                String csvProd = csv.getUserId();
                if (!csvProd.isEmpty() && !loadedProd.isEmpty()
                        && !csvProd.equals(loadedProd)) {
                    mismatches++;
                    report.append("  \u2022 ").append(loaded.getCn())
                            .append("\n    Prod UserID: SKA=\"").append(loadedProd)
                            .append("\" CSV=\"").append(csvProd).append("\"\n");
                }

                // Check Integration userId
                String loadedInt = loaded.getUserIdIntegration();
                String csvInt = csv.getUserIdIntegration();
                if (!csvInt.isEmpty() && !loadedInt.isEmpty()
                        && !csvInt.equals(loadedInt)) {
                    mismatches++;
                    report.append("  \u2022 ").append(loaded.getCn())
                            .append("\n    Int UserID:  SKA=\"").append(loadedInt)
                            .append("\" CSV=\"").append(csvInt).append("\"\n");
                }
            }

            // Build missing-users section
            StringBuilder missingSection = new StringBuilder();
            if (!missingUsers.isEmpty()) {
                missingSection.append(missingUsers.size())
                        .append(" SKA user(s) not found in CSV:\n");
                for (String label : missingUsers) {
                    missingSection.append("  \u2022 ").append(label).append("\n");
                }
            }

            // Show result
            if (mismatches == 0 && missingUsers.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "All user IDs match the CSV. No inconsistencies found.",
                        "CSV Verification", JOptionPane.INFORMATION_MESSAGE);
            } else {
                StringBuilder full = new StringBuilder();
                if (mismatches > 0) {
                    full.append(mismatches).append(" UserID mismatch(es) found:\n\n")
                            .append(report).append("\n");
                }
                if (!missingUsers.isEmpty()) {
                    full.append(missingSection);
                }
                JTextArea textArea = new JTextArea(full.toString());
                textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                textArea.setEditable(false);
                int rows = Math.min(mismatches * 3 + missingUsers.size() + 5, 30);
                textArea.setRows(rows);
                textArea.setColumns(70);
                textArea.setCaretPosition(0);
                JScrollPane scroll = new JScrollPane(textArea);
                String title = mismatches > 0
                        ? "CSV Verification \u2014 Mismatches" : "CSV Verification \u2014 Missing Users";
                JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.WARNING_MESSAGE);
            }

            setStatus("CSV verification: " + mismatches + " mismatch(es)"
                    + (!missingUsers.isEmpty() ? ", " + missingUsers.size() + " not in CSV" : ""));

            // Also merge ALL CSV users into the master pool so they are
            // available for assignment to any SKA
            if (usersPanel.isWorkspaceMode()) {
                doImportCsvIntoPool(csvUsers);
                loadModelIntoUI();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to read CSV:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doSave() {
        if (currentFile == null) {
            doSaveAs();
            return;
        }
        saveToFile(currentFile);
    }

    private void doSaveAs() {
        JFileChooser chooser = fastFileChooser(".");
        chooser.setDialogTitle("Save SKA XML Configuration");
        chooser.setFileFilter(new FileNameExtensionFilter("XML files (*.xml)", "xml"));
        if (currentFile != null) {
            chooser.setSelectedFile(currentFile);
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xml")) {
            file = new File(file.getAbsolutePath() + ".xml");
        }

        if (file.exists()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Overwrite existing file?\n" + file.getName(),
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        saveToFile(file);
    }

    private void saveToFile(File file) {
        try {
            collectUIIntoModel();

            // Prompt to increase version if unchanged since load
            if (loadedVersion >= 0 && config.getVersion() <= loadedVersion) {
                int ans = JOptionPane.showConfirmDialog(this,
                        "The version number (" + config.getVersion()
                                + ") has not been increased since loading the file.\n\n"
                                + "Increase version to " + (loadedVersion + 1) + "?",
                        "Version Number", JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (ans == JOptionPane.CANCEL_OPTION || ans == JOptionPane.CLOSED_OPTION) return;
                if (ans == JOptionPane.YES_OPTION) {
                    config.setVersion(loadedVersion + 1);
                    loadModelIntoUI(); // refresh spinner
                }
            }

            // Pre-save validation warnings
            String warnings = buildSaveWarnings();
            if (!warnings.isEmpty()) {
                int ans = JOptionPane.showConfirmDialog(this,
                        "The configuration has potential issues:\n\n" + warnings
                                + "\nSave anyway?",
                        "Validation Warnings", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (ans != JOptionPane.YES_OPTION) return;
            }

            String envName = promptEnvironmentName();
            if (envName == null) return; // user cancelled

            SkaXmlWriter writer = new SkaXmlWriter();
            String oldEnv = sessionEnvironmentName;
            sessionEnvironmentName = envName;
            file = applyVersionToFilename(file, config.getVersion(), envName, oldEnv);
            writer.write(config, file);
            this.currentFile = file;
            this.dirty = false;

            // Keep workspace entry in sync
            SkaConfigEntry active = workspace.getActiveEntry();
            if (active != null) {
                active.setSourceFile(file);
                active.setDirty(false);
                active.setLoadedVersion(config.getVersion());
            }
            this.loadedVersion = config.getVersion();

            refreshSkaSelector();
            setStatus("Saved: " + file.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doSaveAll() {
        if (workspace.isEmpty()) return;

        // Collect UI into the active model
        collectUIIntoModel();

        // In workspace mode, sync all entries' user lists from the master pool
        if (usersPanel.isWorkspaceMode()) {
            for (SkaConfigEntry entry : workspace.getEntries()) {
                java.util.Set<String> cns = workspace.getCnsForEntry(entry);
                workspace.syncEntryUsersFromPool(entry, cns);
            }
        }

        // Prompt for environment name once (applies to all files)
        String envName = promptEnvironmentName();
        if (envName == null) return; // user cancelled
        String oldEnv = sessionEnvironmentName;
        sessionEnvironmentName = envName;

        // Prompt to bump version numbers for all dirty entries
        List<SkaConfigEntry> dirtyEntries = new java.util.ArrayList<>();
        for (SkaConfigEntry entry : workspace.getEntries()) {
            if (entry.isDirty()) dirtyEntries.add(entry);
        }

        if (!dirtyEntries.isEmpty()) {
            // Build a table-like overview of versions
            StringBuilder versionInfo = new StringBuilder();
            versionInfo.append("The following SKAs have been modified:\n\n");
            boolean anyNeedsBump = false;
            for (SkaConfigEntry entry : dirtyEntries) {
                int current = entry.getConfig().getVersion();
                int loaded = entry.getLoadedVersion();
                boolean needsBump = loaded >= 0 && current <= loaded;
                String status = needsBump
                        ? "  v" + current + " \u2192 v" + (loaded + 1) + "  (bump)"
                        : "  v" + current + "  (already incremented)";
                versionInfo.append("  \u2022 ").append(entry.getDisplayLabel())
                        .append(status).append("\n");
                if (needsBump) anyNeedsBump = true;
            }

            if (anyNeedsBump) {
                versionInfo.append("\nIncrease version numbers as shown above?");
                int ans = JOptionPane.showConfirmDialog(this,
                        versionInfo.toString(),
                        "Version Numbers — Save All",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (ans == JOptionPane.CANCEL_OPTION || ans == JOptionPane.CLOSED_OPTION) return;
                if (ans == JOptionPane.YES_OPTION) {
                    for (SkaConfigEntry entry : dirtyEntries) {
                        int current = entry.getConfig().getVersion();
                        int loaded = entry.getLoadedVersion();
                        if (loaded >= 0 && current <= loaded) {
                            entry.getConfig().setVersion(loaded + 1);
                        }
                    }
                }
            }
        }

        SkaXmlWriter writer = new SkaXmlWriter();
        int saved = 0;
        int errors = 0;
        StringBuilder errorDetails = new StringBuilder();

        for (SkaConfigEntry entry : workspace.getEntries()) {
            if (!entry.isDirty()) continue;
            File file = entry.getSourceFile();
            if (file == null) {
                // Entry without file — skip (needs Save As)
                errorDetails.append("  \u2022 ").append(entry.getDisplayLabel())
                        .append(": no file path (use Save As)\n");
                errors++;
                continue;
            }
            try {
                File versionedFile = applyVersionToFilename(file, entry.getConfig().getVersion(),
                        envName, oldEnv);
                writer.write(entry.getConfig(), versionedFile);
                if (!versionedFile.equals(file)) {
                    entry.setSourceFile(versionedFile);
                }
                entry.setDirty(false);
                entry.setLoadedVersion(entry.getConfig().getVersion());
                saved++;
            } catch (Exception ex) {
                errors++;
                errorDetails.append("  \u2022 ").append(file.getName())
                        .append(": ").append(ex.getMessage()).append("\n");
            }
        }

        // Sync local dirty flag with active entry
        SkaConfigEntry active = workspace.getActiveEntry();
        if (active != null) {
            this.dirty = active.isDirty();
            this.loadedVersion = active.getLoadedVersion();
        }

        updateTitle();
        refreshSkaSelector();

        if (errors > 0) {
            JOptionPane.showMessageDialog(this,
                    "Saved " + saved + " file(s), " + errors + " error(s):\n\n" + errorDetails,
                    "Save All", JOptionPane.WARNING_MESSAGE);
        }
        setStatus("Save All: " + saved + " file(s) saved" + (errors > 0 ? ", " + errors + " failed" : ""));
    }

    /**
     * Prompt for an environment name to embed in the filename.
     * Pre-filled with the previous value. Returns null if user cancels.
     */
    private String promptEnvironmentName() {
        String input = (String) JOptionPane.showInputDialog(this,
                "Enter an environment name for the filename (optional):\n\n"
                        + "Leave blank to omit.  Examples: Prod, Integration, UAT",
                "Environment Name", JOptionPane.PLAIN_MESSAGE,
                null, null, sessionEnvironmentName);
        if (input == null) return null; // cancelled
        return input.trim();
    }

    /**
     * Apply environment and version to a filename.
     * <ul>
     *   <li>{@code name.xml} + env="Prod" + v3 → {@code name_Prod_v3.xml}</li>
     *   <li>{@code name_Prod_v2.xml} + env="Int" + v3 → {@code name_Int_v3.xml}</li>
     *   <li>{@code name.xml} + env="" + v3 → {@code name_v3.xml}</li>
     * </ul>
     *
     * @param previousEnvName the environment name used last time (used to strip
     *                        the old suffix); may be null/empty
     */
    static File applyVersionToFilename(File file, int version,
                                       String envName, String previousEnvName) {
        String name = file.getName();
        String dir = file.getParent();

        // Strip .xml extension (case-insensitive)
        String base;
        String ext;
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            base = name.substring(0, dotIdx);
            ext = name.substring(dotIdx); // e.g. ".xml"
        } else {
            base = name;
            ext = ".xml";
        }

        // Remove existing _v<digits> suffix
        base = base.replaceAll("_v\\d+$", "");

        // Remove previous environment name suffix
        if (previousEnvName != null && !previousEnvName.isEmpty()) {
            String suffix = "_" + previousEnvName;
            if (base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
            }
        }

        // Build new name
        StringBuilder newName = new StringBuilder(base);
        if (envName != null && !envName.isEmpty()) {
            newName.append('_').append(envName);
        }
        newName.append("_v").append(version).append(ext);
        return dir != null ? new File(dir, newName.toString()) : new File(newName.toString());
    }

    private void doImportCsv() {
        JFileChooser chooser = fastFileChooser(".");
        chooser.setDialogTitle("Import Users from Jira CSV Export");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            CsvImporter importer = new CsvImporter();
            List<User> imported = importer.importUsers(file);

            if (imported.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No users found in CSV file.",
                        "Import Result", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            if (usersPanel.isWorkspaceMode()) {
                // Workspace mode: update master pool
                doImportCsvIntoPool(imported);
            } else {
                // Single-file mode: update per-SKA user list
                if (!config.getUsers().isEmpty()) {
                    detectAndPromptCertChanges(imported);
                } else {
                    config.setUsers(imported);
                }
            }

            loadModelIntoUI();
            setStatus("Imported " + imported.size() + " users from " + file.getName());
            markDirty();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to import CSV:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Compare imported users against current users by CN.
     * Prompt user to update certificates that have changed.
     */
    private void detectAndPromptCertChanges(List<User> imported) {
        // Build lookup of current users by CN
        var currentByCn = new java.util.LinkedHashMap<String, User>();
        for (User u : config.getUsers()) {
            if (!u.getCn().isEmpty()) currentByCn.put(u.getCn(), u);
        }

        StringBuilder changed = new StringBuilder();
        int changedCount = 0;
        List<User> newUsers = new java.util.ArrayList<>();

        for (User imp : imported) {
            User existing = currentByCn.get(imp.getCn());
            if (existing == null) {
                newUsers.add(imp);
            } else if (!imp.getCertificate().isEmpty()
                    && !imp.getCertificate().equals(existing.getCertificate())) {
                changedCount++;
                changed.append("  • ").append(imp.getCn()).append("\n");
            }
        }

        // Prompt for cert updates
        if (changedCount > 0) {
            int answer = JOptionPane.showConfirmDialog(this,
                    changedCount + " certificate(s) have changed in the CSV:\n\n"
                            + changed
                            + "\nUpdate these certificates now?",
                    "Certificate Changes Detected",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (answer == JOptionPane.YES_OPTION) {
                for (User imp : imported) {
                    User existing = currentByCn.get(imp.getCn());
                    if (existing != null && !imp.getCertificate().isEmpty()) {
                        existing.setCertificate(imp.getCertificate());
                    }
                    // Also update other fields from CSV (email, org, etc.)
                    if (existing != null) {
                        if (!imp.getEmail().isEmpty()) existing.setEmail(imp.getEmail());
                        if (!imp.getOrganisation().isEmpty()) existing.setOrganisation(imp.getOrganisation());
                        if (!imp.getUserId().isEmpty()) existing.setUserId(imp.getUserId());
                        if (!imp.getUserIdIntegration().isEmpty()) existing.setUserIdIntegration(imp.getUserIdIntegration());
                        existing.getOrgOwnerOf().addAll(imp.getOrgOwnerOf());
                        existing.getOrgSecOffOf().addAll(imp.getOrgSecOffOf());
                        existing.getOrgOpOf().addAll(imp.getOrgOpOf());
                    }
                }
            }
        }

        // Prompt for new users
        if (!newUsers.isEmpty()) {
            StringBuilder newList = new StringBuilder();
            for (User u : newUsers) {
                newList.append("  • ").append(u.getCn()).append("\n");
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    newUsers.size() + " new user(s) found in CSV:\n\n"
                            + newList
                            + "\nAdd them to the configuration?",
                    "New Users Found",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                config.getUsers().addAll(newUsers);
            }
        }

        if (changedCount == 0 && newUsers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "All users are up to date. No changes needed.",
                    "Import Result", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Import CSV users into the master user pool (workspace/folder mode).
     * Updates existing users by CN, adds new ones, and includes new users
     * in the current SKA by default.
     */
    private void doImportCsvIntoPool(List<User> imported) {
        List<User> pool = workspace.getMasterUserPool();
        var poolByCn = new java.util.LinkedHashMap<String, User>();
        for (User u : pool) {
            if (!u.getCn().isEmpty()) poolByCn.put(u.getCn(), u);
        }

        int updated = 0;
        List<User> newUsers = new java.util.ArrayList<>();
        List<String> certChanges = new java.util.ArrayList<>();

        for (User imp : imported) {
            User existing = poolByCn.get(imp.getCn());
            if (existing == null) {
                newUsers.add(imp);
            } else {
                // Track certificate changes before overwriting
                if (!imp.getCertificate().isEmpty()
                        && !imp.getCertificate().equals(existing.getCertificate())
                        && !existing.getCertificate().isEmpty()) {
                    certChanges.add(existing.getCn());
                }
                // Update fields from CSV
                if (!imp.getEmail().isEmpty()) existing.setEmail(imp.getEmail());
                if (!imp.getOrganisation().isEmpty()) existing.setOrganisation(imp.getOrganisation());
                if (!imp.getUserId().isEmpty()) existing.setUserId(imp.getUserId());
                if (!imp.getUserIdIntegration().isEmpty()) existing.setUserIdIntegration(imp.getUserIdIntegration());
                if (!imp.getCertificate().isEmpty()) existing.setCertificate(imp.getCertificate());
                existing.getOrgOwnerOf().addAll(imp.getOrgOwnerOf());
                existing.getOrgSecOffOf().addAll(imp.getOrgSecOffOf());
                existing.getOrgOpOf().addAll(imp.getOrgOpOf());
                updated++;
            }
        }

        // Report certificate changes
        if (!certChanges.isEmpty()) {
            StringBuilder certList = new StringBuilder();
            for (String cn : certChanges) {
                certList.append("  \u2022 ").append(cn).append("\n");
            }
            JOptionPane.showMessageDialog(this,
                    certChanges.size() + " certificate(s) have been updated from CSV:\n\n"
                            + certList,
                    "Certificate Changes", JOptionPane.INFORMATION_MESSAGE);
        }

        // Add new users to pool (always — makes them available for assignment to any SKA)
        if (!newUsers.isEmpty()) {
            StringBuilder newList = new StringBuilder();
            for (User u : newUsers) {
                newList.append("  \u2022 ").append(u.getCn()).append("\n");
                pool.add(u);
            }
            JOptionPane.showMessageDialog(this,
                    newUsers.size() + " new user(s) added to the master pool from CSV:\n\n"
                            + newList
                            + "\nThey are now available to be assigned to any SKA\n"
                            + "via the \"In SKA\" checkbox.",
                    "New Users Added",
                    JOptionPane.INFORMATION_MESSAGE);
        }

        if (updated == 0 && newUsers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "All users are up to date. No changes needed.",
                    "Import Result", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Mark all entries dirty since pool data may have changed
            for (SkaConfigEntry entry : workspace.getEntries()) {
                entry.setDirty(true);
            }
        }
    }

    // --- Model ↔ UI synchronization ---

    /**
     * Push model data into all UI panels.
     */
    private void loadModelIntoUI() {
        globalConfigPanel.loadFrom(config);
        organizationPanel.loadFrom(config.getOrganization());
        skaPlusPanel.loadFrom(config.getSkaPlus());
        skaModifyPanel.loadFrom(config.getSkaModify());
        keysProtoPanel.loadFrom(config.getKeysProto());

        // Users: workspace mode shows master pool + per-SKA checkboxes
        if (workspaceFolder != null && workspace.getEntries().size() > 1) {
            SkaConfigEntry active = workspace.getActiveEntry();
            usersPanel.loadFromWorkspace(workspace, active);
        } else {
            usersPanel.clearWorkspaceMode();
            usersPanel.loadFrom(config.getUsers());
        }

        // Sync environment toggle
        boolean isIntegration = config.isIntegrationEnvironment();
        prodRadio.setSelected(!isIntegration);
        integrationRadio.setSelected(isIntegration);
        usersPanel.setIntegrationEnvironment(isIntegration);
        // Update the Keys tab label to reflect the actual child name
        String childName = config.getKeysProto().getChildName();
        int keysTabIndex = tabbedPane.indexOfComponent(keysProtoPanel);
        if (keysTabIndex >= 0) {
            String label = childName.isEmpty() ? "Keys" : "Keys (" + childName + ")";
            tabbedPane.setTitleAt(keysTabIndex, label);
        }
        updateTitle();
    }

    /**
     * Mark the configuration as having unsaved changes.
     */
    public void markDirty() {
        if (!dirty) {
            dirty = true;
            SkaConfigEntry active = workspace.getActiveEntry();
            if (active != null) active.setDirty(true);
            updateTitle();
            refreshSkaSelector();
        }
    }

    /**
     * Ask the user to confirm discarding unsaved changes.
     * Returns true if we should proceed, false to cancel.
     */
    private boolean confirmDiscardChanges(String action) {
        boolean anyDirty = dirty || workspace.hasAnyDirty();
        if (!anyDirty) return true;

        String message = "You have unsaved changes.";
        if (workspace.getEntries().size() > 1) {
            long dirtyCount = workspace.getEntries().stream()
                    .filter(SkaConfigEntry::isDirty).count();
            StringBuilder detail = new StringBuilder();
            for (SkaConfigEntry e : workspace.getEntries()) {
                if (e.isDirty()) detail.append("  \u2022 ").append(e.getDisplayLabel()).append("\n");
            }
            message = dirtyCount + " file(s) have unsaved changes:\n\n" + detail;
        }
        message += "\nDiscard changes and " + action.toLowerCase() + "?";

        int ans = JOptionPane.showConfirmDialog(this, message,
                action, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return ans == JOptionPane.YES_OPTION;
    }

    /**
     * Exit the application, prompting to save if dirty.
     */
    private void doExit() {
        boolean anyDirty = dirty || workspace.hasAnyDirty();
        if (anyDirty) {
            String message = "You have unsaved changes.";
            if (workspace.getEntries().size() > 1) {
                long dirtyCount = workspace.getEntries().stream()
                        .filter(SkaConfigEntry::isDirty).count();
                StringBuilder detail = new StringBuilder();
                for (SkaConfigEntry e : workspace.getEntries()) {
                    if (e.isDirty()) detail.append("  \u2022 ").append(e.getDisplayLabel()).append("\n");
                }
                message = dirtyCount + " file(s) have unsaved changes:\n\n" + detail;
            }
            message += "\nSave before exiting?";

            int ans = JOptionPane.showConfirmDialog(this, message,
                    "Exit", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans == JOptionPane.CANCEL_OPTION || ans == JOptionPane.CLOSED_OPTION) return;
            if (ans == JOptionPane.YES_OPTION) {
                if (workspace.getEntries().size() > 1) {
                    doSaveAll();
                } else {
                    doSave();
                }
                // If save was cancelled (still dirty), don't exit
                if (dirty || workspace.hasAnyDirty()) return;
            }
        }
        dispose();
        System.exit(0);
    }

    /**
     * Build a string of validation warnings before saving.
     */
    private String buildSaveWarnings() {
        StringBuilder sb = new StringBuilder();
        if (config.getModuleName().isEmpty()) {
            sb.append("  \u2022 Module name is empty\n");
        }
        long missingCerts = config.getUsers().stream()
                .filter(u -> u.getCertificate().isEmpty()).count();
        if (missingCerts > 0) {
            sb.append("  \u2022 ").append(missingCerts).append(" user(s) have no certificate\n");
        }
        // Check for empty groups in all sections
        int emptyGroups = countEmptyGroups(config.getOrganization().getOperations())
                + countEmptyGroups(config.getSkaPlus().getOperations())
                + countEmptyGroups(config.getSkaModify().getOperations())
                + countEmptyGroups(config.getKeysProto().getOperations());
        if (emptyGroups > 0) {
            sb.append("  \u2022 ").append(emptyGroups).append(" group(s) have no members/keys\n");
        }
        // Date format warnings
        for (String w : globalConfigPanel.validateFields()) {
            sb.append("  \u2022 ").append(w).append("\n");
        }
        return sb.toString();
    }

    private int countEmptyGroups(com.pki.model.Operations ops) {
        int count = 0;
        for (var op : List.of(ops.getUse(), ops.getModify(), ops.getBlock(), ops.getUnblock())) {
            for (var b : op.getBoundaries()) {
                for (var g : b.getGroups()) {
                    if (g.getMemberCns().isEmpty() && g.getKeyLabels().isEmpty()
                            && g.getQuorum() > 0) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Pull UI panel data back into the model.
     */
    private void collectUIIntoModel() {
        globalConfigPanel.saveTo(config);
        organizationPanel.saveTo(config.getOrganization());
        skaPlusPanel.saveTo(config.getSkaPlus());
        skaModifyPanel.saveTo(config.getSkaModify());
        keysProtoPanel.saveTo(config.getKeysProto());
        config.setIntegrationEnvironment(integrationRadio.isSelected());

        // In workspace mode, sync per-SKA user list from pool checkboxes
        if (usersPanel.isWorkspaceMode()) {
            SkaConfigEntry active = workspace.getActiveEntry();
            if (active != null) {
                workspace.syncEntryUsersFromPool(active, usersPanel.getSkaCns());
            }
        }
        // In single-file mode UsersPanel edits the list in-place
    }

    private void updateTitle() {
        String title = "SKA Configurator";
        if (workspaceFolder != null) {
            title += " \u2014 " + workspaceFolder.getName();
        }
        if (currentFile != null) {
            title += " \u2014 " + currentFile.getName();
        }
        if (!config.getModuleName().isEmpty()) {
            title += " [" + config.getModuleName() + "]";
        }
        if (dirty) {
            title += " *";
        }
        setTitle(title);
    }

    /**
     * Refresh the SKA selector combo box items and visibility.
     * Shows only when there are 2+ entries (folder mode).
     */
    private void refreshSkaSelector() {
        boolean showSelector = workspace.getEntries().size() > 1;
        skaSelectorLabel.setVisible(showSelector);
        skaSelector.setVisible(showSelector);

        if (!showSelector) return;

        skaSelectorUpdating = true;
        try {
            skaSelector.removeAllItems();
            for (SkaConfigEntry entry : workspace.getEntries()) {
                skaSelector.addItem(entry.getDisplayLabelWithDirty());
            }
            skaSelector.setSelectedIndex(workspace.getActiveIndex());
        } finally {
            skaSelectorUpdating = false;
        }
    }
}
