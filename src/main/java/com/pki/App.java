package com.pki;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

/**
 * SKA Configurator — entry point.
 * Launches the Swing GUI for creating and editing SKA configuration files.
 */
public class App {

    public static void main(String[] args) {
        // Set modern look-and-feel before any Swing component is created
        FlatLightLaf.setup();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SKA Configurator");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1024, 768);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
