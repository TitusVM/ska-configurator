package com.pki.gui;

import com.pki.model.SkaConfigEntry;
import com.pki.model.SkaWorkspace;
import com.pki.model.User;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel for viewing and managing users in the configuration.
 * <p>
 * In <b>single-file mode</b> the table shows the per-SKA user list (6 columns).
 * In <b>workspace/folder mode</b> the table shows the <em>master user pool</em>
 * with an extra leading "In SKA" checkbox column to toggle per-SKA membership.
 */
public class UsersPanel extends JPanel {

    private final UserTableModel tableModel;
    private final JTable table;

    /** Users currently displayed in the table. */
    private List<User> users = new ArrayList<>();
    /** CNs that belong to the current SKA entry (only used in workspace mode). */
    private Set<String> skaCns = new LinkedHashSet<>();

    private boolean integrationEnvironment = false;
    private boolean workspaceMode = false;
    private SkaWorkspace workspace;

    private Consumer<String> statusCallback;
    private Runnable dirtyCallback;
    private Runnable refreshCallback;

    /**
     * Set a callback for status bar messages.
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    /**
     * Set a callback to mark the configuration as dirty.
     */
    public void setDirtyCallback(Runnable callback) {
        this.dirtyCallback = callback;
    }

    /**
     * Set a callback to refresh all UI panels from the model.
     * Called after bulk model changes (e.g. Replace User).
     */
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    private void postStatus(String msg) {
        if (statusCallback != null) statusCallback.accept(msg);
    }

    private void markDirty() {
        if (dirtyCallback != null) dirtyCallback.run();
    }

    /**
     * Switch the environment toggle. Updates the UserID column header and data.
     */
    public void setIntegrationEnvironment(boolean integration) {
        this.integrationEnvironment = integration;
        int col = workspaceMode ? 5 : 4;
        table.getColumnModel().getColumn(col).setHeaderValue(
                integration ? "UserID (Int)" : "UserID (Prod)");
        table.getTableHeader().repaint();
        tableModel.fireTableDataChanged();
    }

    /**
     * Enter workspace (folder) mode — the table will show the master pool
     * with an "In SKA" checkbox column.
     */
    public void setWorkspaceMode(SkaWorkspace workspace) {
        this.workspace = workspace;
        this.workspaceMode = true;
        replaceBtn.setVisible(true);
        header.setText("User master pool:");
    }

    /**
     * Leave workspace mode (single-file).
     */
    public void clearWorkspaceMode() {
        this.workspace = null;
        this.workspaceMode = false;
        this.skaCns.clear();
        replaceBtn.setVisible(false);
        header.setText("Users in this SKA configuration:");
    }

    private final JLabel header;
    private final JButton replaceBtn;

    public UsersPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header
        header = new JLabel("Users in this SKA configuration:");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        add(header, BorderLayout.NORTH);

        // Table
        tableModel = new UserTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        setupColumnWidths();
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton addBtn = new JButton("Add User");
        addBtn.addActionListener(e -> doAdd());
        JButton editBtn = new JButton("Edit User");
        editBtn.addActionListener(e -> doEdit());
        JButton removeBtn = new JButton("Remove User");
        removeBtn.addActionListener(e -> doRemove());
        JButton viewCertBtn = new JButton("View Certificate");
        viewCertBtn.addActionListener(e -> doViewCert());

        replaceBtn = new JButton("Replace User\u2026");
        replaceBtn.setToolTipText("Replace a user across all open SKA configurations");
        replaceBtn.addActionListener(e -> doReplaceUser());
        replaceBtn.setVisible(false); // only shown in workspace mode

        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(removeBtn);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(viewCertBtn);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(replaceBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private void setupColumnWidths() {
        var cm = table.getColumnModel();
        if (workspaceMode) {
            // 7 columns: InSKA, CN, Name, Email, Org, UserID, Cert
            if (cm.getColumnCount() < 7) return;
            cm.getColumn(0).setPreferredWidth(55);
            cm.getColumn(0).setMaxWidth(60);
            cm.getColumn(1).setPreferredWidth(200);
            cm.getColumn(2).setPreferredWidth(140);
            cm.getColumn(3).setPreferredWidth(200);
            cm.getColumn(4).setPreferredWidth(100);
            cm.getColumn(5).setPreferredWidth(80);
            cm.getColumn(6).setPreferredWidth(60);
        } else {
            // 6 columns: CN, Name, Email, Org, UserID, Cert
            if (cm.getColumnCount() < 6) return;
            cm.getColumn(0).setPreferredWidth(200);
            cm.getColumn(1).setPreferredWidth(140);
            cm.getColumn(2).setPreferredWidth(200);
            cm.getColumn(3).setPreferredWidth(100);
            cm.getColumn(4).setPreferredWidth(80);
            cm.getColumn(5).setPreferredWidth(60);
        }
    }

    // --- Actions ---

    private void doAdd() {
        UserEditDialog dlg = new UserEditDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), null);
        dlg.setExistingCns(users.stream().map(User::getCn).toList());
        dlg.setVisible(true);
        User newUser = dlg.getResult();
        if (newUser != null) {
            users.add(newUser);
            if (workspaceMode) {
                // Also include in the current SKA by default
                skaCns.add(newUser.getCn());
            }
            tableModel.fireTableDataChanged();
            table.setRowSelectionInterval(users.size() - 1, users.size() - 1);
            markDirty();
            postStatus("Added user: " + newUser.getCn());
        }
    }

    private void doEdit() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.");
            return;
        }
        User user = users.get(row);
        UserEditDialog dlg = new UserEditDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), user);
        dlg.setVisible(true);
        User result = dlg.getResult();
        if (result != null) {
            // result is the same object, edited in place
            tableModel.fireTableRowsUpdated(row, row);
            markDirty();
            postStatus("Updated user: " + result.getCn());
        }
    }

    private void doRemove() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.");
            return;
        }
        User user = users.get(row);

        if (workspaceMode) {
            // In folder mode, offer two choices: remove from pool or just un-link
            String[] options = {"Remove from Pool", "Exclude from SKA", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this,
                    "User: " + user.getCn() + "\n\n"
                            + "• \"Remove from Pool\" removes from ALL SKAs\n"
                            + "• \"Exclude from SKA\" un-checks this user from the current SKA only",
                    "Remove User", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (choice == 0) {
                // Remove from pool entirely
                users.remove(row);
                skaCns.remove(user.getCn());
                tableModel.fireTableDataChanged();
                markDirty();
                postStatus("Removed from pool: " + user.getCn());
            } else if (choice == 1) {
                // Just exclude from current SKA
                skaCns.remove(user.getCn());
                tableModel.fireTableRowsUpdated(row, row);
                markDirty();
                postStatus("Excluded from SKA: " + user.getCn());
            }
        } else {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove user \"" + user.getCn() + "\"?\n\n"
                            + "Note: This will NOT automatically remove them from groups.",
                    "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            users.remove(row);
            tableModel.fireTableDataChanged();
            markDirty();
            postStatus("Removed user: " + user.getCn());
        }
    }

    private void doViewCert() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.");
            return;
        }
        User user = users.get(row);
        if (user.getCertificate().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No certificate for " + user.getCn(),
                    "Certificate", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea textArea = new JTextArea(user.getCertificate());
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        textArea.setEditable(false);
        textArea.setRows(20);
        textArea.setColumns(70);
        JScrollPane scroll = new JScrollPane(textArea);
        JOptionPane.showMessageDialog(this, scroll,
                "Certificate — " + user.getCn(), JOptionPane.PLAIN_MESSAGE);
    }

    private void doReplaceUser() {
        if (!workspaceMode || workspace == null) {
            JOptionPane.showMessageDialog(this,
                    "Replace User is only available in workspace (folder) mode.",
                    "Not Available", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (users.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "At least two users are needed in the pool to perform a replacement.",
                    "Not Enough Users", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ReplaceUserDialog dlg = new ReplaceUserDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), workspace, users);
        dlg.setVisible(true);
        if (dlg.isCommitted()) {
            // Refresh all UI panels from the updated model so that
            // section/operation panels pick up the CN changes in group memberships
            if (refreshCallback != null) refreshCallback.run();
            // Reload the table and update SKA CN sets from the modified entry user lists
            SkaConfigEntry active = workspace.getActiveEntry();
            if (active != null) {
                this.skaCns = workspace.getCnsForEntry(active);
            }
            tableModel.fireTableDataChanged();
            markDirty();
            postStatus("User replacement committed across all SKAs.");
        }
    }

    // --- Model ↔ UI ---

    /**
     * Load users into the table. In single-file mode this is the per-SKA list.
     * In workspace mode, call {@link #loadFromWorkspace} instead.
     */
    public void loadFrom(List<User> users) {
        this.users = users;
        this.skaCns.clear();
        tableModel.fireTableStructureChanged();
        setupColumnWidths();
    }

    /**
     * Load the master pool and per-SKA membership for folder mode.
     */
    public void loadFromWorkspace(SkaWorkspace ws, SkaConfigEntry entry) {
        this.workspace = ws;
        this.workspaceMode = true;
        this.users = ws.getMasterUserPool();
        this.skaCns = ws.getCnsForEntry(entry);
        replaceBtn.setVisible(true);
        header.setText("Users — master pool  (check \"In SKA\" to include in current configuration):");
        tableModel.fireTableStructureChanged();
        setupColumnWidths();
    }

    /**
     * Build the per-SKA user list from the master pool based on checked CNs.
     * Called before saving the active entry.
     */
    public List<User> buildSkaUserList() {
        if (!workspaceMode) return users;
        List<User> result = new ArrayList<>();
        for (User u : users) {
            if (skaCns.contains(u.getCn())) {
                result.add(u);
            }
        }
        return result;
    }

    /**
     * Returns the current set of CNs checked as belonging to this SKA.
     */
    public Set<String> getSkaCns() {
        return skaCns;
    }

    /**
     * Returns the live user list (same reference as the model).
     * No need for saveTo — the list is edited in place.
     */
    public List<User> getUsers() {
        return users;
    }

    public boolean isWorkspaceMode() {
        return workspaceMode;
    }

    // --- Table model ---

    private class UserTableModel extends AbstractTableModel {

        private final String[] COLUMNS_SINGLE = {"CN", "Name", "Email", "Organisation", "UserID (Prod)", "Cert"};
        private final String[] COLUMNS_WORKSPACE = {"In SKA", "CN", "Name", "Email", "Organisation", "UserID (Prod)", "Cert"};

        @Override public int getRowCount() { return users.size(); }

        @Override public int getColumnCount() {
            return workspaceMode ? COLUMNS_WORKSPACE.length : COLUMNS_SINGLE.length;
        }

        @Override public String getColumnName(int col) {
            if (workspaceMode) {
                if (col == 5) return integrationEnvironment ? "UserID (Int)" : "UserID (Prod)";
                return COLUMNS_WORKSPACE[col];
            } else {
                if (col == 4) return integrationEnvironment ? "UserID (Int)" : "UserID (Prod)";
                return COLUMNS_SINGLE[col];
            }
        }

        @Override public Class<?> getColumnClass(int col) {
            if (workspaceMode && col == 0) return Boolean.class;
            return String.class;
        }

        @Override public boolean isCellEditable(int row, int col) {
            return workspaceMode && col == 0;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (workspaceMode && col == 0 && value instanceof Boolean checked) {
                User u = users.get(row);
                if (checked) {
                    skaCns.add(u.getCn());
                } else {
                    skaCns.remove(u.getCn());
                }
                markDirty();
            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            User u = users.get(row);
            if (workspaceMode) {
                return switch (col) {
                    case 0 -> skaCns.contains(u.getCn());
                    case 1 -> u.getCn();
                    case 2 -> u.getName();
                    case 3 -> u.getEmail();
                    case 4 -> u.getOrganisation();
                    case 5 -> integrationEnvironment ? u.getUserIdIntegration() : u.getUserId();
                    case 6 -> u.getCertificate().isEmpty() ? "—" : "\u2713";
                    default -> "";
                };
            } else {
                return switch (col) {
                    case 0 -> u.getCn();
                    case 1 -> u.getName();
                    case 2 -> u.getEmail();
                    case 3 -> u.getOrganisation();
                    case 4 -> integrationEnvironment ? u.getUserIdIntegration() : u.getUserId();
                    case 5 -> u.getCertificate().isEmpty() ? "—" : "\u2713";
                    default -> "";
                };
            }
        }
    }
}
