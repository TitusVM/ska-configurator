package com.pki.gui;

import com.pki.model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal dialog for adding or editing a user.
 * If a user is provided, the dialog edits it in place.
 * Otherwise, a new User is created on OK.
 */
public class UserEditDialog extends JDialog {

    private final JTextField cnField = new JTextField(30);
    private final JTextField nameField = new JTextField(30);
    private final JTextField emailField = new JTextField(30);
    private final JTextField organisationField = new JTextField(20);
    private final JTextField userIdField = new JTextField(15);
    private final JTextArea certArea = new JTextArea(12, 60);

    private User result = null;
    private final User editing;
    private List<String> existingCns;
    private Consumer<String> statusCallback;

    /**
     * @param owner  parent frame
     * @param user   user to edit (null = create new)
     */
    public UserEditDialog(Frame owner, User user) {
        super(owner, user == null ? "Add User" : "Edit User", true);
        this.editing = user;

        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Form fields
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addFormRow(form, gbc, row++, "CN (Common Name):", cnField);
        addFormRow(form, gbc, row++, "Name:", nameField);
        addFormRow(form, gbc, row++, "Email:", emailField);
        addFormRow(form, gbc, row++, "Organisation:", organisationField);
        addFormRow(form, gbc, row++, "User ID:", userIdField);

        // Certificate label
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Certificate (PEM):"), gbc);

        add(form, BorderLayout.NORTH);

        // Certificate text area
        certArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        certArea.setLineWrap(false);
        JScrollPane certScroll = new JScrollPane(certArea);
        add(certScroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> doOk());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> doCancel());
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okBtn);

        // Populate fields if editing
        if (user != null) {
            cnField.setText(user.getCn());
            nameField.setText(user.getName());
            emailField.setText(user.getEmail());
            organisationField.setText(user.getOrganisation());
            userIdField.setText(user.getUserId());
            certArea.setText(user.getCertificate());
            certArea.setCaretPosition(0);
        }

        setSize(700, 550);
        setLocationRelativeTo(owner);

        // Escape key closes the dialog
        getRootPane().registerKeyboardAction(e -> doCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Set existing CNs to check for duplicates when adding.
     */
    public void setExistingCns(List<String> cns) {
        this.existingCns = cns;
    }

    /**
     * Set a callback for status bar messages.
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    private void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                            String label, JTextField field) {
        gbc.gridx = 0; gbc.gridy = row;
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        form.add(field, gbc);
        gbc.weightx = 0;
    }

    private void doOk() {
        String cn = cnField.getText().trim();
        if (cn.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "CN (Common Name) is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            cnField.requestFocusInWindow();
            return;
        }

        // Check for duplicate CN on add (not on edit of same user)
        if (existingCns != null && (editing == null || !editing.getCn().equals(cn))) {
            if (existingCns.contains(cn)) {
                JOptionPane.showMessageDialog(this,
                        "A user with CN \"" + cn + "\" already exists.",
                        "Duplicate CN", JOptionPane.WARNING_MESSAGE);
                cnField.requestFocusInWindow();
                return;
            }
        }

        // Validate certificate PEM format if provided
        String cert = certArea.getText().trim();
        if (!cert.isEmpty() && !isValidPem(cert)) {
            int ans = JOptionPane.showConfirmDialog(this,
                    "The certificate does not appear to be valid PEM format\n"
                            + "(missing BEGIN/END CERTIFICATE markers).\n\n"
                            + "Save it anyway?",
                    "PEM Format Warning", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (ans != JOptionPane.YES_OPTION) {
                certArea.requestFocusInWindow();
                return;
            }
        }

        if (editing != null) {
            // Edit in place
            editing.setCn(cn);
            editing.setName(nameField.getText().trim());
            editing.setEmail(emailField.getText().trim());
            editing.setOrganisation(organisationField.getText().trim());
            editing.setUserId(userIdField.getText().trim());
            editing.setCertificate(certArea.getText().trim());
            result = editing;
        } else {
            // Create new
            User u = new User();
            u.setCn(cn);
            u.setName(nameField.getText().trim());
            u.setEmail(emailField.getText().trim());
            u.setOrganisation(organisationField.getText().trim());
            u.setUserId(userIdField.getText().trim());
            u.setCertificate(certArea.getText().trim());
            result = u;
        }
        dispose();
    }

    private boolean isValidPem(String text) {
        return text.contains("-----BEGIN CERTIFICATE-----")
                && text.contains("-----END CERTIFICATE-----");
    }

    private void doCancel() {
        result = null;
        dispose();
    }

    /**
     * @return the created/edited user, or null if cancelled
     */
    public User getResult() {
        return result;
    }
}
