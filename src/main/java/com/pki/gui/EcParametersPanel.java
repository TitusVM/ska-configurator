package com.pki.gui;

import com.pki.model.EcParameters;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing EC parameters: optional curve name (combo + free text)
 * and raw PEM text area.
 */
public class EcParametersPanel extends JPanel {

    private static final String[] KNOWN_CURVES = {
            "", "brainpoolP256r1", "brainpoolP384r1", "brainpoolP512r1",
            "prime256v1", "secp384r1", "secp521r1"
    };

    private final JComboBox<String> curveCombo;
    private final JTextArea pemArea;

    public EcParametersPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createTitledBorder("EC Parameters"));

        // Curve name row
        JPanel curveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        curveRow.add(new JLabel("Curve name:"));
        curveCombo = new JComboBox<>(KNOWN_CURVES);
        curveCombo.setEditable(true); // allow free text
        curveCombo.setPreferredSize(new Dimension(200, curveCombo.getPreferredSize().height));
        curveRow.add(curveCombo);
        curveRow.add(new JLabel("(select or type custom)"));
        add(curveRow, BorderLayout.NORTH);

        // PEM text area
        pemArea = new JTextArea(5, 60);
        pemArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pemArea.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(pemArea);
        add(scroll, BorderLayout.CENTER);
    }

    public void loadFrom(EcParameters ec) {
        curveCombo.setSelectedItem(ec.getCurveName() != null ? ec.getCurveName() : "");
        pemArea.setText(ec.getPemText() != null ? ec.getPemText() : "");
        pemArea.setCaretPosition(0);
    }

    public void saveTo(EcParameters ec) {
        Object selected = curveCombo.getSelectedItem();
        ec.setCurveName(selected != null ? selected.toString().trim() : "");
        ec.setPemText(pemArea.getText().trim());
    }
}
