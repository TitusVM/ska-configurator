package com.pki.util;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.*;

/**
 * Tests for {@link CurveUtils} and {@link CertUtils}.
 * Verifies both named-form and explicit-form EC parameter detection
 * using Bouncy Castle for test data generation.
 */
public class CurveUtilsTest {

    // ── detectCurveFromPem — null/malformed ─────────────────

    @Test
    public void testDetectNull() {
        assertNull(CurveUtils.detectCurveFromPem(null));
        assertNull(CurveUtils.detectCurveFromPem(""));
        assertNull(CurveUtils.detectCurveFromPem("   "));
    }

    @Test
    public void testDetectMalformed() {
        assertNull(CurveUtils.detectCurveFromPem("not-valid!!!"));
    }

    @Test
    public void testDetectInvalidBase64() {
        assertNull(CurveUtils.detectCurveFromPem(
                "-----BEGIN EC PARAMETERS-----\nAA==\n-----END EC PARAMETERS-----"));
    }

    // ── Named form (OID only) ───────────────────────────────

    @Test
    public void testNamedSecp256r1() {
        String pem = namedFormPem("secp256r1");
        assertEquals("secp256r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testNamedSecp384r1() {
        String pem = namedFormPem("secp384r1");
        assertEquals("secp384r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testNamedSecp521r1() {
        String pem = namedFormPem("secp521r1");
        assertEquals("secp521r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testNamedBrainpoolP256r1() {
        String pem = namedFormPem("brainpoolP256r1");
        assertEquals("brainpoolP256r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testNamedBrainpoolP384r1() {
        String pem = namedFormPem("brainpoolP384r1");
        assertEquals("brainpoolP384r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testNamedBrainpoolP512r1() {
        String pem = namedFormPem("brainpoolP512r1");
        assertEquals("brainpoolP512r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testNamedBrainpoolP224r1() {
        String pem = namedFormPem("brainpoolP224r1");
        assertEquals("brainpoolP224r1", CurveUtils.detectCurveFromPem(pem));
    }

    // ── Explicit form (full domain parameters) ──────────────

    @Test
    public void testExplicitSecp256r1() {
        String pem = explicitFormPem("secp256r1");
        assertNotNull("Should generate explicit PEM", pem);
        assertEquals("secp256r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testExplicitSecp384r1() {
        String pem = explicitFormPem("secp384r1");
        assertNotNull("Should generate explicit PEM", pem);
        assertEquals("secp384r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testExplicitSecp521r1() {
        String pem = explicitFormPem("secp521r1");
        assertNotNull("Should generate explicit PEM", pem);
        assertEquals("secp521r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testExplicitBrainpoolP256r1() {
        String pem = explicitFormPem("brainpoolP256r1");
        assertNotNull("Should generate explicit PEM", pem);
        assertEquals("brainpoolP256r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testExplicitBrainpoolP384r1() {
        String pem = explicitFormPem("brainpoolP384r1");
        assertNotNull("Should generate explicit PEM", pem);
        assertEquals("brainpoolP384r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testExplicitBrainpoolP512r1() {
        String pem = explicitFormPem("brainpoolP512r1");
        assertNotNull("Should generate explicit PEM", pem);
        assertEquals("brainpoolP512r1", CurveUtils.detectCurveFromPem(pem));
    }

    @Test
    public void testExplicitBrainpoolP224r1() {
        String pem = explicitFormPem("brainpoolP224r1");
        assertNotNull("Should generate explicit PEM", pem);
        assertEquals("brainpoolP224r1", CurveUtils.detectCurveFromPem(pem));
    }

    // ── resolveCurveName ────────────────────────────────────

    @Test
    public void testResolveEmpty() {
        assertEquals("(none)", CurveUtils.resolveCurveName("", ""));
        assertEquals("(none)", CurveUtils.resolveCurveName(null, null));
    }

    @Test
    public void testResolveLabelOnlyUnverified() {
        String result = CurveUtils.resolveCurveName("secp384r1", "");
        assertTrue("Should say unverified: " + result, result.contains("unverified"));
    }

    @Test
    public void testResolveMatchingLabelAndPem() {
        String pem = namedFormPem("secp256r1");
        assertEquals("secp256r1", CurveUtils.resolveCurveName("secp256r1", pem));
    }

    @Test
    public void testResolveAliasNotMismatch() {
        // prime256v1 == secp256r1, should not flag mismatch
        String pem = namedFormPem("secp256r1");
        String result = CurveUtils.resolveCurveName("prime256v1", pem);
        assertFalse("Alias should not be flagged as mismatch: " + result,
                result.contains("mismatch"));
        assertEquals("secp256r1", result);
    }

    @Test
    public void testResolveMismatchFlagged() {
        String pem = namedFormPem("secp256r1");
        String result = CurveUtils.resolveCurveName("secp384r1", pem);
        assertTrue("Should flag mismatch: " + result, result.contains("mismatch"));
    }

    @Test
    public void testResolveUnknownPem() {
        String result = CurveUtils.resolveCurveName("",
                "-----BEGIN EC PARAMETERS-----\nAA==\n-----END EC PARAMETERS-----");
        assertTrue("Should indicate unknown: " + result,
                result.contains("unknown") || result.contains("Explicit"));
    }

    // ── Aliases ─────────────────────────────────────────────

    @Test
    public void testCanonicalize() {
        assertEquals("secp256r1", CurveUtils.canonicalize("prime256v1"));
        assertEquals("secp256r1", CurveUtils.canonicalize("P-256"));
        assertEquals("brainpoolP256r1", CurveUtils.canonicalize("brainpoolP256r1"));
    }

    // ── KNOWN_CURVES ────────────────────────────────────────

    @Test
    public void testKnownCurvesArray() {
        assertTrue(CurveUtils.KNOWN_CURVES.length >= 7);
        assertEquals("", CurveUtils.KNOWN_CURVES[0]);
        var curves = java.util.Arrays.asList(CurveUtils.KNOWN_CURVES);
        assertTrue(curves.contains("brainpoolP256r1"));
        assertTrue(curves.contains("secp384r1"));
        assertTrue(curves.contains("secp521r1"));
    }

    // ── CertUtils ───────────────────────────────────────────

    @Test
    public void testCertParseNull() {
        assertNull(CertUtils.parse(null));
        assertNull(CertUtils.parse(""));
        assertNull(CertUtils.parse("   "));
    }

    @Test
    public void testCertParseMalformed() {
        assertNull(CertUtils.parse(
                "-----BEGIN CERTIFICATE-----\nnot-valid\n-----END CERTIFICATE-----"));
    }

    // ── Helpers ─────────────────────────────────────────────

    /**
     * Generate a named-form EC PARAMETERS PEM (just the OID).
     * Uses BC's ECNamedCurveTable to get the OID, then DER-encodes it.
     */
    private String namedFormPem(String curveName) {
        try {
            ASN1ObjectIdentifier oid = ECNamedCurveTable.getOID(curveName);
            byte[] der = oid.getEncoded();
            return wrapPem(der);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate named-form PEM for " + curveName, e);
        }
    }

    /**
     * Generate an explicit-form EC PARAMETERS PEM (full domain parameters).
     * Uses BC's ECNamedCurveTable to get X9ECParameters, then DER-encodes them.
     */
    private String explicitFormPem(String curveName) {
        try {
            X9ECParameters params = ECNamedCurveTable.getByName(curveName);
            byte[] der = params.getEncoded();
            return wrapPem(der);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate explicit-form PEM for " + curveName, e);
        }
    }

    private String wrapPem(byte[] der) {
        return "-----BEGIN EC PARAMETERS-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END EC PARAMETERS-----";
    }
}
