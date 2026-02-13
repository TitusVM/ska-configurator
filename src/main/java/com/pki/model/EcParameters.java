package com.pki.model;

/**
 * Elliptic-curve parameters for an SKA section.
 * Contains an optional curve name and the raw PEM text.
 */
public class EcParameters {

    private String curveName = "";  // optional, e.g. "brainpoolP256r1"
    private String pemText = "";    // PEM block (-----BEGIN EC PARAMETERS----- ... -----END EC PARAMETERS-----)

    public String getCurveName() { return curveName; }
    public void setCurveName(String curveName) { this.curveName = curveName; }

    public String getPemText() { return pemText; }
    public void setPemText(String pemText) { this.pemText = pemText; }
}
