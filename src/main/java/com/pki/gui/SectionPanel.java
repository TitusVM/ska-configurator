package com.pki.gui;

import com.pki.model.Operations;
import com.pki.model.SkaSection;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing an SKA section (organization, skaplus, or skamodify).
 * Contains EC parameters at the top and tabbed operation editors below.
 */
public class SectionPanel extends JPanel {

    private final EcParametersPanel ecPanel;
    private final OperationPanel usePanel;
    private final OperationPanel modifyPanel;
    private final OperationPanel blockPanel;
    private final OperationPanel unblockPanel;

    public SectionPanel() {
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

    public void loadFrom(SkaSection section) {
        ecPanel.loadFrom(section.getEcParameters());
        Operations ops = section.getOperations();
        usePanel.loadFrom(ops.getUse());
        modifyPanel.loadFrom(ops.getModify());
        blockPanel.loadFrom(ops.getBlock());
        unblockPanel.loadFrom(ops.getUnblock());
    }

    public void saveTo(SkaSection section) {
        ecPanel.saveTo(section.getEcParameters());
        Operations ops = section.getOperations();
        usePanel.saveTo(ops.getUse());
        modifyPanel.saveTo(ops.getModify());
        blockPanel.saveTo(ops.getBlock());
        unblockPanel.saveTo(ops.getUnblock());
    }
}
