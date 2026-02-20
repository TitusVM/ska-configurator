package com.pki.model;

/**
 * Model for the {@code <personalization>} element inside {@code <keys>}.
 * <p>
 * Example XML:
 * <pre>
 * &lt;personalization useKek="true" kekLabel="personalizationKEK"&gt;
 *     &lt;ecParameters curveName="brainpoolP256r1"&gt;...&lt;/ecParameters&gt;
 * &lt;/personalization&gt;
 * </pre>
 */
public class Personalization {

    private boolean enabled = false;         // whether the <personalization> tag is present
    private boolean useKek = true;           // useKek attribute
    private String kekLabel = "";            // kekLabel attribute
    private EcParameters ecParameters = new EcParameters();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isUseKek() { return useKek; }
    public void setUseKek(boolean useKek) { this.useKek = useKek; }

    public String getKekLabel() { return kekLabel; }
    public void setKekLabel(String kekLabel) { this.kekLabel = kekLabel; }

    public EcParameters getEcParameters() { return ecParameters; }
    public void setEcParameters(EcParameters ecParameters) { this.ecParameters = ecParameters; }
}
