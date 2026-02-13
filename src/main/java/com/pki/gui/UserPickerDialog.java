package com.pki.gui;

import com.pki.model.User;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog that shows the list of users in the configuration
 * and lets the user pick one or more to add as group members.
 */
public class UserPickerDialog extends JDialog {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(listModel);
    private final List<User> allUsers;
    private List<String> selectedCns = null;

    /**
     * @param owner parent frame
     * @param users list of available users
     */
    public UserPickerDialog(Frame owner, List<User> users) {
        super(owner, "Select Users", true);
        this.allUsers = users;

        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Info label
        add(new JLabel("Select users to add as group members (Ctrl+click for multiple):"),
                BorderLayout.NORTH);

        // User list
        for (User u : users) {
            listModel.addElement(u.getCn() + "  —  " + u.getName() + "  [" + u.getOrganisation() + "]");
        }
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        userList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        userList.setVisibleRowCount(15);
        add(new JScrollPane(userList), BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton okBtn = new JButton("Add Selected");
        okBtn.addActionListener(e -> doOk());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> doCancel());
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okBtn);
        setSize(600, 400);
        setLocationRelativeTo(owner);
    }

    private void doOk() {
        int[] indices = userList.getSelectedIndices();
        if (indices.length == 0) {
            JOptionPane.showMessageDialog(this, "No users selected.");
            return;
        }
        selectedCns = new ArrayList<>();
        for (int idx : indices) {
            selectedCns.add(allUsers.get(idx).getCn());
        }
        dispose();
    }

    private void doCancel() {
        selectedCns = null;
        dispose();
    }

    /**
     * @return list of selected CNs, or null if cancelled
     */
    public List<String> getSelectedCns() {
        return selectedCns;
    }
}
