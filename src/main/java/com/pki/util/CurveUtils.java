package com.pki.util;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared utility for EC curve name detection.
 * Keeps a single source of truth for known curve names, used by both the
 * GUI combo-box and the report generator.
 * <p>
 * Uses Bouncy Castle ASN.1 parsing to handle <b>both</b> named-form (OID)
 * and explicit-form (full domain parameters) EC PARAMETERS encodings.
 * For explicit parameters, the actual mathematical domain parameters
 * (curve equation, generator point, order, cofactor) are compared against
 * every known curve — labels and OIDs are never trusted.
 */
public final class CurveUtils {

    private CurveUtils() {}

    /** Known named curves in display order (empty string = "none selected"). */
    public static final String[] KNOWN_CURVES = {
            "", "brainpoolP224r1", "brainpoolP256r1", "brainpoolP384r1", "brainpoolP512r1",
            "prime256v1", "secp384r1", "secp521r1", "secp256r1"
    };

    /** Map of common OIDs to display names for named-form detection. */
    private static final Map<String, String> OID_TO_NAME = new LinkedHashMap<>();
    static {
        OID_TO_NAME.put("1.2.840.10045.3.1.7",    "secp256r1");
        OID_TO_NAME.put("1.3.132.0.34",           "secp384r1");
        OID_TO_NAME.put("1.3.132.0.35",           "secp521r1");
        OID_TO_NAME.put("1.3.132.0.10",           "secp256k1");
        OID_TO_NAME.put("1.2.840.10045.3.1.1",    "secp192r1");
        OID_TO_NAME.put("1.3.132.0.33",           "secp224r1");
        OID_TO_NAME.put("1.3.36.3.3.2.8.1.1.5",   "brainpoolP224r1");
        OID_TO_NAME.put("1.3.36.3.3.2.8.1.1.7",   "brainpoolP256r1");
        OID_TO_NAME.put("1.3.36.3.3.2.8.1.1.11",  "brainpoolP384r1");
        OID_TO_NAME.put("1.3.36.3.3.2.8.1.1.13",  "brainpoolP512r1");
    }

    /** Curves to compare against when matching explicit parameters. */
    private static final String[] COMPARE_CURVES = {
            "secp256r1", "secp384r1", "secp521r1", "secp256k1",
            "brainpoolP256r1", "brainpoolP384r1", "brainpoolP512r1",
            "secp192r1", "secp224r1", "brainpoolP224r1"
    };

    /**
     * Alias map: alternative names that refer to the same curve.
     * Used by {@link #resolveCurveName} for alias-aware label comparison.
     */
    private static final Map<String, String> CANONICAL = new LinkedHashMap<>();
    static {
        CANONICAL.put("prime256v1", "secp256r1");
        CANONICAL.put("P-256",     "secp256r1");
        CANONICAL.put("P-384",     "secp384r1");
        CANONICAL.put("P-521",     "secp521r1");
    }

    /** Returns the canonical name for a curve (resolves known aliases). */
    public static String canonicalize(String name) {
        if (name == null) return "";
        return CANONICAL.getOrDefault(name, name);
    }

    /**
     * Detect the curve name from raw EC PARAMETERS PEM text.
     * <p>
     * Strategy:
     * <ol>
     *   <li>Parse PEM → DER via Base64</li>
     *   <li>Parse ASN.1 structure with Bouncy Castle</li>
     *   <li>If named form (OID): look up OID in known map, fallback to BC's ECNamedCurveTable</li>
     *   <li>If explicit form: parse as X9ECParameters and compare curve/G/n/h against
     *       every known curve's reference parameters</li>
     * </ol>
     *
     * @param pemText the raw PEM text (may include BEGIN/END EC PARAMETERS markers)
     * @return detected curve name, or {@code null} if not detected
     */
    public static String detectCurveFromPem(String pemText) {
        if (pemText == null || pemText.isBlank()) return null;

        try {
            String base64 = pemText.trim()
                    .replace("-----BEGIN EC PARAMETERS-----", "")
                    .replace("-----END EC PARAMETERS-----", "")
                    .replaceAll("\\s", "");
            if (base64.isEmpty()) return null;

            byte[] der = Base64.getDecoder().decode(base64);

            try (ASN1InputStream asn1In = new ASN1InputStream(new ByteArrayInputStream(der))) {
                ASN1Object asn1 = asn1In.readObject();

                if (asn1 instanceof ASN1ObjectIdentifier oid) {
                    // ── Named form: the DER is just an OID ──
                    return resolveOid(oid);
                } else {
                    // ── Explicit form: full domain parameters ──
                    return matchExplicitParameters(der);
                }
            }
        } catch (Exception e) {
            // Malformed PEM, bad Base64, unparseable ASN.1
        }
        return null;
    }

    /** Resolve a named-form OID to a curve name. */
    private static String resolveOid(ASN1ObjectIdentifier oid) {
        String oidStr = oid.getId();

        // Check our known OID map first
        String known = OID_TO_NAME.get(oidStr);
        if (known != null) return known;

        // Fallback to Bouncy Castle's full curve table
        try {
            String bcName = ECNamedCurveTable.getName(oid);
            if (bcName != null) return bcName;
        } catch (Exception ignored) {}

        return null; // Unknown OID
    }

    /**
     * Match explicit EC parameters against all known curves by comparing
     * the actual mathematical domain parameters.
     */
    private static String matchExplicitParameters(byte[] der) {
        try {
            X9ECParameters inputParams = parseX9(der);
            if (inputParams == null) return null;

            for (String curveName : COMPARE_CURVES) {
                try {
                    X9ECParameters ref = ECNamedCurveTable.getByName(curveName);
                    if (ref != null && ecParamsMatch(inputParams, ref)) {
                        return curveName;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return null; // No known curve matched
    }

    /** Parse raw DER bytes into X9ECParameters. */
    private static X9ECParameters parseX9(byte[] der) {
        try (ASN1InputStream in = new ASN1InputStream(new ByteArrayInputStream(der))) {
            ASN1Object obj = in.readObject();
            return obj != null ? X9ECParameters.getInstance(obj) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compare two {@link X9ECParameters} by their mathematical domain parameters:
     * curve equation, generator point G, order n, and cofactor h.
     */
    private static boolean ecParamsMatch(X9ECParameters a, X9ECParameters b) {
        try {
            if (!a.getCurve().equals(b.getCurve())) return false;
            if (!a.getG().equals(b.getG())) return false;
            if (!a.getN().equals(b.getN())) return false;
            if (a.getH() != null && b.getH() != null && !a.getH().equals(b.getH())) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolve the display name for EC parameters.
     * <p>
     * Strategy (never trusts labels — always verifies via parameter matching):
     * <ol>
     *   <li>If PEM text is present, detect curve from actual parameters</li>
     *   <li>If detected and curveName matches (alias-aware): return curve name</li>
     *   <li>If detected but curveName differs: flag mismatch</li>
     *   <li>If PEM present but no curve matched: "Explicit parameters (unknown curve)"</li>
     *   <li>If curveName set but no PEM: curveName + " (unverified — no parameters)"</li>
     *   <li>If nothing set: "(none)"</li>
     * </ol>
     */
    public static String resolveCurveName(String curveName, String pemText) {
        boolean hasLabel = curveName != null && !curveName.isBlank();
        boolean hasPem = pemText != null && !pemText.isBlank();

        if (hasPem) {
            String detected = detectCurveFromPem(pemText);
            if (detected != null) {
                if (hasLabel) {
                    String canonicalLabel = canonicalize(curveName);
                    String canonicalDetected = canonicalize(detected);
                    if (!canonicalDetected.equals(canonicalLabel)) {
                        return detected + " (label mismatch: \"" + curveName + "\")";
                    }
                }
                return detected;
            }
            // PEM present but no known curve matched
            if (hasLabel) {
                return curveName + " (unverified — parameters don't match known curves)";
            }
            return "Explicit parameters (unknown curve)";
        }

        if (hasLabel) {
            return curveName + " (unverified — no parameters)";
        }
        return "(none)";
    }
}
