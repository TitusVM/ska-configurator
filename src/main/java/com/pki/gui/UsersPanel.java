package com.pki.gui;

import com.pki.model.User;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel for viewing and managing users in the configuration.
 * Shows a table with cn, name, email, organisation, userId, and certificate status.
 * Provides add, edit, remove buttons.
 */
public class UsersPanel extends JPanel {

    private final UserTableModel tableModel;
    private final JTable table;
    private List<User> users = new ArrayList<>();
    private boolean integrationEnvironment = false;
    private Consumer<String> statusCallback;
    private Runnable dirtyCallback;

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
        // Update column header
        table.getColumnModel().getColumn(4).setHeaderValue(
                integration ? "UserID (Int)" : "UserID (Prod)");
        table.getTableHeader().repaint();
        tableModel.fireTableDataChanged();
    }

    public UsersPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header
        JLabel header = new JLabel("Users in this SKA configuration:");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        add(header, BorderLayout.NORTH);

        // Table
        tableModel = new UserTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(200); // CN
        table.getColumnModel().getColumn(1).setPreferredWidth(140); // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(200); // Email
        table.getColumnModel().getColumn(3).setPreferredWidth(100); // Org
        table.getColumnModel().getColumn(4).setPreferredWidth(80);  // userId
        table.getColumnModel().getColumn(5).setPreferredWidth(60);  // Cert
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

        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(removeBtn);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(viewCertBtn);
        add(buttons, BorderLayout.SOUTH);
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

    // --- Model ↔ UI ---

    public void loadFrom(List<User> users) {
        this.users = users;
        tableModel.fireTableDataChanged();
    }

    /**
     * Returns the live user list (same reference as the model).
     * No need for saveTo — the list is edited in place.
     */
    public List<User> getUsers() {
        return users;
    }

    // --- Table model ---

    private class UserTableModel extends AbstractTableModel {

        private final String[] COLUMNS = {"CN", "Name", "Email", "Organisation", "UserID (Prod)", "Cert"};

        @Override public int getRowCount() { return users.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) {
            if (col == 4) return integrationEnvironment ? "UserID (Int)" : "UserID (Prod)";
            return COLUMNS[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            User u = users.get(row);
            return switch (col) {
                case 0 -> u.getCn();
                case 1 -> u.getName();
                case 2 -> u.getEmail();
                case 3 -> u.getOrganisation();
                case 4 -> integrationEnvironment ? u.getUserIdIntegration() : u.getUserId();
                case 5 -> u.getCertificate().isEmpty() ? "—" : "\u2713"; // checkmark or dash
                default -> "";
            };
        }
    }
}
