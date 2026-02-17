package com.pki.io;

import com.pki.model.*;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link ReportGenerator}: membership report and user/certificate report.
 */
public class ReportGeneratorTest {

    @Test
    public void testMembershipReportFromExampleXml() throws Exception {
        SkaXmlReader reader = new SkaXmlReader();
        SkaConfig config = reader.read(new File("example/ska.xml"));

        SkaConfigEntry entry = new SkaConfigEntry(config, new File("example/ska.xml"));
        List<SkaConfigEntry> entries = List.of(entry);

        File tmpDir = Files.createTempDirectory("report-test").toFile();
        tmpDir.deleteOnExit();

        ReportGenerator gen = new ReportGenerator();
        ReportGenerator.ReportResult result = gen.generate(entries, config.getUsers(), tmpDir);

        // Membership report should exist and have content
        assertTrue("Membership file should exist", result.membershipFile.exists());
        assertTrue("Should have membership rows", result.membershipRows > 0);

        // Read and verify header
        try (BufferedReader br = new BufferedReader(new FileReader(result.membershipFile))) {
            String header = br.readLine();
            assertNotNull("Header should not be null", header);
            assertTrue("Header should contain SKA Module", header.contains("SKA Module"));
            assertTrue("Header should contain Operation", header.contains("Operation"));
            assertTrue("Header should contain EC Curve", header.contains("EC Curve"));
            assertTrue("Header should contain Group Name", header.contains("Group Name"));

            // Verify we have data rows for all 4 sections (org, skaplus, skamodify, keys)
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            assertFalse("Should have data rows", lines.isEmpty());

            // Check that all sections appear
            String allContent = String.join("\n", lines);
            assertTrue("Should have Organization section", allContent.contains("Organization"));
            assertTrue("Should have SKA Plus section", allContent.contains("SKA Plus"));
            assertTrue("Should have SKA Modify section", allContent.contains("SKA Modify"));
            assertTrue("Should have Keys section", allContent.contains("Keys"));
        }

        // Clean up
        new File(tmpDir, "report_memberships.csv").delete();
        new File(tmpDir, "report_users.csv").delete();
        tmpDir.delete();
    }

    @Test
    public void testUserReportFromExampleXml() throws Exception {
        SkaXmlReader reader = new SkaXmlReader();
        SkaConfig config = reader.read(new File("example/ska.xml"));

        SkaConfigEntry entry = new SkaConfigEntry(config, new File("example/ska.xml"));

        File tmpDir = Files.createTempDirectory("report-test-users").toFile();
        tmpDir.deleteOnExit();

        ReportGenerator gen = new ReportGenerator();
        ReportGenerator.ReportResult result = gen.generate(List.of(entry), config.getUsers(), tmpDir);

        // User report should exist
        assertTrue("User file should exist", result.userFile.exists());
        assertEquals("Should have all users", config.getUsers().size(), result.userRows);

        // Read and verify header
        try (BufferedReader br = new BufferedReader(new FileReader(result.userFile))) {
            String header = br.readLine();
            assertNotNull(header);
            assertTrue("Header should contain CN", header.contains("CN"));
            assertTrue("Header should contain Key Usage", header.contains("Key Usage"));
            assertTrue("Header should contain Not Before", header.contains("Not Before"));
            assertTrue("Header should contain SHA-256", header.contains("SHA-256"));
        }

        // Clean up
        new File(tmpDir, "report_memberships.csv").delete();
        new File(tmpDir, "report_users.csv").delete();
        tmpDir.delete();
    }

    @Test
    public void testReportWithEmptyBoundaries() throws Exception {
        // Build a minimal config with an empty operation (no boundaries)
        SkaConfig config = new SkaConfig();
        config.setModuleName("EmptyTest");
        config.setVersion(1);

        SkaConfigEntry entry = new SkaConfigEntry(config, new File("empty.xml"));

        File tmpDir = Files.createTempDirectory("report-test-empty").toFile();
        tmpDir.deleteOnExit();

        ReportGenerator gen = new ReportGenerator();
        ReportGenerator.ReportResult result = gen.generate(
                List.of(entry), List.of(), tmpDir);

        // Should produce "no boundaries" rows without crashing
        assertTrue("Should have rows even for empty config", result.membershipRows > 0);
        assertEquals("Should have 0 users", 0, result.userRows);

        // Clean up
        new File(tmpDir, "report_memberships.csv").delete();
        new File(tmpDir, "report_users.csv").delete();
        tmpDir.delete();
    }

    @Test
    public void testMultiEntryReport() throws Exception {
        // Two minimal configs
        SkaConfig cfg1 = new SkaConfig();
        cfg1.setModuleName("Module-A");
        SkaConfig cfg2 = new SkaConfig();
        cfg2.setModuleName("Module-B");

        // Add a group with members to cfg1
        Group g = new Group();
        g.setName("TestGroup");
        g.setQuorum(2);
        g.getMemberCns().add("Alice A ABC123");
        g.getMemberCns().add("Bob B DEF456");
        Boundary b = new Boundary();
        b.getGroups().add(g);
        cfg1.getOrganization().getOperations().getUse().getBoundaries().add(b);

        List<SkaConfigEntry> entries = List.of(
                new SkaConfigEntry(cfg1, new File("a.xml")),
                new SkaConfigEntry(cfg2, new File("b.xml"))
        );

        // Users
        User alice = new User();
        alice.setCn("Alice A ABC123");
        alice.setName("Alice Anderson");

        File tmpDir = Files.createTempDirectory("report-test-multi").toFile();
        tmpDir.deleteOnExit();

        ReportGenerator gen = new ReportGenerator();
        ReportGenerator.ReportResult result = gen.generate(entries, List.of(alice), tmpDir);

        assertTrue(result.membershipRows > 0);
        assertEquals(1, result.userRows);

        // Verify both modules appear
        String content = Files.readString(result.membershipFile.toPath());
        assertTrue("Module-A should appear", content.contains("Module-A"));
        assertTrue("Module-B should appear", content.contains("Module-B"));

        // Verify user name resolution
        assertTrue("Alice name should be resolved", content.contains("Alice Anderson"));

        // Clean up
        new File(tmpDir, "report_memberships.csv").delete();
        new File(tmpDir, "report_users.csv").delete();
        tmpDir.delete();
    }
}
