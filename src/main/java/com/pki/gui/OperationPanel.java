package com.pki.gui;

import com.pki.model.Boundary;
import com.pki.model.Group;
import com.pki.model.Operation;
import com.pki.model.User;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Panel for editing a single Operation (use, modify, block, or unblock).
 * Left side: list of boundaries. Right side: groups within the selected boundary.
 * Bottom-right: members/keys within the selected group.
 */
public class OperationPanel extends JPanel {

    private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1L));
    private final JSpinner timeLimitSpinner = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1L));

    // Boundaries list
    private final DefaultListModel<String> boundaryListModel = new DefaultListModel<>();
    private final JList<String> boundaryList = new JList<>(boundaryListModel);
    private final List<Boundary> boundaries = new ArrayList<>();

    // Groups list
    private final DefaultListModel<String> groupListModel = new DefaultListModel<>();
    private final JList<String> groupList = new JList<>(groupListModel);

    // Group detail
    private final JTextField groupNameField = new JTextField(15);
    private final JSpinner quorumSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 99, 1));
    private final DefaultListModel<String> memberListModel = new DefaultListModel<>();
    private final JList<String> memberList = new JList<>(memberListModel);
    private final JTextField addMemberField = new JTextField(20);
    // Toggle between members and keys
    private final JRadioButton membersRadio = new JRadioButton("Members (CNs)", true);
    private final JRadioButton keysRadio = new JRadioButton("Keys (labels)");

    // Supplier for the current user list (set by MainFrame)
    private Supplier<List<User>> userListSupplier;

    public void setUserListSupplier(Supplier<List<User>> supplier) {
        this.userListSupplier = supplier;
    }

    public OperationPanel() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top row: delay & time limit
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        topRow.add(new JLabel("Delay (ms):"));
        delaySpinner.setPreferredSize(new Dimension(100, delaySpinner.getPreferredSize().height));
        topRow.add(delaySpinner);
        topRow.add(Box.createHorizontalStrut(16));
        topRow.add(new JLabel("Time limit (ms):"));
        timeLimitSpinner.setPreferredSize(new Dimension(100, timeLimitSpinner.getPreferredSize().height));
        topRow.add(timeLimitSpinner);
        add(topRow, BorderLayout.NORTH);

        // Main split: boundaries | groups+members
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createBoundaryPanel(), createGroupPanel());
        mainSplit.setDividerLocation(180);
        mainSplit.setResizeWeight(0.25);
        add(mainSplit, BorderLayout.CENTER);
    }

    // --- Boundary panel (left) ---

    private JPanel createBoundaryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Boundaries"));

        boundaryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        boundaryList.addListSelectionListener(this::onBoundarySelected);
        panel.add(new JScrollPane(boundaryList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> addBoundary());
        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> removeBoundary());
        buttons.add(addBtn);
        buttons.add(removeBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    // --- Group panel (right) ---

    private JPanel createGroupPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));

        // Top: group list
        JPanel groupListPanel = new JPanel(new BorderLayout(0, 4));
        groupListPanel.setBorder(BorderFactory.createTitledBorder("Groups"));
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.addListSelectionListener(this::onGroupSelected);
        groupListPanel.add(new JScrollPane(groupList), BorderLayout.CENTER);

        JPanel groupButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addGroupBtn = new JButton("Add Group");
        addGroupBtn.addActionListener(e -> addGroup());
        JButton removeGroupBtn = new JButton("Remove Group");
        removeGroupBtn.addActionListener(e -> removeGroup());
        groupButtons.add(addGroupBtn);
        groupButtons.add(removeGroupBtn);
        groupListPanel.add(groupButtons, BorderLayout.SOUTH);

        // Bottom: group detail (name, quorum, members/keys)
        JPanel detailPanel = createGroupDetailPanel();

        JSplitPane groupSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                groupListPanel, detailPanel);
        groupSplit.setDividerLocation(150);
        groupSplit.setResizeWeight(0.35);
        panel.add(groupSplit, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createGroupDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Group Detail"));

        // Group name + quorum row
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        meta.add(new JLabel("Name:"));
        meta.add(groupNameField);
        meta.add(Box.createHorizontalStrut(8));
        meta.add(new JLabel("Quorum:"));
        quorumSpinner.setPreferredSize(new Dimension(60, quorumSpinner.getPreferredSize().height));
        meta.add(quorumSpinner);
        meta.add(Box.createHorizontalStrut(8));
        ButtonGroup bg = new ButtonGroup();
        bg.add(membersRadio);
        bg.add(keysRadio);
        meta.add(membersRadio);
        meta.add(keysRadio);
        panel.add(meta, BorderLayout.NORTH);

        // Apply button to save group edits
        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> applyGroupDetail());

        // Members/keys list
        JPanel listPanel = new JPanel(new BorderLayout(0, 4));
        memberList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        listPanel.add(new JScrollPane(memberList), BorderLayout.CENTER);

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        addRow.add(new JLabel("Add:"));
        addRow.add(addMemberField);
        JButton addMemberBtn = new JButton("Add");
        addMemberBtn.addActionListener(e -> addMember());
        JButton removeMemberBtn = new JButton("Remove Selected");
        removeMemberBtn.addActionListener(e -> removeMember());
        addRow.add(addMemberBtn);
        addRow.add(removeMemberBtn);
        addRow.add(Box.createHorizontalStrut(8));
        JButton pickUsersBtn = new JButton("Pick from Users…");
        pickUsersBtn.addActionListener(e -> doPickUsers());
        addRow.add(pickUsersBtn);
        addRow.add(Box.createHorizontalStrut(16));
        addRow.add(applyBtn);
        listPanel.add(addRow, BorderLayout.SOUTH);

        panel.add(listPanel, BorderLayout.CENTER);
        return panel;
    }

    // --- Boundary operations ---

    private void addBoundary() {
        Boundary b = new Boundary();
        boundaries.add(b);
        refreshBoundaryList();
        boundaryList.setSelectedIndex(boundaries.size() - 1);
    }

    private void removeBoundary() {
        int idx = boundaryList.getSelectedIndex();
        if (idx < 0) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove this boundary and all its groups?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        boundaries.remove(idx);
        refreshBoundaryList();
        refreshGroupList();
    }

    private void refreshBoundaryList() {
        boundaryListModel.clear();
        for (int i = 0; i < boundaries.size(); i++) {
            Boundary b = boundaries.get(i);
            String label = "Boundary " + (i + 1) + " (" + b.getGroups().size() + " groups)";
            boundaryListModel.addElement(label);
        }
    }

    // --- Group operations ---

    private void onBoundarySelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        refreshGroupList();
    }

    private void refreshGroupList() {
        groupListModel.clear();
        clearGroupDetail();
        Boundary b = getSelectedBoundary();
        if (b == null) return;
        for (Group g : b.getGroups()) {
            groupListModel.addElement(g.getName() + " (q=" + g.getQuorum() + ")");
        }
    }

    private void addGroup() {
        Boundary b = getSelectedBoundary();
        if (b == null) {
            JOptionPane.showMessageDialog(this, "Select a boundary first.");
            return;
        }
        Group g = new Group();
        g.setName("New Group");
        b.getGroups().add(g);
        refreshGroupList();
        refreshBoundaryList();
        groupList.setSelectedIndex(b.getGroups().size() - 1);
    }

    private void removeGroup() {
        Boundary b = getSelectedBoundary();
        int idx = groupList.getSelectedIndex();
        if (b == null || idx < 0) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove group \"" + b.getGroups().get(idx).getName() + "\"?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        b.getGroups().remove(idx);
        refreshGroupList();
        refreshBoundaryList();
    }

    // --- Group detail ---

    private void onGroupSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        loadGroupDetail();
    }

    private void loadGroupDetail() {
        Group g = getSelectedGroup();
        if (g == null) {
            clearGroupDetail();
            return;
        }
        groupNameField.setText(g.getName());
        quorumSpinner.setValue(g.getQuorum());
        memberListModel.clear();

        boolean hasKeys = !g.getKeyLabels().isEmpty();
        keysRadio.setSelected(hasKeys);
        membersRadio.setSelected(!hasKeys);

        if (hasKeys) {
            for (String k : g.getKeyLabels()) memberListModel.addElement(k);
        } else {
            for (String cn : g.getMemberCns()) memberListModel.addElement(cn);
        }
    }

    private void clearGroupDetail() {
        groupNameField.setText("");
        quorumSpinner.setValue(1);
        memberListModel.clear();
        membersRadio.setSelected(true);
    }

    private void applyGroupDetail() {
        Group g = getSelectedGroup();
        if (g == null) {
            JOptionPane.showMessageDialog(this, "Select a group first.");
            return;
        }
        g.setName(groupNameField.getText().trim());
        g.setQuorum((int) quorumSpinner.getValue());

        // Collect members/keys from list
        List<String> items = new ArrayList<>();
        for (int i = 0; i < memberListModel.size(); i++) {
            items.add(memberListModel.get(i));
        }

        if (keysRadio.isSelected()) {
            g.setKeyLabels(items);
            g.setMemberCns(new ArrayList<>());
        } else {
            g.setMemberCns(items);
            g.setKeyLabels(new ArrayList<>());
        }

        refreshGroupList();
        refreshBoundaryList();
    }

    private void addMember() {
        String text = addMemberField.getText().trim();
        if (text.isEmpty()) return;
        memberListModel.addElement(text);
        addMemberField.setText("");
        addMemberField.requestFocusInWindow();
    }

    private void removeMember() {
        int idx = memberList.getSelectedIndex();
        if (idx >= 0) memberListModel.remove(idx);
    }

    private void doPickUsers() {
        if (keysRadio.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    "Switch to 'Members (CNs)' mode to pick users.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (userListSupplier == null) return;
        List<User> users = userListSupplier.get();
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No users available. Import a CSV or add users first.",
                    "No Users", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        UserPickerDialog dlg = new UserPickerDialog(
                owner instanceof JFrame ? (JFrame) owner : null, users);
        dlg.setVisible(true);
        for (String cn : dlg.getSelectedCns()) {
            // Avoid duplicates in the current member list
            boolean exists = false;
            for (int i = 0; i < memberListModel.size(); i++) {
                if (memberListModel.get(i).equals(cn)) { exists = true; break; }
            }
            if (!exists) memberListModel.addElement(cn);
        }
    }

    // --- Helpers ---

    private Boundary getSelectedBoundary() {
        int idx = boundaryList.getSelectedIndex();
        if (idx < 0 || idx >= boundaries.size()) return null;
        return boundaries.get(idx);
    }

    private Group getSelectedGroup() {
        Boundary b = getSelectedBoundary();
        int idx = groupList.getSelectedIndex();
        if (b == null || idx < 0 || idx >= b.getGroups().size()) return null;
        return b.getGroups().get(idx);
    }

    // --- Model ↔ UI ---

    public void loadFrom(Operation op) {
        delaySpinner.setValue(op.getDelayMillis());
        timeLimitSpinner.setValue(op.getTimeLimitMillis());
        boundaries.clear();
        // Deep copy boundaries so UI edits don't directly modify until apply
        for (Boundary orig : op.getBoundaries()) {
            Boundary copy = new Boundary();
            List<Group> groupsCopy = new ArrayList<>();
            for (Group g : orig.getGroups()) {
                Group gc = new Group();
                gc.setName(g.getName());
                gc.setQuorum(g.getQuorum());
                gc.setMemberCns(new ArrayList<>(g.getMemberCns()));
                gc.setKeyLabels(new ArrayList<>(g.getKeyLabels()));
                groupsCopy.add(gc);
            }
            copy.setGroups(groupsCopy);
            boundaries.add(copy);
        }
        refreshBoundaryList();
        if (!boundaries.isEmpty()) {
            boundaryList.setSelectedIndex(0);
        } else {
            refreshGroupList();
        }
    }

    public void saveTo(Operation op) {
        op.setDelayMillis((long) delaySpinner.getValue());
        op.setTimeLimitMillis((long) timeLimitSpinner.getValue());
        // Apply any pending group detail edits
        Group g = getSelectedGroup();
        if (g != null) applyGroupDetail();
        op.setBoundaries(new ArrayList<>(boundaries));
    }
}
