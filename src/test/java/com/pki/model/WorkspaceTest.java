package com.pki.model;

import com.pki.io.SkaXmlReader;
import com.pki.io.SkaXmlWriter;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for multi-SKA folder mode (Phase 10):
 * SkaWorkspace, SkaConfigEntry, master user pool, shared-user sync.
 */
public class WorkspaceTest {

    /**
     * Load 2 XMLs into a workspace, verify master pool merging,
     * modify a shared user cert, sync + save both, re-read and verify.
     */
    @Test
    public void testFolderModeSharedUsers() throws Exception {
        SkaXmlReader reader = new SkaXmlReader();
        SkaXmlWriter writer = new SkaXmlWriter();

        // --- Setup: create two temp XML files from the example ---
        SkaConfig configA = reader.read(new File("example/ska.xml"));
        configA.setModuleName("MODULE-A");
        configA.setVersion(1);

        SkaConfig configB = reader.read(new File("example/ska.xml"));
        configB.setModuleName("MODULE-B");
        configB.setVersion(2);
        // Remove some users from B to make the sets differ
        // Keep first 5 users in B (A has all 9)
        while (configB.getUsers().size() > 5) {
            configB.getUsers().remove(configB.getUsers().size() - 1);
        }

        Path tempDir = Files.createTempDirectory("ska-workspace-test-");
        File fileA = tempDir.resolve("module-a.xml").toFile();
        File fileB = tempDir.resolve("module-b.xml").toFile();
        writer.write(configA, fileA);
        writer.write(configB, fileB);

        // --- Load into workspace ---
        SkaWorkspace ws = new SkaWorkspace();
        SkaConfig loadedA = reader.read(fileA);
        SkaConfig loadedB = reader.read(fileB);
        SkaConfigEntry entryA = new SkaConfigEntry(loadedA, fileA);
        SkaConfigEntry entryB = new SkaConfigEntry(loadedB, fileB);
        ws.addEntry(entryA);
        ws.addEntry(entryB);

        // Verify active entry defaults
        assertEquals(0, ws.getActiveIndex());
        assertSame(entryA, ws.getActiveEntry());

        // --- Rebuild master pool and verify merge ---
        ws.rebuildMasterUserPool();
        // A has 9 users, B has 5 (subset) → pool should be 9 (union by CN)
        assertEquals(9, ws.getMasterUserPool().size());

        // Verify CN sets per entry
        Set<String> cnsA = ws.getCnsForEntry(entryA);
        Set<String> cnsB = ws.getCnsForEntry(entryB);
        assertEquals(9, cnsA.size());
        assertEquals(5, cnsB.size());
        assertTrue("B's CNs should be a subset of A's", cnsA.containsAll(cnsB));

        // --- Modify a shared user in the master pool ---
        String sharedCn = loadedB.getUsers().get(0).getCn(); // first user, in both A and B
        assertTrue(cnsA.contains(sharedCn));
        assertTrue(cnsB.contains(sharedCn));

        User poolUser = ws.findUserByCn(sharedCn);
        assertNotNull(poolUser);
        String newCert = "-----BEGIN CERTIFICATE-----\nMODIFIED-CERT-DATA\n-----END CERTIFICATE-----";
        poolUser.setCertificate(newCert);

        // --- Sync both entries from pool and save ---
        ws.syncEntryUsersFromPool(entryA, cnsA);
        ws.syncEntryUsersFromPool(entryB, cnsB);
        writer.write(entryA.getConfig(), fileA);
        writer.write(entryB.getConfig(), fileB);

        // --- Re-read both and verify the modified cert propagated ---
        SkaConfig reloadedA = reader.read(fileA);
        SkaConfig reloadedB = reader.read(fileB);

        assertEquals("MODULE-A", reloadedA.getModuleName());
        assertEquals("MODULE-B", reloadedB.getModuleName());
        assertEquals(1, reloadedA.getVersion());
        assertEquals(2, reloadedB.getVersion());

        // A should still have 9 users, B should still have 5
        assertEquals(9, reloadedA.getUsers().size());
        assertEquals(5, reloadedB.getUsers().size());

        // Find the shared user in both and verify cert
        User userInA = reloadedA.getUsers().stream()
                .filter(u -> u.getCn().equals(sharedCn)).findFirst().orElse(null);
        User userInB = reloadedB.getUsers().stream()
                .filter(u -> u.getCn().equals(sharedCn)).findFirst().orElse(null);
        assertNotNull("Shared user should be in A", userInA);
        assertNotNull("Shared user should be in B", userInB);
        assertEquals(newCert, userInA.getCertificate().trim());
        assertEquals(newCert, userInB.getCertificate().trim());

        // --- Cleanup ---
        fileA.delete();
        fileB.delete();
        tempDir.toFile().delete();
    }

    /**
     * SkaConfigEntry display labels and dirty tracking.
     */
    @Test
    public void testEntryLabelsAndDirty() throws Exception {
        SkaConfig config = new SkaConfig();
        config.setModuleName("MOD-X");
        config.setVersion(3);

        SkaConfigEntry entry = new SkaConfigEntry(config, new File("test.xml"));
        assertEquals("MOD-X (test.xml)", entry.getDisplayLabel());
        assertEquals("MOD-X (test.xml)", entry.getDisplayLabelWithDirty());
        assertFalse(entry.isDirty());
        assertEquals(3, entry.getLoadedVersion());

        entry.setDirty(true);
        assertEquals("MOD-X (test.xml) *", entry.getDisplayLabelWithDirty());
        assertEquals("MOD-X (test.xml) *", entry.toString());
    }

    /**
     * Workspace with no entries, edge cases.
     */
    @Test
    public void testWorkspaceEdgeCases() {
        SkaWorkspace ws = new SkaWorkspace();
        assertTrue(ws.isEmpty());
        assertNull(ws.getActiveEntry());
        assertEquals(-1, ws.getActiveIndex());
        assertFalse(ws.hasAnyDirty());
        assertNull(ws.findUserByCn("nobody"));

        // rebuildMasterUserPool on empty workspace should not throw
        ws.rebuildMasterUserPool();
        assertTrue(ws.getMasterUserPool().isEmpty());

        // Add an entry
        SkaConfig cfg = new SkaConfig();
        cfg.setModuleName("TEST");
        SkaConfigEntry entry = new SkaConfigEntry(cfg, null);
        ws.addEntry(entry);
        assertFalse(ws.isEmpty());
        assertEquals(0, ws.getActiveIndex());
        assertSame(entry, ws.getActiveEntry());

        // Dirty tracking
        assertFalse(ws.hasAnyDirty());
        entry.setDirty(true);
        assertTrue(ws.hasAnyDirty());

        // Remove entry
        ws.removeEntry(entry);
        assertTrue(ws.isEmpty());
        assertNull(ws.getActiveEntry());

        // Clear
        ws.addEntry(entry);
        ws.clear();
        assertTrue(ws.isEmpty());
        assertTrue(ws.getMasterUserPool().isEmpty());
    }

    /**
     * Active index management with multiple entries.
     */
    @Test
    public void testActiveIndexSwitching() {
        SkaWorkspace ws = new SkaWorkspace();
        SkaConfigEntry e1 = new SkaConfigEntry(new SkaConfig(), new File("a.xml"));
        SkaConfigEntry e2 = new SkaConfigEntry(new SkaConfig(), new File("b.xml"));
        SkaConfigEntry e3 = new SkaConfigEntry(new SkaConfig(), new File("c.xml"));

        ws.addEntry(e1);
        ws.addEntry(e2);
        ws.addEntry(e3);
        assertEquals(0, ws.getActiveIndex());

        ws.setActiveIndex(2);
        assertSame(e3, ws.getActiveEntry());

        // Remove entry before active → active shifts back
        ws.removeEntry(e1);
        assertEquals(1, ws.getActiveIndex()); // was 2, e1 removed before it
        assertSame(e3, ws.getActiveEntry());

        // Remove active entry → goes to previous
        ws.setActiveIndex(1);
        ws.removeEntry(e3);
        assertEquals(0, ws.getActiveIndex());
        assertSame(e2, ws.getActiveEntry());

        // Remove last entry
        ws.removeEntry(e2);
        assertTrue(ws.isEmpty());
    }

    /**
     * Master pool merge: later occurrence fills blank fields.
     */
    @Test
    public void testMasterPoolMerge() {
        SkaWorkspace ws = new SkaWorkspace();

        SkaConfig cfgA = new SkaConfig();
        User u1a = new User();
        u1a.setCn("Alice CN");
        u1a.setEmail("alice@example.com");
        u1a.setCertificate("CERT-A");
        cfgA.getUsers().add(u1a);

        SkaConfig cfgB = new SkaConfig();
        User u1b = new User();
        u1b.setCn("Alice CN");
        u1b.setEmail(""); // blank — should not overwrite
        u1b.setOrganisation("OrgB"); // new info — should fill in
        u1b.setCertificate("CERT-B-IGNORED"); // not blank in target, so ignored by merge
        cfgB.getUsers().add(u1b);

        // Add a user only in B
        User u2 = new User();
        u2.setCn("Bob CN");
        u2.setEmail("bob@example.com");
        cfgB.getUsers().add(u2);

        ws.addEntry(new SkaConfigEntry(cfgA, new File("a.xml")));
        ws.addEntry(new SkaConfigEntry(cfgB, new File("b.xml")));
        ws.rebuildMasterUserPool();

        assertEquals(2, ws.getMasterUserPool().size());

        User alice = ws.findUserByCn("Alice CN");
        assertNotNull(alice);
        assertEquals("alice@example.com", alice.getEmail()); // from A, not overwritten
        assertEquals("OrgB", alice.getOrganisation()); // filled from B
        assertEquals("CERT-A", alice.getCertificate()); // from A, not overwritten (not blank)

        User bob = ws.findUserByCn("Bob CN");
        assertNotNull(bob);
        assertEquals("bob@example.com", bob.getEmail());
    }
}
