package com.pki;

import com.formdev.flatlaf.FlatLightLaf;
import com.pki.gui.MainFrame;

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
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
