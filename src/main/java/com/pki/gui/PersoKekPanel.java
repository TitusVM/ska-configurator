package com.pki.gui;

import com.pki.model.EcParameters;
import com.pki.model.Personalization;
import com.pki.util.CurveUtils;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing the {@code <personalization>} element inside {@code <keys>}.
 * <p>
 * A top-level checkbox controls whether the tag is present at all.
 * When unchecked, all fields are disabled (greyed out).
 * <p>
 * Fields:
 * <ul>
 *   <li>useKek — boolean (checkbox)</li>
 *   <li>kekLabel — free-text label</li>
 *   <li>EC Parameters curve name — combo box (same set as other panels)</li>
 *   <li>EC Parameters PEM text — text area</li>
 * </ul>
 */
public class PersoKekPanel extends JPanel {

    private final JCheckBox enabledCheck;
    private final JCheckBox useKekCheck;
    private final JTextField kekLabelField;
    private final JComboBox<String> curveCombo;
    private final JTextArea pemArea;

    // Keep references for enable/disable toggling
    private final JLabel useKekLabel;
    private final JLabel kekLabelLabel;
    private final JLabel curveLabel;
    private final JLabel curveHint;
    private final JLabel pemLabel;

    public PersoKekPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // --- Enable checkbox at top ---
        enabledCheck = new JCheckBox("Enable Personalization KEK");
        enabledCheck.setFont(enabledCheck.getFont().deriveFont(Font.BOLD, 13f));
        enabledCheck.addActionListener(e -> updateEnabledState());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topPanel.add(enabledCheck);
        add(topPanel, BorderLayout.NORTH);

        // --- Settings panel (centre) ---
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Personalization Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 0: useKek checkbox
        gbc.gridx = 0; gbc.gridy = 0;
        useKekLabel = new JLabel("Use KEK:");
        settingsPanel.add(useKekLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        useKekCheck = new JCheckBox();
        settingsPanel.add(useKekCheck, gbc);
        gbc.gridwidth = 1;

        // Row 1: kekLabel
        gbc.gridx = 0; gbc.gridy = 1;
        kekLabelLabel = new JLabel("KEK Label:");
        settingsPanel.add(kekLabelLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        kekLabelField = new JTextField(30);
        settingsPanel.add(kekLabelField, gbc);
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;

        // Row 2: Curve name
        gbc.gridx = 0; gbc.gridy = 2;
        curveLabel = new JLabel("Curve name:");
        settingsPanel.add(curveLabel, gbc);
        gbc.gridx = 1;
        curveCombo = new JComboBox<>(CurveUtils.KNOWN_CURVES);
        curveCombo.setEditable(true);
        curveCombo.setPreferredSize(new Dimension(200, curveCombo.getPreferredSize().height));
        settingsPanel.add(curveCombo, gbc);
        gbc.gridx = 2;
        curveHint = new JLabel("(select or type custom)");
        curveHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        settingsPanel.add(curveHint, gbc);

        // Row 3: PEM label
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.NORTHWEST;
        pemLabel = new JLabel("EC Parameters PEM:");
        settingsPanel.add(pemLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        pemArea = new JTextArea(8, 60);
        pemArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pemArea.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(pemArea);
        settingsPanel.add(scroll, gbc);

        add(settingsPanel, BorderLayout.CENTER);

        // Initial state: disabled
        updateEnabledState();
    }

    /**
     * Enable or disable all fields based on the enabled checkbox.
     */
    private void updateEnabledState() {
        boolean on = enabledCheck.isSelected();
        useKekCheck.setEnabled(on);
        kekLabelField.setEnabled(on);
        curveCombo.setEnabled(on);
        pemArea.setEnabled(on);

        useKekLabel.setEnabled(on);
        kekLabelLabel.setEnabled(on);
        curveLabel.setEnabled(on);
        curveHint.setEnabled(on);
        pemLabel.setEnabled(on);
    }

    /**
     * Load model data into the panel.
     */
    public void loadFrom(Personalization perso) {
        enabledCheck.setSelected(perso.isEnabled());
        useKekCheck.setSelected(perso.isUseKek());
        kekLabelField.setText(perso.getKekLabel());

        EcParameters ec = perso.getEcParameters();
        curveCombo.setSelectedItem(ec.getCurveName() != null ? ec.getCurveName() : "");
        pemArea.setText(ec.getPemText() != null ? ec.getPemText() : "");
        pemArea.setCaretPosition(0);

        updateEnabledState();
    }

    /**
     * Save panel data back into the model.
     */
    public void saveTo(Personalization perso) {
        perso.setEnabled(enabledCheck.isSelected());
        perso.setUseKek(useKekCheck.isSelected());
        perso.setKekLabel(kekLabelField.getText().trim());

        EcParameters ec = perso.getEcParameters();
        Object selected = curveCombo.getSelectedItem();
        ec.setCurveName(selected != null ? selected.toString().trim() : "");
        ec.setPemText(pemArea.getText().trim());
    }
}
