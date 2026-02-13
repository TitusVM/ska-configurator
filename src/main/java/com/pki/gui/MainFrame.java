package com.pki.gui;

import com.pki.io.CsvImporter;
import com.pki.io.SkaXmlReader;
import com.pki.io.SkaXmlWriter;
import com.pki.model.SkaConfig;
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

    private SkaConfig config;
    private File currentFile;
    private boolean dirty = false;

    private final GlobalConfigPanel globalConfigPanel;
    private final SectionPanel organizationPanel;
    private final SectionPanel skaPlusPanel;
    private final SectionPanel skaModifyPanel;
    private final KeysProtoPanel keysProtoPanel;
    private final UsersPanel usersPanel;
    private final JTabbedPane tabbedPane;
    private final JLabel statusBar;

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

    public void setStatus(String message) {
        statusBar.setText("  " + message);
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

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
        saveItem.addActionListener(e -> doSave());

        JMenuItem saveAsItem = new JMenuItem("Save As…");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke("control shift S"));
        saveAsItem.addActionListener(e -> doSaveAs());

        JMenuItem importCsvItem = new JMenuItem("Import Users from CSV…");
        importCsvItem.setAccelerator(KeyStroke.getKeyStroke("control I"));
        importCsvItem.addActionListener(e -> doImportCsv());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> doExit());

        fileMenu.add(newItem);
        fileMenu.addSeparator();
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
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
        loadModelIntoUI();
        setStatus("New configuration created");
    }

    private void doOpen() {
        if (!confirmDiscardChanges("Open File")) return;

        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("Open SKA XML Configuration");
        chooser.setFileFilter(new FileNameExtensionFilter("XML files (*.xml)", "xml"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            SkaXmlReader reader = new SkaXmlReader();
            this.config = reader.read(file);
            this.currentFile = file;
            this.dirty = false;
            loadModelIntoUI();
            setStatus("Loaded: " + file.getName() + "  (" + config.getUsers().size() + " users)");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open file:\n" + ex.getMessage(),
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
        JFileChooser chooser = new JFileChooser(".");
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

            SkaXmlWriter writer = new SkaXmlWriter();
            writer.write(config, file);
            this.currentFile = file;
            this.dirty = false;
            setStatus("Saved: " + file.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doImportCsv() {
        JFileChooser chooser = new JFileChooser(".");
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

            // Detect certificate changes if we already have users
            if (!config.getUsers().isEmpty()) {
                detectAndPromptCertChanges(imported);
            } else {
                config.setUsers(imported);
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
        usersPanel.loadFrom(config.getUsers());
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
            updateTitle();
        }
    }

    /**
     * Ask the user to confirm discarding unsaved changes.
     * Returns true if we should proceed, false to cancel.
     */
    private boolean confirmDiscardChanges(String action) {
        if (!dirty) return true;
        int ans = JOptionPane.showConfirmDialog(this,
                "You have unsaved changes.\n\nDiscard changes and " + action.toLowerCase() + "?",
                action, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return ans == JOptionPane.YES_OPTION;
    }

    /**
     * Exit the application, prompting to save if dirty.
     */
    private void doExit() {
        if (dirty) {
            int ans = JOptionPane.showConfirmDialog(this,
                    "You have unsaved changes.\n\nSave before exiting?",
                    "Exit", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans == JOptionPane.CANCEL_OPTION || ans == JOptionPane.CLOSED_OPTION) return;
            if (ans == JOptionPane.YES_OPTION) {
                doSave();
                // If save was cancelled (no file chosen), don't exit
                if (dirty) return;
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
        // UsersPanel edits the list in-place, no explicit saveTo needed
    }

    private void updateTitle() {
        String title = "SKA Configurator";
        if (currentFile != null) {
            title += " — " + currentFile.getName();
        }
        if (!config.getModuleName().isEmpty()) {
            title += " [" + config.getModuleName() + "]";
        }        if (dirty) {
            title += " *";
        }        setTitle(title);
    }
}
