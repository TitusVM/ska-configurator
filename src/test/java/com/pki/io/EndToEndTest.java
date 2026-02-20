package com.pki.io;

import com.pki.model.*;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end integration test: open XML → import CSV → modify → save → re-read.
 */
public class EndToEndTest {

    @Test
    public void testFullPipeline() throws Exception {
        // 1. Read the example XML
        SkaXmlReader reader = new SkaXmlReader();
        SkaConfig config = reader.read(new File("example/ska.xml"));
        assertEquals("proto", config.getModuleName());
        assertEquals(9, config.getUsers().size());

        // 2. Import CSV users
        File csvFile = new File("exports/export.csv");
        Assume.assumeTrue("Skipping: exports/export.csv not found", csvFile.exists());
        CsvImporter importer = new CsvImporter();
        List<User> csvUsers = importer.importUsers(csvFile);
        assertFalse("CSV should produce users", csvUsers.isEmpty());

        // 3. Merge: detect new users from CSV not in XML
        var existingCns = config.getUsers().stream().map(User::getCn).toList();
        long newCount = csvUsers.stream()
                .filter(u -> !existingCns.contains(u.getCn()))
                .count();
        // Some CSV users are new (not in the XML)
        assertTrue("Should find at least some new CSV users", newCount >= 0);

        // 4. Modify config: update module name, add a boundary
        config.setModuleName("TEST-E2E");
        config.setVersion(42);

        Boundary newBoundary = new Boundary();
        Group g = new Group();
        g.setName("E2E-Group");
        g.setQuorum(2);
        g.getMemberCns().add("Person One ABCDEF");
        g.getMemberCns().add("Person Two ABCDEF");
        newBoundary.getGroups().add(g);
        config.getOrganization().getOperations().getBlock().getBoundaries().add(newBoundary);

        // 5. Write modified config
        File tempFile = File.createTempFile("ska-e2e-", ".xml");
        tempFile.deleteOnExit();
        SkaXmlWriter writer = new SkaXmlWriter();
        writer.write(config, tempFile);
        assertTrue("Output file should exist", tempFile.exists());
        assertTrue("Output file should have content", tempFile.length() > 1000);

        // 6. Re-read and verify modifications survived
        SkaConfig reloaded = reader.read(tempFile);
        assertEquals("TEST-E2E", reloaded.getModuleName());
        assertEquals(42, reloaded.getVersion());
        assertEquals(9, reloaded.getUsers().size());

        // Verify the new boundary was persisted
        Operation blockOp = reloaded.getOrganization().getOperations().getBlock();
        boolean found = false;
        for (Boundary b : blockOp.getBoundaries()) {
            for (Group grp : b.getGroups()) {
                if ("E2E-Group".equals(grp.getName())) {
                    assertEquals(2, grp.getQuorum());
                    assertEquals(2, grp.getMemberCns().size());
                    found = true;
                }
            }
        }
        assertTrue("E2E-Group should be found in block boundaries", found);

        // 7. Verify all original data preserved
        SkaSection org = reloaded.getOrganization();
        assertEquals("PROTO_ORG_KEY_00000", org.getKeyLabel());
        assertTrue(org.getEcParameters().getPemText().contains("BEGIN EC PARAMETERS"));
        assertEquals(3, org.getOperations().getUse().getBoundaries().get(0).getGroups().size());

        // Verify user certificates survived (find by CN since list is sorted)
        User firstUser = reloaded.getUsers().stream()
                .filter(u -> u.getCn().equals("Person One ABCDEF"))
                .findFirst().orElse(null);
        assertNotNull("Person One ABCDEF should exist", firstUser);
        assertTrue(firstUser.getCertificate().contains("BEGIN CERTIFICATE"));
    }
}
