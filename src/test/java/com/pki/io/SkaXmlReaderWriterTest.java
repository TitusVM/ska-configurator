package com.pki.io;

import com.pki.model.*;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class SkaXmlReaderWriterTest {

    @Test
    public void testReadExampleXml() throws Exception {
        SkaXmlReader reader = new SkaXmlReader();
        File xmlFile = new File("example/ska.xml");
        assertTrue("Example XML must exist", xmlFile.exists());

        SkaConfig config = reader.read(xmlFile);

        // Root attributes
        assertEquals("proto", config.getModuleName());
        assertEquals(1, config.getVersion());
        assertEquals("skaconfig.xsd", config.getXsiNoNamespaceSchemaLocation());

        // Organization section
        SkaSection org = config.getOrganization();
        assertFalse(org.isBlockedOnInitialize());
        assertEquals("PROTO_ORG_KEY_00000", org.getKeyLabel());
        assertEquals("2023-08-03", org.getStartValidity());
        assertEquals("2026-08-02", org.getEndValidity());
        assertTrue(org.getEcParameters().getPemText().contains("BEGIN EC PARAMETERS"));

        // Organization use operation: 1 boundary with 3 groups
        Operation useOp = org.getOperations().getUse();
        assertEquals(0, useOp.getDelayMillis());
        assertEquals(1, useOp.getBoundaries().size());
        assertEquals(3, useOp.getBoundaries().get(0).getGroups().size());

        Group owners = useOp.getBoundaries().get(0).getGroups().get(0);
        assertEquals("Owners", owners.getName());
        assertEquals(1, owners.getQuorum());
        assertEquals(3, owners.getMemberCns().size());
        assertEquals("Person One ABCDEF", owners.getMemberCns().get(0));

        // Modify operation: 2 boundaries (key-based + member-based)
        Operation modOp = org.getOperations().getModify();
        assertEquals(2, modOp.getBoundaries().size());
        Group skaModGroup = modOp.getBoundaries().get(0).getGroups().get(0);
        assertEquals("SKA Modify", skaModGroup.getName());
        assertEquals(1, skaModGroup.getKeyLabels().size());
        assertEquals("MODIFY_KEY_00000", skaModGroup.getKeyLabels().get(0));

        // SkaModify section - use has quorum=0 Unprotected
        SkaSection skaMod = config.getSkaModify();
        Operation skaModUse = skaMod.getOperations().getUse();
        assertEquals(1, skaModUse.getBoundaries().size());
        Group unprotected = skaModUse.getBoundaries().get(0).getGroups().get(0);
        assertEquals("Unprotected", unprotected.getName());
        assertEquals(0, unprotected.getQuorum());

        // SkaModify ecParameters has curveName
        assertEquals("brainpoolXYZ", skaMod.getEcParameters().getCurveName());

        // Keys > Proto
        KeysProto kp = config.getKeysProto();
        assertNotNull(kp);
        assertEquals(1, kp.getOperations().getUse().getBoundaries().size());
        assertTrue(kp.getEcParameters().getPemText().contains("BEGIN EC PARAMETERS"));

        // Users
        List<User> users = config.getUsers();
        assertEquals(9, users.size());
        User first = users.get(0);
        assertEquals("Person One ABCDEF", first.getCn());
        assertEquals("person.one@admin.ch", first.getEmail());
        assertEquals("12345678", first.getUserId());
        assertEquals("fedpol", first.getOrganisation());
        assertTrue(first.getCertificate().contains("BEGIN CERTIFICATE"));
    }

    @Test
    public void testRoundTrip() throws Exception {
        // Read the example
        SkaXmlReader reader = new SkaXmlReader();
        SkaConfig config = reader.read(new File("example/ska.xml"));

        // Write it out
        File tempFile = File.createTempFile("ska-test-", ".xml");
        tempFile.deleteOnExit();
        SkaXmlWriter writer = new SkaXmlWriter();
        writer.write(config, tempFile);

        // Verify XML declaration does not contain standalone
        String xmlContent = Files.readString(tempFile.toPath());
        assertFalse("XML should not contain standalone attribute",
                xmlContent.contains("standalone"));

        // Read it back
        SkaConfig roundTripped = reader.read(tempFile);

        // Verify key data survived the round trip
        assertEquals(config.getModuleName(), roundTripped.getModuleName());
        assertEquals(config.getVersion(), roundTripped.getVersion());
        assertEquals(config.getUsers().size(), roundTripped.getUsers().size());

        // Check organization section preserved
        SkaSection orgOrig = config.getOrganization();
        SkaSection orgRT = roundTripped.getOrganization();
        assertEquals(orgOrig.getKeyLabel(), orgRT.getKeyLabel());
        assertEquals(orgOrig.getStartValidity(), orgRT.getStartValidity());
        assertEquals(orgOrig.getEndValidity(), orgRT.getEndValidity());

        // Check use operation groups preserved
        List<Group> origGroups = orgOrig.getOperations().getUse().getBoundaries().get(0).getGroups();
        List<Group> rtGroups = orgRT.getOperations().getUse().getBoundaries().get(0).getGroups();
        assertEquals(origGroups.size(), rtGroups.size());
        for (int i = 0; i < origGroups.size(); i++) {
            assertEquals(origGroups.get(i).getName(), rtGroups.get(i).getName());
            assertEquals(origGroups.get(i).getQuorum(), rtGroups.get(i).getQuorum());
            List<String> origMembers = new java.util.ArrayList<>(origGroups.get(i).getMemberCns());
            List<String> rtMembers = new java.util.ArrayList<>(rtGroups.get(i).getMemberCns());
            java.util.Collections.sort(origMembers);
            java.util.Collections.sort(rtMembers);
            assertEquals(origMembers, rtMembers);
        }

        // Check user certificates survived CDATA round-trip
        // Users may be in a different order after round-trip (sorted on write)
        for (User origUser : config.getUsers()) {
            User rtUser = roundTripped.getUsers().stream()
                    .filter(u -> u.getCn().equals(origUser.getCn()))
                    .findFirst().orElse(null);
            assertNotNull("User " + origUser.getCn() + " should survive round-trip", rtUser);
            assertEquals(origUser.getEmail(), rtUser.getEmail());
            assertEquals(origUser.getCertificate().trim(), rtUser.getCertificate().trim());
        }
    }
}
