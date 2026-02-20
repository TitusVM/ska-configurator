package com.pki.io;

import com.pki.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an SKA configuration XML file into the in-memory model.
 * Uses DOM parsing (no external dependencies).
 */
public class SkaXmlReader {

    /**
     * Parse an SKA XML file and return the populated model.
     *
     * @param file the XML file to read
     * @return populated SkaConfig
     * @throws Exception if parsing fails
     */
    public SkaConfig read(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Security: disable external entities
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        SkaConfig config = new SkaConfig();
        config.setModuleName(attr(root, "moduleName"));
        config.setVersion(intAttr(root, "version", 1));

        // XSD schema location (xsi:noNamespaceSchemaLocation)
        String schemaLoc = root.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "noNamespaceSchemaLocation");
        if (schemaLoc == null || schemaLoc.isEmpty()) {
            schemaLoc = attr(root, "xsi:noNamespaceSchemaLocation");
        }
        config.setXsiNoNamespaceSchemaLocation(schemaLoc != null ? schemaLoc : "");

        // Organization
        Element orgEl = firstChild(root, "organization");
        if (orgEl != null) {
            config.setOrganization(readSection(orgEl));
        }

        // SKA Plus
        Element plusEl = firstChild(root, "skaplus");
        if (plusEl != null) {
            config.setSkaPlus(readSection(plusEl));
        }

        // SKA Modify
        Element modEl = firstChild(root, "skamodify");
        if (modEl != null) {
            config.setSkaModify(readSection(modEl));
        }

        // Keys > child (e.g. "proto", but can be any name)
        Element keysEl = firstChild(root, "keys");
        if (keysEl != null) {
            // Read the first element child of <keys>, whatever its tag name
            Element childEl = firstElementChild(keysEl);
            if (childEl != null) {
                KeysProto kp = readKeysProto(childEl);
                kp.setChildName(childEl.getTagName());
                config.setKeysProto(kp);
            }

            // <personalization useKek="true" kekLabel="..."> inside <keys>
            Element persoEl = firstChild(keysEl, "personalization");
            if (persoEl != null) {
                config.setPersonalization(readPersonalization(persoEl));
            }
        }

        // Users
        Element usersEl = firstChild(root, "users");
        if (usersEl != null) {
            config.setUsers(readUsers(usersEl));
        }

        return config;
    }

    // --- Section parsing ---

    private SkaSection readSection(Element el) {
        SkaSection section = new SkaSection();
        section.setBlockedOnInitialize(Boolean.parseBoolean(attr(el, "blockedOnInitialize")));
        section.setKeyLabel(attr(el, "keyLabel"));
        section.setStartValidity(attr(el, "startValidity"));
        section.setEndValidity(attr(el, "endValidity"));
        section.setEcParameters(readEcParameters(el));
        Element opsEl = firstChild(el, "operations");
        if (opsEl != null) {
            section.setOperations(readOperations(opsEl));
        }
        return section;
    }

    private KeysProto readKeysProto(Element el) {
        KeysProto kp = new KeysProto();
        kp.setEcParameters(readEcParameters(el));
        Element opsEl = firstChild(el, "operations");
        if (opsEl != null) {
            kp.setOperations(readOperations(opsEl));
        }
        return kp;
    }

    private Personalization readPersonalization(Element el) {
        Personalization p = new Personalization();
        p.setEnabled(true); // tag is present → enabled
        p.setUseKek(Boolean.parseBoolean(attr(el, "useKek")));
        p.setKekLabel(attr(el, "kekLabel"));
        p.setEcParameters(readEcParameters(el));
        return p;
    }

    // --- EC Parameters ---

    private EcParameters readEcParameters(Element parent) {
        EcParameters ec = new EcParameters();
        Element ecEl = firstChild(parent, "ecParameters");
        if (ecEl != null) {
            ec.setCurveName(attr(ecEl, "curveName"));
            ec.setPemText(ecEl.getTextContent().trim());
        }
        return ec;
    }

    // --- Operations ---

    private Operations readOperations(Element opsEl) {
        Operations ops = new Operations();
        Element useEl = firstChild(opsEl, "use");
        if (useEl != null) ops.setUse(readOperation(useEl));
        Element modifyEl = firstChild(opsEl, "modify");
        if (modifyEl != null) ops.setModify(readOperation(modifyEl));
        Element blockEl = firstChild(opsEl, "block");
        if (blockEl != null) ops.setBlock(readOperation(blockEl));
        Element unblockEl = firstChild(opsEl, "unblock");
        if (unblockEl != null) ops.setUnblock(readOperation(unblockEl));
        return ops;
    }

    private Operation readOperation(Element opEl) {
        Operation op = new Operation();
        op.setDelayMillis(longAttr(opEl, "delayMillis", 0));
        op.setTimeLimitMillis(longAttr(opEl, "timeLimitMillis", 0));
        op.setBoundaries(readBoundaries(opEl));
        return op;
    }

    // --- Boundaries & Groups ---

    private List<Boundary> readBoundaries(Element parent) {
        List<Boundary> list = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && "boundary".equals(el.getTagName())) {
                list.add(readBoundary(el));
            }
        }
        return list;
    }

    private Boundary readBoundary(Element bEl) {
        Boundary boundary = new Boundary();
        List<Group> groups = new ArrayList<>();
        NodeList nodes = bEl.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && "group".equals(el.getTagName())) {
                groups.add(readGroup(el));
            }
        }
        boundary.setGroups(groups);
        return boundary;
    }

    private Group readGroup(Element gEl) {
        Group group = new Group();
        group.setQuorum(intAttr(gEl, "quorum", 0));
        group.setName(attr(gEl, "name"));

        // Members
        Element membersEl = firstChild(gEl, "members");
        if (membersEl != null) {
            List<String> cns = new ArrayList<>();
            NodeList mcns = membersEl.getElementsByTagName("membercn");
            for (int i = 0; i < mcns.getLength(); i++) {
                cns.add(mcns.item(i).getTextContent().trim());
            }
            group.setMemberCns(cns);
        }

        // Keys
        Element keysEl = firstChild(gEl, "keys");
        if (keysEl != null) {
            List<String> labels = new ArrayList<>();
            NodeList kls = keysEl.getElementsByTagName("keylabel");
            for (int i = 0; i < kls.getLength(); i++) {
                labels.add(kls.item(i).getTextContent().trim());
            }
            group.setKeyLabels(labels);
        }

        return group;
    }

    // --- Users ---

    private List<User> readUsers(Element usersEl) {
        List<User> users = new ArrayList<>();
        NodeList nodes = usersEl.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && "user".equals(el.getTagName())) {
                users.add(readUser(el));
            }
        }
        return users;
    }

    private User readUser(Element uEl) {
        User user = new User();
        user.setEmail(attr(uEl, "email"));
        user.setUserId(attr(uEl, "userId"));
        user.setCn(attr(uEl, "cn"));
        user.setName(attr(uEl, "name"));
        user.setOrganisation(attr(uEl, "organisation"));
        Element certEl = firstChild(uEl, "cert");
        if (certEl != null) {
            user.setCertificate(certEl.getTextContent().trim());
        }
        return user;
    }

    // --- DOM helpers ---

    private Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && tagName.equals(el.getTagName())) {
                return el;
            }
        }
        return null;
    }

    /** Returns the first child element regardless of tag name. */
    private Element firstElementChild(Element parent) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el) {
                return el;
            }
        }
        return null;
    }

    private String attr(Element el, String name) {
        String val = el.getAttribute(name);
        return val != null ? val : "";
    }

    private int intAttr(Element el, String name, int defaultValue) {
        String val = el.getAttribute(name);
        if (val == null || val.isEmpty()) return defaultValue;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultValue; }
    }

    private long longAttr(Element el, String name, long defaultValue) {
        String val = el.getAttribute(name);
        if (val == null || val.isEmpty()) return defaultValue;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return defaultValue; }
    }
}
