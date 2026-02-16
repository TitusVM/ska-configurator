package com.pki.gui;

import com.pki.model.SkaConfig;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Panel for editing the top-level SKA configuration attributes:
 * moduleName, version, and per-section metadata (blockedOnInitialize, validity dates).
 */
public class GlobalConfigPanel extends JPanel {

    private final JTextField moduleNameField = new JTextField(20);
    private final JSpinner versionSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JTextField keysChildNameField = new JTextField(12);
    private final JTextField schemaLocationField = new JTextField(20);

    // Organization section
    private final JTextField orgKeyLabel = new JTextField(25);
    private final JTextField orgStartValidity = new JTextField(12);
    private final JTextField orgEndValidity = new JTextField(12);
    private final JCheckBox orgBlocked = new JCheckBox("Blocked on initialize");

    // SKA Plus section
    private final JTextField plusKeyLabel = new JTextField(25);
    private final JTextField plusStartValidity = new JTextField(12);
    private final JTextField plusEndValidity = new JTextField(12);
    private final JCheckBox plusBlocked = new JCheckBox("Blocked on initialize");

    // SKA Modify section
    private final JTextField modKeyLabel = new JTextField(25);
    private final JTextField modStartValidity = new JTextField(12);
    private final JTextField modEndValidity = new JTextField(12);
    private final JCheckBox modBlocked = new JCheckBox("Blocked on initialize");

    private Consumer<String> keysChildNameChangeListener;

    public void setKeysChildNameChangeListener(Consumer<String> listener) {
        this.keysChildNameChangeListener = listener;
    }

    public GlobalConfigPanel() {
        // Set tooltips
        moduleNameField.setToolTipText("Module name identifier (e.g. EDOC-PP-CERT-01)");
        keysChildNameField.setToolTipText("Element name inside <keys> (e.g. proto, operational)");
        schemaLocationField.setToolTipText("XSD schema file (xsi:noNamespaceSchemaLocation, e.g. skaconfig.xsd)");

        // Fire listener on every keystroke in keysChildNameField
        keysChildNameField.getDocument().addDocumentListener(new DocumentListener() {
            private void fire() {
                if (keysChildNameChangeListener != null) {
                    keysChildNameChangeListener.accept(keysChildNameField.getText().trim());
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { fire(); }
            @Override public void removeUpdate(DocumentEvent e) { fire(); }
            @Override public void changedUpdate(DocumentEvent e) { fire(); }
        });
        orgStartValidity.setToolTipText("Format: YYYY-MM-DD  (e.g. 2025-01-01)");;
        orgEndValidity.setToolTipText("Format: YYYY-MM-DD  (e.g. 2030-12-31)");
        plusStartValidity.setToolTipText("Format: YYYY-MM-DD");
        plusEndValidity.setToolTipText("Format: YYYY-MM-DD");
        modStartValidity.setToolTipText("Format: YYYY-MM-DD");
        modEndValidity.setToolTipText("Format: YYYY-MM-DD");
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // --- Top-level config ---
        content.add(createTitledSection("Module Configuration", createModulePanel()));
        content.add(Box.createVerticalStrut(10));

        // --- Per-section metadata ---
        content.add(createTitledSection("Organization Key", createSectionPanel(
                orgKeyLabel, orgStartValidity, orgEndValidity, orgBlocked)));
        content.add(Box.createVerticalStrut(10));

        content.add(createTitledSection("SKA Plus Key", createSectionPanel(
                plusKeyLabel, plusStartValidity, plusEndValidity, plusBlocked)));
        content.add(Box.createVerticalStrut(10));

        content.add(createTitledSection("SKA Modify Key", createSectionPanel(
                modKeyLabel, modStartValidity, modEndValidity, modBlocked)));

        content.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        add(scrollPane, BorderLayout.CENTER);
    }

    // --- UI builders ---

    private JPanel createModulePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.add(new JLabel("Module name:"));
        panel.add(moduleNameField);
        panel.add(Box.createHorizontalStrut(16));
        panel.add(new JLabel("Version:"));
        panel.add(versionSpinner);
        panel.add(Box.createHorizontalStrut(16));
        panel.add(new JLabel("Keys child name:"));
        panel.add(keysChildNameField);
        panel.add(Box.createHorizontalStrut(16));
        panel.add(new JLabel("XSD schema:"));
        panel.add(schemaLocationField);
        return panel;
    }

    private JPanel createSectionPanel(JTextField keyLabel, JTextField startVal,
                                       JTextField endVal, JCheckBox blocked) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row1.add(new JLabel("Key label:"));
        row1.add(keyLabel);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.add(new JLabel("Start validity (YYYY-MM-DD):"));
        row2.add(startVal);
        row2.add(Box.createHorizontalStrut(8));
        row2.add(new JLabel("End validity:"));
        row2.add(endVal);
        row2.add(Box.createHorizontalStrut(8));
        row2.add(blocked);

        panel.add(row1);
        panel.add(row2);
        return panel;
    }

    private JPanel createTitledSection(String title, JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(title));
        wrapper.add(content, BorderLayout.CENTER);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height + 20));
        return wrapper;
    }

    // --- Model ↔ UI ---

    public void loadFrom(SkaConfig config) {
        moduleNameField.setText(config.getModuleName());
        versionSpinner.setValue(config.getVersion());
        keysChildNameField.setText(config.getKeysProto().getChildName());
        schemaLocationField.setText(config.getXsiNoNamespaceSchemaLocation());

        // Organization
        orgKeyLabel.setText(config.getOrganization().getKeyLabel());
        orgStartValidity.setText(config.getOrganization().getStartValidity());
        orgEndValidity.setText(config.getOrganization().getEndValidity());
        orgBlocked.setSelected(config.getOrganization().isBlockedOnInitialize());

        // SKA Plus
        plusKeyLabel.setText(config.getSkaPlus().getKeyLabel());
        plusStartValidity.setText(config.getSkaPlus().getStartValidity());
        plusEndValidity.setText(config.getSkaPlus().getEndValidity());
        plusBlocked.setSelected(config.getSkaPlus().isBlockedOnInitialize());

        // SKA Modify
        modKeyLabel.setText(config.getSkaModify().getKeyLabel());
        modStartValidity.setText(config.getSkaModify().getStartValidity());
        modEndValidity.setText(config.getSkaModify().getEndValidity());
        modBlocked.setSelected(config.getSkaModify().isBlockedOnInitialize());
    }

    public void saveTo(SkaConfig config) {
        config.setModuleName(moduleNameField.getText().trim());
        config.setVersion((int) versionSpinner.getValue());
        config.setXsiNoNamespaceSchemaLocation(schemaLocationField.getText().trim());
        String childName = keysChildNameField.getText().trim();
        if (!childName.isEmpty()) {
            config.getKeysProto().setChildName(childName);
        }

        // Organization
        config.getOrganization().setKeyLabel(orgKeyLabel.getText().trim());
        config.getOrganization().setStartValidity(orgStartValidity.getText().trim());
        config.getOrganization().setEndValidity(orgEndValidity.getText().trim());
        config.getOrganization().setBlockedOnInitialize(orgBlocked.isSelected());

        // SKA Plus
        config.getSkaPlus().setKeyLabel(plusKeyLabel.getText().trim());
        config.getSkaPlus().setStartValidity(plusStartValidity.getText().trim());
        config.getSkaPlus().setEndValidity(plusEndValidity.getText().trim());
        config.getSkaPlus().setBlockedOnInitialize(plusBlocked.isSelected());

        // SKA Modify
        config.getSkaModify().setKeyLabel(modKeyLabel.getText().trim());
        config.getSkaModify().setStartValidity(modStartValidity.getText().trim());
        config.getSkaModify().setEndValidity(modEndValidity.getText().trim());
        config.getSkaModify().setBlockedOnInitialize(modBlocked.isSelected());
    }

    /**
     * Validate date fields and return any warnings.
     */
    public List<String> validateFields() {
        List<String> warnings = new ArrayList<>();
        Pattern datePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

        validateDate(orgStartValidity.getText().trim(), "Organization start validity", datePattern, warnings);
        validateDate(orgEndValidity.getText().trim(), "Organization end validity", datePattern, warnings);
        validateDate(plusStartValidity.getText().trim(), "SKA Plus start validity", datePattern, warnings);
        validateDate(plusEndValidity.getText().trim(), "SKA Plus end validity", datePattern, warnings);
        validateDate(modStartValidity.getText().trim(), "SKA Modify start validity", datePattern, warnings);
        validateDate(modEndValidity.getText().trim(), "SKA Modify end validity", datePattern, warnings);

        return warnings;
    }

    private void validateDate(String value, String label, Pattern pattern, List<String> warnings) {
        if (!value.isEmpty() && !pattern.matcher(value).matches()) {
            warnings.add(label + " is not in YYYY-MM-DD format: \"" + value + "\"");
        }
    }
}
