package com.pki.model;

/**
 * Shared model for the organization, skaplus, and skamodify sections.
 * Each has key metadata, EC parameters, and a set of operations.
 */
public class SkaSection {

    private boolean blockedOnInitialize = false;
    private String keyLabel = "";
    private String startValidity = "";   // format: YYYY-MM-DD
    private String endValidity = "";     // format: YYYY-MM-DD
    private EcParameters ecParameters = new EcParameters();
    private Operations operations = new Operations();

    public boolean isBlockedOnInitialize() { return blockedOnInitialize; }
    public void setBlockedOnInitialize(boolean blockedOnInitialize) { this.blockedOnInitialize = blockedOnInitialize; }

    public String getKeyLabel() { return keyLabel; }
    public void setKeyLabel(String keyLabel) { this.keyLabel = keyLabel; }

    public String getStartValidity() { return startValidity; }
    public void setStartValidity(String startValidity) { this.startValidity = startValidity; }

    public String getEndValidity() { return endValidity; }
    public void setEndValidity(String endValidity) { this.endValidity = endValidity; }

    public EcParameters getEcParameters() { return ecParameters; }
    public void setEcParameters(EcParameters ecParameters) { this.ecParameters = ecParameters; }

    public Operations getOperations() { return operations; }
    public void setOperations(Operations operations) { this.operations = operations; }
}
