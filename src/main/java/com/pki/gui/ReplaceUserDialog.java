package com.pki.gui;

import com.pki.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog that lets the user replace one person with another across all open
 * SKA configurations.  The outgoing user's group memberships (quorum groups)
 * and org roles are transferred to the replacement, while the replacement
 * keeps their own identity (CN, name, email, userId, certificate).
 *
 * <p>Workflow: Select outgoing → Select replacement → Simulate → Commit.</p>
 */
public class ReplaceUserDialog extends JDialog {

    private final SkaWorkspace workspace;
    private final List<User> pool;

    private final JComboBox<UserItem> outgoingCombo;
    private final JComboBox<UserItem> replacementCombo;
    private final JButton simulateBtn;
    private final JButton commitBtn;
    private final JTextArea reportArea;

    private List<ChangeRecord> pendingChanges;
    private boolean committed = false;

    /**
     * @param owner     parent frame
     * @param workspace the current workspace (must not be empty)
     * @param pool      master user pool
     */
    public ReplaceUserDialog(Frame owner, SkaWorkspace workspace, List<User> pool) {
        super(owner, "Replace User Across All SKAs", true);
        this.workspace = workspace;
        this.pool = pool;

        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setMinimumSize(new Dimension(700, 500));

        // --- Selection panel (top) ---
        JPanel selPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        selPanel.add(new JLabel("Outgoing user (to be replaced):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        outgoingCombo = new JComboBox<>(buildUserItems());
        selPanel.add(outgoingCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        selPanel.add(new JLabel("Replacement user:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        replacementCombo = new JComboBox<>(buildUserItems());
        if (replacementCombo.getItemCount() > 1) replacementCombo.setSelectedIndex(1);
        selPanel.add(replacementCombo, gbc);

        add(selPanel, BorderLayout.NORTH);

        // --- Report area (center) ---
        reportArea = new JTextArea();
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        reportArea.setEditable(false);
        reportArea.setText("Click \"Simulate\" to preview the changes.");
        JScrollPane scroll = new JScrollPane(reportArea);
        scroll.setPreferredSize(new Dimension(660, 320));
        add(scroll, BorderLayout.CENTER);

        // --- Buttons (bottom) ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        simulateBtn = new JButton("Simulate");
        commitBtn = new JButton("Commit Changes");
        JButton cancelBtn = new JButton("Cancel");

        commitBtn.setEnabled(false);
        simulateBtn.addActionListener(e -> doSimulate());
        commitBtn.addActionListener(e -> doCommit());
        cancelBtn.addActionListener(e -> dispose());

        btnPanel.add(simulateBtn);
        btnPanel.add(commitBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    /** Whether changes were committed. */
    public boolean isCommitted() { return committed; }

    // --- Build combo items ---

    private UserItem[] buildUserItems() {
        List<UserItem> items = new ArrayList<>();
        for (User u : pool) {
            items.add(new UserItem(u));
        }
        items.sort(Comparator.comparing(a -> a.user.getCn()));
        return items.toArray(new UserItem[0]);
    }

    /** Wrapper so the combo shows a readable label. */
    private static class UserItem {
        final User user;
        UserItem(User u) { this.user = u; }
        @Override public String toString() {
            String label = user.getCn();
            if (!user.getName().isEmpty()) label += "  (" + user.getName() + ")";
            return label;
        }
    }

    // --- Simulate ---

    private void doSimulate() {
        UserItem outItem = (UserItem) outgoingCombo.getSelectedItem();
        UserItem repItem = (UserItem) replacementCombo.getSelectedItem();
        if (outItem == null || repItem == null) return;
        if (outItem.user.getCn().equals(repItem.user.getCn())) {
            reportArea.setText("Outgoing and replacement users must be different.");
            commitBtn.setEnabled(false);
            return;
        }

        String outCn = outItem.user.getCn();
        String repCn = repItem.user.getCn();

        pendingChanges = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        report.append("=== Replacement Simulation ===\n\n");
        report.append("Outgoing  : ").append(outCn).append("\n");
        report.append("Replacement: ").append(repCn).append("\n\n");

        for (SkaConfigEntry entry : workspace.getEntries()) {
            SkaConfig cfg = entry.getConfig();
            List<String> entryChanges = new ArrayList<>();

            // Check user list membership
            boolean outInUsers = cfg.getUsers().stream()
                    .anyMatch(u -> u.getCn().equals(outCn));
            boolean repAlreadyInUsers = cfg.getUsers().stream()
                    .anyMatch(u -> u.getCn().equals(repCn));

            if (outInUsers) {
                if (!repAlreadyInUsers) {
                    entryChanges.add("  \u2022 Add " + repCn + " to user list");
                    pendingChanges.add(new ChangeRecord(entry, ChangeType.ADD_USER_TO_LIST, null, null, null, repCn));
                }
                entryChanges.add("  \u2022 Remove " + outCn + " from user list");
                pendingChanges.add(new ChangeRecord(entry, ChangeType.REMOVE_USER_FROM_LIST, null, null, null, outCn));
            }

            // Check all sections / operations / boundaries / groups
            scanSection(entry, cfg.getOrganization(), "Organization", outCn, repCn, entryChanges);
            scanSection(entry, cfg.getSkaPlus(), "SkaPlus", outCn, repCn, entryChanges);
            scanSection(entry, cfg.getSkaModify(), "SkaModify", outCn, repCn, entryChanges);

            // Keys section
            String keysLabel = "Keys(" + cfg.getKeysProto().getChildName() + ")";
            scanKeysProto(entry, cfg.getKeysProto(), keysLabel, outCn, repCn, entryChanges);

            if (!entryChanges.isEmpty()) {
                report.append("--- ").append(entry.getDisplayLabel()).append(" ---\n");
                for (String c : entryChanges) report.append(c).append("\n");
                report.append("\n");
            }
        }

        // Org roles transfer (pool-level)
        User outUser = workspace.findUserByCn(outCn);
        User repUser = workspace.findUserByCn(repCn);
        if (outUser != null && repUser != null) {
            List<String> roleChanges = new ArrayList<>();
            transferRolePreview(outUser.getOrgOwnerOf(), repUser.getOrgOwnerOf(), "Org Owner", roleChanges);
            transferRolePreview(outUser.getOrgSecOffOf(), repUser.getOrgSecOffOf(), "Org SecOff", roleChanges);
            transferRolePreview(outUser.getOrgOpOf(), repUser.getOrgOpOf(), "Org Op", roleChanges);
            if (!roleChanges.isEmpty()) {
                report.append("--- Org Role Transfers (pool level) ---\n");
                for (String c : roleChanges) report.append(c).append("\n");
                report.append("\n");
                pendingChanges.add(new ChangeRecord(null, ChangeType.TRANSFER_ROLES, null, null, null, null));
            }
        }

        if (pendingChanges.isEmpty()) {
            report.append("No changes needed — outgoing user has no memberships.\n");
        } else {
            report.append("Total: ").append(pendingChanges.size()).append(" change(s) to apply.\n");
        }

        reportArea.setText(report.toString());
        reportArea.setCaretPosition(0);
        commitBtn.setEnabled(!pendingChanges.isEmpty());
    }

    private void scanSection(SkaConfigEntry entry, SkaSection section,
                             String sectionName, String outCn, String repCn,
                             List<String> changes) {
        Operations ops = section.getOperations();
        scanOperation(entry, ops.getUse(), sectionName, "use", outCn, repCn, changes);
        scanOperation(entry, ops.getModify(), sectionName, "modify", outCn, repCn, changes);
        scanOperation(entry, ops.getBlock(), sectionName, "block", outCn, repCn, changes);
        scanOperation(entry, ops.getUnblock(), sectionName, "unblock", outCn, repCn, changes);
    }

    private void scanKeysProto(SkaConfigEntry entry, KeysProto keysProto,
                               String label, String outCn, String repCn,
                               List<String> changes) {
        Operations ops = keysProto.getOperations();
        scanOperation(entry, ops.getUse(), label, "use", outCn, repCn, changes);
        scanOperation(entry, ops.getModify(), label, "modify", outCn, repCn, changes);
        scanOperation(entry, ops.getBlock(), label, "block", outCn, repCn, changes);
        scanOperation(entry, ops.getUnblock(), label, "unblock", outCn, repCn, changes);
    }

    private void scanOperation(SkaConfigEntry entry, Operation op,
                                String sectionName, String opName,
                                String outCn, String repCn,
                                List<String> changes) {
        for (int bi = 0; bi < op.getBoundaries().size(); bi++) {
            Boundary b = op.getBoundaries().get(bi);
            for (int gi = 0; gi < b.getGroups().size(); gi++) {
                Group g = b.getGroups().get(gi);
                if (g.getMemberCns().contains(outCn)) {
                    String groupDesc = sectionName + " > " + opName
                            + " > boundary " + (bi + 1)
                            + " > group" + (g.getName().isEmpty() ? " " + (gi + 1) : " \"" + g.getName() + "\"");
                    changes.add("  \u2022 " + groupDesc + ": " + outCn + " \u2192 " + repCn);
                    pendingChanges.add(new ChangeRecord(entry, ChangeType.REPLACE_IN_GROUP,
                            op, b, g, null));
                }
            }
        }
    }

    private void transferRolePreview(Set<String> outRoles, Set<String> repRoles,
                                     String roleName, List<String> changes) {
        for (String role : outRoles) {
            if (!repRoles.contains(role)) {
                changes.add("  \u2022 " + roleName + ": transfer \"" + role + "\"");
            }
        }
    }

    // --- Commit ---

    private void doCommit() {
        UserItem outItem = (UserItem) outgoingCombo.getSelectedItem();
        UserItem repItem = (UserItem) replacementCombo.getSelectedItem();
        if (outItem == null || repItem == null || pendingChanges == null) return;

        String outCn = outItem.user.getCn();
        String repCn = repItem.user.getCn();

        // Apply group membership replacements across all entries
        for (SkaConfigEntry entry : workspace.getEntries()) {
            SkaConfig cfg = entry.getConfig();
            replaceInSection(cfg.getOrganization(), outCn, repCn);
            replaceInSection(cfg.getSkaPlus(), outCn, repCn);
            replaceInSection(cfg.getSkaModify(), outCn, repCn);
            replaceInKeysProto(cfg.getKeysProto(), outCn, repCn);

            // User list: add replacement if not present, remove outgoing
            boolean outInUsers = cfg.getUsers().stream()
                    .anyMatch(u -> u.getCn().equals(outCn));
            if (outInUsers) {
                boolean repInUsers = cfg.getUsers().stream()
                        .anyMatch(u -> u.getCn().equals(repCn));
                if (!repInUsers) {
                    // Copy from pool
                    User poolRep = workspace.findUserByCn(repCn);
                    if (poolRep != null) {
                        User copy = deepCopyUser(poolRep);
                        cfg.getUsers().add(copy);
                    }
                }
                cfg.getUsers().removeIf(u -> u.getCn().equals(outCn));
                entry.setDirty(true);
            }
        }

        // Transfer org roles in the pool
        User outPool = workspace.findUserByCn(outCn);
        User repPool = workspace.findUserByCn(repCn);
        if (outPool != null && repPool != null) {
            repPool.getOrgOwnerOf().addAll(outPool.getOrgOwnerOf());
            outPool.getOrgOwnerOf().clear();
            repPool.getOrgSecOffOf().addAll(outPool.getOrgSecOffOf());
            outPool.getOrgSecOffOf().clear();
            repPool.getOrgOpOf().addAll(outPool.getOrgOpOf());
            outPool.getOrgOpOf().clear();
        }

        // Update workspace CN sets: remove outgoing, add replacement
        for (SkaConfigEntry entry : workspace.getEntries()) {
            Set<String> cns = workspace.getCnsForEntry(entry);
            // Rebuild from updated user list
            Set<String> newCns = new LinkedHashSet<>();
            for (User u : entry.getConfig().getUsers()) {
                newCns.add(u.getCn());
            }
            // Note: cns is a snapshot; the real state is in the entry's user list
        }

        committed = true;
        reportArea.append("\n\u2705 Changes committed successfully.\n");
        reportArea.setCaretPosition(reportArea.getDocument().getLength());
        commitBtn.setEnabled(false);
        simulateBtn.setEnabled(false);
    }

    private void replaceInSection(SkaSection section, String outCn, String repCn) {
        Operations ops = section.getOperations();
        replaceInOperation(ops.getUse(), outCn, repCn);
        replaceInOperation(ops.getModify(), outCn, repCn);
        replaceInOperation(ops.getBlock(), outCn, repCn);
        replaceInOperation(ops.getUnblock(), outCn, repCn);
    }

    private void replaceInKeysProto(KeysProto keysProto, String outCn, String repCn) {
        Operations ops = keysProto.getOperations();
        replaceInOperation(ops.getUse(), outCn, repCn);
        replaceInOperation(ops.getModify(), outCn, repCn);
        replaceInOperation(ops.getBlock(), outCn, repCn);
        replaceInOperation(ops.getUnblock(), outCn, repCn);
    }

    private void replaceInOperation(Operation op, String outCn, String repCn) {
        for (Boundary b : op.getBoundaries()) {
            for (Group g : b.getGroups()) {
                List<String> members = g.getMemberCns();
                int idx = members.indexOf(outCn);
                if (idx >= 0) {
                    if (members.contains(repCn)) {
                        // Replacement already in group — just remove outgoing
                        members.remove(idx);
                    } else {
                        // Swap in-place to preserve ordering
                        members.set(idx, repCn);
                    }
                }
            }
        }
    }

    private static User deepCopyUser(User src) {
        User copy = new User();
        copy.setCn(src.getCn());
        copy.setName(src.getName());
        copy.setEmail(src.getEmail());
        copy.setOrganisation(src.getOrganisation());
        copy.setUserId(src.getUserId());
        copy.setUserIdIntegration(src.getUserIdIntegration());
        copy.setCertificate(src.getCertificate());
        copy.setOrgOwnerOf(new LinkedHashSet<>(src.getOrgOwnerOf()));
        copy.setOrgSecOffOf(new LinkedHashSet<>(src.getOrgSecOffOf()));
        copy.setOrgOpOf(new LinkedHashSet<>(src.getOrgOpOf()));
        return copy;
    }

    // --- Change tracking ---

    private enum ChangeType {
        REPLACE_IN_GROUP,
        ADD_USER_TO_LIST,
        REMOVE_USER_FROM_LIST,
        TRANSFER_ROLES
    }

    private record ChangeRecord(
            SkaConfigEntry entry,
            ChangeType type,
            Operation operation,
            Boundary boundary,
            Group group,
            String cn
    ) {}
}
