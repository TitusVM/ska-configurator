package com.pki.io;

import com.pki.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.List;

/**
 * Writes an SkaConfig model to a well-formatted SKA XML file.
 * Produces human-readable, properly indented output.
 * Certificates are wrapped in CDATA sections.
 */
public class SkaXmlWriter {

    /**
     * Serialize the model to an XML file.
     *
     * @param config the model to serialize
     * @param file   the output file
     * @throws Exception if writing fails
     */
    public void write(SkaConfig config, File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        doc.setXmlStandalone(true);

        // Root: <skaconfig>
        Element root = doc.createElement("skaconfig");
        root.setAttribute("moduleName", config.getModuleName());
        root.setAttribute("version", String.valueOf(config.getVersion()));
        doc.appendChild(root);

        // <organization>
        writeSection(doc, root, "organization", config.getOrganization());

        // <skaplus>
        writeSection(doc, root, "skaplus", config.getSkaPlus());

        // <skamodify>
        writeSection(doc, root, "skamodify", config.getSkaModify());

        // <keys><proto>
        writeKeysProto(doc, root, config.getKeysProto());

        // <users>
        writeUsers(doc, root, config.getUsers());

        // Write to file with indentation
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        transformer.transform(new DOMSource(doc), new StreamResult(file));
    }

    // --- Section writing ---

    private void writeSection(Document doc, Element parent, String tagName, SkaSection section) {
        Element el = doc.createElement(tagName);
        el.setAttribute("blockedOnInitialize", String.valueOf(section.isBlockedOnInitialize()));
        el.setAttribute("keyLabel", section.getKeyLabel());
        el.setAttribute("startValidity", section.getStartValidity());
        el.setAttribute("endValidity", section.getEndValidity());
        parent.appendChild(el);

        writeEcParameters(doc, el, section.getEcParameters());
        writeOperations(doc, el, section.getOperations());
    }

    private void writeKeysProto(Document doc, Element parent, KeysProto keysProto) {
        String childTag = keysProto.getChildName();
        if (childTag == null || childTag.isEmpty()) return; // no keys block if no child name set
        Element keysEl = doc.createElement("keys");
        parent.appendChild(keysEl);
        Element childEl = doc.createElement(childTag);
        keysEl.appendChild(childEl);

        writeOperations(doc, childEl, keysProto.getOperations());
        writeEcParameters(doc, childEl, keysProto.getEcParameters());
    }

    // --- EC Parameters ---

    private void writeEcParameters(Document doc, Element parent, EcParameters ec) {
        Element ecEl = doc.createElement("ecParameters");
        if (ec.getCurveName() != null && !ec.getCurveName().isEmpty()) {
            ecEl.setAttribute("curveName", ec.getCurveName());
        }
        if (ec.getPemText() != null && !ec.getPemText().isEmpty()) {
            ecEl.setTextContent(ec.getPemText());
        }
        parent.appendChild(ecEl);
    }

    // --- Operations ---

    private void writeOperations(Document doc, Element parent, Operations ops) {
        Element opsEl = doc.createElement("operations");
        parent.appendChild(opsEl);

        writeOperation(doc, opsEl, "use", ops.getUse());
        writeOperation(doc, opsEl, "modify", ops.getModify());
        writeOperation(doc, opsEl, "block", ops.getBlock());
        writeOperation(doc, opsEl, "unblock", ops.getUnblock());
    }

    private void writeOperation(Document doc, Element parent, String tagName, Operation op) {
        Element opEl = doc.createElement(tagName);
        opEl.setAttribute("delayMillis", String.valueOf(op.getDelayMillis()));
        opEl.setAttribute("timeLimitMillis", String.valueOf(op.getTimeLimitMillis()));
        parent.appendChild(opEl);

        for (Boundary boundary : op.getBoundaries()) {
            writeBoundary(doc, opEl, boundary);
        }
    }

    // --- Boundaries & Groups ---

    private void writeBoundary(Document doc, Element parent, Boundary boundary) {
        Element bEl = doc.createElement("boundary");
        parent.appendChild(bEl);

        for (Group group : boundary.getGroups()) {
            writeGroup(doc, bEl, group);
        }
    }

    private void writeGroup(Document doc, Element parent, Group group) {
        Element gEl = doc.createElement("group");
        gEl.setAttribute("quorum", String.valueOf(group.getQuorum()));
        gEl.setAttribute("name", group.getName());
        parent.appendChild(gEl);

        // Members
        if (!group.getMemberCns().isEmpty()) {
            Element membersEl = doc.createElement("members");
            gEl.appendChild(membersEl);
            for (String cn : group.getMemberCns()) {
                Element mcn = doc.createElement("membercn");
                mcn.setTextContent(cn);
                membersEl.appendChild(mcn);
            }
        }

        // Keys
        if (!group.getKeyLabels().isEmpty()) {
            Element keysEl = doc.createElement("keys");
            gEl.appendChild(keysEl);
            for (String label : group.getKeyLabels()) {
                Element kl = doc.createElement("keylabel");
                kl.setTextContent(label);
                keysEl.appendChild(kl);
            }
        }
    }

    // --- Users ---

    private void writeUsers(Document doc, Element parent, List<User> users) {
        Element usersEl = doc.createElement("users");
        parent.appendChild(usersEl);

        for (User user : users) {
            writeUser(doc, usersEl, user);
        }
    }

    private void writeUser(Document doc, Element parent, User user) {
        Element uEl = doc.createElement("user");
        uEl.setAttribute("email", user.getEmail());
        uEl.setAttribute("userId", user.getUserId());
        uEl.setAttribute("cn", user.getCn());
        uEl.setAttribute("name", user.getName());
        uEl.setAttribute("organisation", user.getOrganisation());
        parent.appendChild(uEl);

        if (user.getCertificate() != null && !user.getCertificate().isEmpty()) {
            Element certEl = doc.createElement("cert");
            CDATASection cdata = doc.createCDATASection("\n" + user.getCertificate() + "\n");
            certEl.appendChild(cdata);
            uEl.appendChild(certEl);
        }
    }
}
