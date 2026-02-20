package com.pki.io;

import com.pki.model.User;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CsvImporterTest {

    @Test
    public void testImportExportCsv() throws Exception {
        CsvImporter importer = new CsvImporter();
        File csvFile = new File("exports/export.csv");
        Assume.assumeTrue("Skipping: exports/export.csv not found", csvFile.exists());

        List<User> users = importer.importUsers(csvFile);

        // Should have deduplicated users (8 rows, some share the same CN)
        assertFalse("Should import at least one user", users.isEmpty());

        // Check that all users have a non-empty CN
        for (User u : users) {
            assertFalse("CN must not be empty", u.getCn().isEmpty());
        }

        // Find Baesler Boris (appears in two rows: EDOC-46 and EDOC-51)
        User baesler = users.stream()
                .filter(u -> u.getCn().equals("Baesler Boris KJBDG0"))
                .findFirst().orElse(null);
        assertNotNull("Baesler Boris should be present", baesler);
        assertEquals("boris.baesler@fedpol.admin.ch", baesler.getEmail());
        assertEquals("83022099", baesler.getUserId());
        assertFalse("Should have a certificate", baesler.getCertificate().isEmpty());
        assertTrue("Cert should start with PEM header",
                baesler.getCertificate().startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue("Cert should end with PEM footer",
                baesler.getCertificate().endsWith("-----END CERTIFICATE-----"));

        // Baesler has Org Owner role for two modules
        assertTrue("Should have Org Owner roles", baesler.getOrgOwnerOf().size() >= 1);

        // Find Haesler Andreas (cert is in wrong column in EDOC-48)
        User haesler = users.stream()
                .filter(u -> u.getCn().equals("Haesler Andreas 0TJBES"))
                .findFirst().orElse(null);
        assertNotNull("Haesler Andreas should be present", haesler);
        assertFalse("Haesler should have a cert (found in wrong column)",
                haesler.getCertificate().isEmpty());
    }

    @Test
    public void testCleanCertificate() {
        String raw = "  -----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----\n\n&nbsp;  ";
        String cleaned = CsvImporter.cleanCertificate(raw);
        assertEquals("-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----", cleaned);
    }

    @Test
    public void testCleanCertificateEmpty() {
        assertEquals("", CsvImporter.cleanCertificate(""));
        assertEquals("", CsvImporter.cleanCertificate(null));
        assertEquals("", CsvImporter.cleanCertificate("just some text"));
    }

    @Test
    public void testParseMultiValue() {
        Set<String> result = CsvImporter.parseMultiValue("CVCA PP (Prod)||CVCA PP (PreProd)");
        assertEquals(2, result.size());
        assertTrue(result.contains("CVCA PP (Prod)"));
        assertTrue(result.contains("CVCA PP (PreProd)"));
    }

    @Test
    public void testParseMultiValueEmpty() {
        assertTrue(CsvImporter.parseMultiValue("").isEmpty());
        assertTrue(CsvImporter.parseMultiValue(null).isEmpty());
    }
}
