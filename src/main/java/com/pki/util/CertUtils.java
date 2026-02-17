package com.pki.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for parsing X.509 certificates from PEM text
 * and extracting report-relevant fields.
 */
public final class CertUtils {

    private CertUtils() {}

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /** Key usage bit names per RFC 5280, index 0..8. */
    private static final String[] KEY_USAGE_NAMES = {
            "digitalSignature", "nonRepudiation", "keyEncipherment",
            "dataEncipherment", "keyAgreement", "keyCertSign",
            "cRLSign", "encipherOnly", "decipherOnly"
    };

    /**
     * Parsed certificate information for report output.
     */
    public static class CertInfo {
        public final String subject;
        public final String issuer;
        public final String notBefore;
        public final String notAfter;
        public final String serialNumber;
        public final String keyUsage;
        public final String sha256Fingerprint;

        public CertInfo(String subject, String issuer, String notBefore,
                        String notAfter, String serialNumber, String keyUsage,
                        String sha256Fingerprint) {
            this.subject = subject;
            this.issuer = issuer;
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            this.serialNumber = serialNumber;
            this.keyUsage = keyUsage;
            this.sha256Fingerprint = sha256Fingerprint;
        }
    }

    /**
     * Parse a PEM-encoded X.509 certificate and extract report fields.
     *
     * @param pem the PEM text (with or without BEGIN/END markers)
     * @return parsed info, or {@code null} if parsing fails or PEM is blank
     */
    public static CertInfo parse(String pem) {
        if (pem == null || pem.isBlank()) return null;

        try {
            // Ensure PEM has markers (some certs might be stored without them)
            String normalized = pem.trim();
            if (!normalized.startsWith("-----BEGIN")) {
                normalized = "-----BEGIN CERTIFICATE-----\n" + normalized + "\n-----END CERTIFICATE-----";
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8)));

            String subject = cert.getSubjectX500Principal().getName();
            String issuer = cert.getIssuerX500Principal().getName();
            String notBefore = DATE_FMT.format(cert.getNotBefore().toInstant());
            String notAfter = DATE_FMT.format(cert.getNotAfter().toInstant());
            String serial = cert.getSerialNumber().toString(16).toUpperCase();
            String keyUsage = formatKeyUsage(cert.getKeyUsage());
            String fingerprint = sha256Fingerprint(cert);

            return new CertInfo(subject, issuer, notBefore, notAfter, serial, keyUsage, fingerprint);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatKeyUsage(boolean[] bits) {
        if (bits == null) return "(not set)";
        List<String> active = new ArrayList<>();
        for (int i = 0; i < bits.length && i < KEY_USAGE_NAMES.length; i++) {
            if (bits[i]) active.add(KEY_USAGE_NAMES[i]);
        }
        return active.isEmpty() ? "(none)" : String.join(", ", active);
    }

    private static String sha256Fingerprint(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", digest[i] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return "N/A";
        }
    }
}
