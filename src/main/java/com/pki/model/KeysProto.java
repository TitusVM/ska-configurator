package com.pki.model;

/**
 * Model for the {@code <keys><name>} section.
 * Contains operations and EC parameters but no key label or validity metadata.
 * The child element name (e.g. "proto") is dynamic and stored in {@code childName}.
 */
public class KeysProto {

    private String childName = "";
    private Operations operations = new Operations();
    private EcParameters ecParameters = new EcParameters();

    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }

    public Operations getOperations() { return operations; }
    public void setOperations(Operations operations) { this.operations = operations; }

    public EcParameters getEcParameters() { return ecParameters; }
    public void setEcParameters(EcParameters ecParameters) { this.ecParameters = ecParameters; }
}
