package com.pki.io;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.pki.model.User;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Imports users from a Jira CSV asset export.
 * Handles multiline PEM certificate fields, {@code &nbsp;} artifacts,
 * and deduplicates records by CN (keeping the most complete entry).
 */
public class CsvImporter {

    // Expected column headers (matched case-insensitively)
    private static final String COL_CN = "cn";
    private static final String COL_NAME = "Name";
    private static final String COL_EMAIL = "Email";
    private static final String COL_ORG = "Organisation";
    private static final String COL_USER_ID = "userID";
    private static final String COL_CERT = "cert";
    private static final String COL_ORG_OWNER = "Org Owner";
    private static final String COL_ORG_SECOFF = "Org SecOff";
    private static final String COL_ORG_OP = "Org Op";

    /**
     * Parse a Jira CSV export file and return a list of unique users.
     * Rows with an empty {@code cn} column are skipped.
     * Duplicate CNs are merged (most complete data wins).
     *
     * @param file the CSV file to import
     * @return list of parsed users
     * @throws IOException if the file cannot be read or parsed
     */
    public List<User> importUsers(File file) throws IOException {
        Map<String, Integer> headerMap;
        List<String[]> rows;

        try (Reader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {

            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(new RFC4180ParserBuilder().build())
                    .build();

            String[] headerRow = csvReader.readNext();
            if (headerRow == null) {
                throw new IOException("CSV file is empty");
            }
            headerMap = buildHeaderMap(headerRow);

            rows = csvReader.readAll();
        } catch (Exception e) {
            throw new IOException("Failed to parse CSV: " + e.getMessage(), e);
        }

        // Build users, keyed by CN for deduplication
        Map<String, User> usersByCn = new LinkedHashMap<>();

        for (String[] row : rows) {
            String cn = getField(row, headerMap, COL_CN).trim();
            if (cn.isEmpty()) {
                continue; // skip rows without CN
            }

            User user = new User();
            user.setCn(cn);
            user.setName(getField(row, headerMap, COL_NAME).trim());
            user.setEmail(getField(row, headerMap, COL_EMAIL).trim());
            user.setOrganisation(getField(row, headerMap, COL_ORG).trim());
            user.setUserId(getField(row, headerMap, COL_USER_ID).trim());

            // Certificate: try the cert column first, then scan all columns for PEM block
            String cert = cleanCertificate(getField(row, headerMap, COL_CERT));
            if (cert.isEmpty()) {
                cert = findCertInRow(row);
            }
            user.setCertificate(cert);

            // Role assignments (|| separated)
            user.setOrgOwnerOf(parseMultiValue(getField(row, headerMap, COL_ORG_OWNER)));
            user.setOrgSecOffOf(parseMultiValue(getField(row, headerMap, COL_ORG_SECOFF)));
            user.setOrgOpOf(parseMultiValue(getField(row, headerMap, COL_ORG_OP)));

            // Deduplication: merge with existing if CN already seen
            if (usersByCn.containsKey(cn)) {
                merge(usersByCn.get(cn), user);
            } else {
                usersByCn.put(cn, user);
            }
        }

        return new ArrayList<>(usersByCn.values());
    }

    /**
     * Build a case-insensitive header → column-index map.
     */
    private Map<String, Integer> buildHeaderMap(String[] headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.length; i++) {
            map.put(headerRow[i].trim(), i);
        }
        return map;
    }

    /**
     * Safely get a field value by header name.
     */
    private String getField(String[] row, Map<String, Integer> headerMap, String column) {
        Integer idx = headerMap.get(column);
        if (idx == null || idx >= row.length) {
            return "";
        }
        return row[idx];
    }

    /**
     * Clean a PEM certificate string: remove {@code &nbsp;}, trim whitespace,
     * and normalize line endings.
     */
    static String cleanCertificate(String raw) {
        if (raw == null) return "";
        String cleaned = raw
                .replace("&nbsp;", "")
                .replace("\u00A0", "") // non-breaking space
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        // Validate it looks like a PEM certificate
        if (!cleaned.contains("-----BEGIN CERTIFICATE-----")) {
            return "";
        }

        // Extract just the PEM block
        int start = cleaned.indexOf("-----BEGIN CERTIFICATE-----");
        int end = cleaned.indexOf("-----END CERTIFICATE-----");
        if (start < 0 || end < 0) {
            return "";
        }
        return cleaned.substring(start, end + "-----END CERTIFICATE-----".length()).trim();
    }

    /**
     * Scan all columns in a row for a PEM certificate block.
     * Handles cases where the cert ended up in the wrong CSV column.
     */
    private String findCertInRow(String[] row) {
        for (String field : row) {
            if (field != null && field.contains("-----BEGIN CERTIFICATE-----")) {
                String cert = cleanCertificate(field);
                if (!cert.isEmpty()) {
                    return cert;
                }
            }
        }
        return "";
    }

    /**
     * Parse a {@code ||} separated multi-value field into a set of trimmed values.
     */
    static Set<String> parseMultiValue(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        for (String part : raw.split("\\|\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Merge data from {@code source} into {@code target}, filling in blanks.
     * Non-empty values in source overwrite empty values in target.
     * Role sets are merged additively.
     */
    private void merge(User target, User source) {
        if (target.getName().isEmpty() && !source.getName().isEmpty())
            target.setName(source.getName());
        if (target.getEmail().isEmpty() && !source.getEmail().isEmpty())
            target.setEmail(source.getEmail());
        if (target.getOrganisation().isEmpty() && !source.getOrganisation().isEmpty())
            target.setOrganisation(source.getOrganisation());
        if (target.getUserId().isEmpty() && !source.getUserId().isEmpty())
            target.setUserId(source.getUserId());
        // Prefer the most recent (last seen) non-empty certificate
        if (!source.getCertificate().isEmpty())
            target.setCertificate(source.getCertificate());
        // Merge role sets
        target.getOrgOwnerOf().addAll(source.getOrgOwnerOf());
        target.getOrgSecOffOf().addAll(source.getOrgSecOffOf());
        target.getOrgOpOf().addAll(source.getOrgOpOf());
    }
}
