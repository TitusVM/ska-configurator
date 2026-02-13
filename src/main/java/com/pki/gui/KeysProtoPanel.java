package com.pki.gui;

import com.pki.model.KeysProto;
import com.pki.model.Operations;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing the keys > proto section.
 * Same layout as SectionPanel but without key label / validity metadata
 * (those are handled at the global config level).
 */
public class KeysProtoPanel extends JPanel {

    private final EcParametersPanel ecPanel;
    private final OperationPanel usePanel;
    private final OperationPanel modifyPanel;
    private final OperationPanel blockPanel;
    private final OperationPanel unblockPanel;

    public KeysProtoPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // EC Parameters at top
        ecPanel = new EcParametersPanel();
        ecPanel.setPreferredSize(new Dimension(0, 180));
        add(ecPanel, BorderLayout.NORTH);

        // Operation tabs
        JTabbedPane opTabs = new JTabbedPane();
        usePanel = new OperationPanel();
        modifyPanel = new OperationPanel();
        blockPanel = new OperationPanel();
        unblockPanel = new OperationPanel();
        opTabs.addTab("Use", usePanel);
        opTabs.addTab("Modify", modifyPanel);
        opTabs.addTab("Block", blockPanel);
        opTabs.addTab("Unblock", unblockPanel);
        add(opTabs, BorderLayout.CENTER);
    }

    public void loadFrom(KeysProto kp) {
        ecPanel.loadFrom(kp.getEcParameters());
        Operations ops = kp.getOperations();
        usePanel.loadFrom(ops.getUse());
        modifyPanel.loadFrom(ops.getModify());
        blockPanel.loadFrom(ops.getBlock());
        unblockPanel.loadFrom(ops.getUnblock());
    }

    public void saveTo(KeysProto kp) {
        ecPanel.saveTo(kp.getEcParameters());
        Operations ops = kp.getOperations();
        usePanel.saveTo(ops.getUse());
        modifyPanel.saveTo(ops.getModify());
        blockPanel.saveTo(ops.getBlock());
        unblockPanel.saveTo(ops.getUnblock());
    }
}
