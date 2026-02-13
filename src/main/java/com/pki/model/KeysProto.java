package com.pki.model;

/**
 * Model for the {@code <keys><proto>} section.
 * Contains operations and EC parameters but no key label or validity metadata.
 */
public class KeysProto {

    private Operations operations = new Operations();
    private EcParameters ecParameters = new EcParameters();

    public Operations getOperations() { return operations; }
    public void setOperations(Operations operations) { this.operations = operations; }

    public EcParameters getEcParameters() { return ecParameters; }
    public void setEcParameters(EcParameters ecParameters) { this.ecParameters = ecParameters; }
}
