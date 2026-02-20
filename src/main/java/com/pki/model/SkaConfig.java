package com.pki.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root model representing an entire SKA configuration file.
 * Maps to the {@code <skaconfig>} XML element.
 */
public class SkaConfig {

    private String moduleName = "";
    private int version = 1;
    private String xsiNoNamespaceSchemaLocation = "";
    private boolean integrationEnvironment = false;
    private SkaSection organization = new SkaSection();
    private SkaSection skaPlus = new SkaSection();
    private SkaSection skaModify = new SkaSection();
    private KeysProto keysProto = new KeysProto();
    private Personalization personalization = new Personalization();
    private List<User> users = new ArrayList<>();

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getXsiNoNamespaceSchemaLocation() { return xsiNoNamespaceSchemaLocation; }
    public void setXsiNoNamespaceSchemaLocation(String loc) { this.xsiNoNamespaceSchemaLocation = loc != null ? loc : ""; }

    public boolean isIntegrationEnvironment() { return integrationEnvironment; }
    public void setIntegrationEnvironment(boolean integrationEnvironment) { this.integrationEnvironment = integrationEnvironment; }

    public SkaSection getOrganization() { return organization; }
    public void setOrganization(SkaSection organization) { this.organization = organization; }

    public SkaSection getSkaPlus() { return skaPlus; }
    public void setSkaPlus(SkaSection skaPlus) { this.skaPlus = skaPlus; }

    public SkaSection getSkaModify() { return skaModify; }
    public void setSkaModify(SkaSection skaModify) { this.skaModify = skaModify; }

    public KeysProto getKeysProto() { return keysProto; }
    public void setKeysProto(KeysProto keysProto) { this.keysProto = keysProto; }

    public Personalization getPersonalization() { return personalization; }
    public void setPersonalization(Personalization personalization) { this.personalization = personalization; }

    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
}
