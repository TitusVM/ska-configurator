package com.pki.gui;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests for the version-and-environment-in-filename logic.
 */
public class VersionFilenameTest {

    @Test
    public void testPlainFilename() {
        File result = MainFrame.applyVersionToFilename(new File("ska.xml"), 3, "", "");
        assertEquals("ska_v3.xml", result.getName());
    }

    @Test
    public void testReplacesExistingVersion() {
        File result = MainFrame.applyVersionToFilename(new File("ska_v2.xml"), 5, "", "");
        assertEquals("ska_v5.xml", result.getName());
    }

    @Test
    public void testPreservesDirectory() {
        File result = MainFrame.applyVersionToFilename(
                new File("/some/path/module-a.xml"), 1, "", "");
        assertEquals("module-a_v1.xml", result.getName());
        assertTrue(result.getPath().replace('\\', '/').contains("/some/path/"));
    }

    @Test
    public void testMultiDotFilename() {
        File result = MainFrame.applyVersionToFilename(new File("my.ska.config.xml"), 10, "", "");
        assertEquals("my.ska.config_v10.xml", result.getName());
    }

    @Test
    public void testExistingVersionMultiDigit() {
        File result = MainFrame.applyVersionToFilename(new File("ska_v123.xml"), 124, "", "");
        assertEquals("ska_v124.xml", result.getName());
    }

    @Test
    public void testVersionZero() {
        File result = MainFrame.applyVersionToFilename(new File("config.xml"), 0, "", "");
        assertEquals("config_v0.xml", result.getName());
    }

    // --- Environment name tests ---

    @Test
    public void testWithEnvironmentName() {
        File result = MainFrame.applyVersionToFilename(new File("ska.xml"), 3, "Prod", "");
        assertEquals("ska_Prod_v3.xml", result.getName());
    }

    @Test
    public void testReplacesEnvironmentName() {
        File result = MainFrame.applyVersionToFilename(
                new File("ska_Prod_v2.xml"), 3, "Integration", "Prod");
        assertEquals("ska_Integration_v3.xml", result.getName());
    }

    @Test
    public void testRemovesEnvironmentWhenEmpty() {
        File result = MainFrame.applyVersionToFilename(
                new File("ska_UAT_v5.xml"), 6, "", "UAT");
        assertEquals("ska_v6.xml", result.getName());
    }

    @Test
    public void testSameEnvironmentNewVersion() {
        File result = MainFrame.applyVersionToFilename(
                new File("ska_Prod_v1.xml"), 2, "Prod", "Prod");
        assertEquals("ska_Prod_v2.xml", result.getName());
    }
}
