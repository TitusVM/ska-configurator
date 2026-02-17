package com.pki.io;

import com.opencsv.CSVWriter;
import com.pki.model.*;
import com.pki.util.CertUtils;
import com.pki.util.CurveUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Generates two CSV report files from loaded SKA configurations:
 * <ol>
 *   <li><b>Membership report</b> — flat view of every group membership across all
 *       sections, operations, and boundaries, including EC curve info.</li>
 *   <li><b>User / certificate report</b> — list of all users with parsed X.509
 *       certificate details (subject, validity, key usage, fingerprint).</li>
 * </ol>
 */
public class ReportGenerator {

    /**
     * Result holder for the two generated report files.
     */
    public static class ReportResult {
        public final File membershipFile;
        public final File userFile;
        public final int membershipRows;
        public final int userRows;

        public ReportResult(File membershipFile, File userFile, int membershipRows, int userRows) {
            this.membershipFile = membershipFile;
            this.userFile = userFile;
            this.membershipRows = membershipRows;
            this.userRows = userRows;
        }
    }

    /**
     * Generate both report CSV files.
     *
     * @param entries    the list of SKA config entries (may be a single entry)
     * @param users      the complete user list (master pool or single-file users)
     * @param outputDir  directory to write the CSV files into
     * @return result with file references and row counts
     * @throws IOException if writing fails
     */
    public ReportResult generate(List<SkaConfigEntry> entries, List<User> users,
                                 File outputDir) throws IOException {
        File membershipFile = new File(outputDir, "report_memberships.csv");
        File userFile = new File(outputDir, "report_users.csv");

        int membershipRows = writeMembershipReport(entries, users, membershipFile);
        int userRows = writeUserReport(users, userFile);

        return new ReportResult(membershipFile, userFile, membershipRows, userRows);
    }

    // ─────────────────────────────────────────────────────────
    // Membership report
    // ─────────────────────────────────────────────────────────

    private static final String[] MEMBERSHIP_HEADER = {
            "SKA Module", "Section", "EC Curve", "Key Label",
            "Start Validity", "End Validity", "Blocked On Init",
            "Operation", "Delay (ms)", "Time Limit (ms)",
            "Boundary #", "Group Name", "Quorum",
            "Type", "Member/Key"
    };

    private int writeMembershipReport(List<SkaConfigEntry> entries, List<User> users,
                                      File outFile) throws IOException {
        // Build CN → Name lookup for resolving member names
        Map<String, String> cnToName = new LinkedHashMap<>();
        for (User u : users) {
            if (!u.getCn().isBlank()) {
                cnToName.put(u.getCn(), u.getName());
            }
        }

        int rowCount = 0;
        try (CSVWriter w = new CSVWriter(new FileWriter(outFile))) {
            w.writeNext(MEMBERSHIP_HEADER);

            for (SkaConfigEntry entry : entries) {
                SkaConfig cfg = entry.getConfig();
                String module = cfg.getModuleName();

                // Organization
                rowCount += writeSection(w, module, "Organization", cfg.getOrganization(), cnToName);
                // SKA Plus
                rowCount += writeSection(w, module, "SKA Plus", cfg.getSkaPlus(), cnToName);
                // SKA Modify
                rowCount += writeSection(w, module, "SKA Modify", cfg.getSkaModify(), cnToName);
                // Keys
                if (cfg.getKeysProto() != null) {
                    rowCount += writeKeysProto(w, module, cfg.getKeysProto(), cnToName);
                }
            }
        }
        return rowCount;
    }

    private int writeSection(CSVWriter w, String module, String sectionName,
                             SkaSection section, Map<String, String> cnToName) {
        String curve = CurveUtils.resolveCurveName(
                section.getEcParameters().getCurveName(),
                section.getEcParameters().getPemText());
        String keyLabel = section.getKeyLabel();
        String startVal = section.getStartValidity();
        String endVal = section.getEndValidity();
        String blocked = String.valueOf(section.isBlockedOnInitialize());

        int rows = 0;
        Operations ops = section.getOperations();
        rows += writeOperation(w, module, sectionName, curve, keyLabel, startVal, endVal,
                blocked, "use", ops.getUse(), cnToName);
        rows += writeOperation(w, module, sectionName, curve, keyLabel, startVal, endVal,
                blocked, "modify", ops.getModify(), cnToName);
        rows += writeOperation(w, module, sectionName, curve, keyLabel, startVal, endVal,
                blocked, "block", ops.getBlock(), cnToName);
        rows += writeOperation(w, module, sectionName, curve, keyLabel, startVal, endVal,
                blocked, "unblock", ops.getUnblock(), cnToName);
        return rows;
    }

    private int writeKeysProto(CSVWriter w, String module, KeysProto kp,
                               Map<String, String> cnToName) {
        String sectionName = "Keys" + (kp.getChildName().isBlank() ? "" : " (" + kp.getChildName() + ")");
        String curve = CurveUtils.resolveCurveName(
                kp.getEcParameters().getCurveName(),
                kp.getEcParameters().getPemText());

        int rows = 0;
        Operations ops = kp.getOperations();
        rows += writeOperation(w, module, sectionName, curve, "", "", "", "",
                "use", ops.getUse(), cnToName);
        rows += writeOperation(w, module, sectionName, curve, "", "", "", "",
                "modify", ops.getModify(), cnToName);
        rows += writeOperation(w, module, sectionName, curve, "", "", "", "",
                "block", ops.getBlock(), cnToName);
        rows += writeOperation(w, module, sectionName, curve, "", "", "", "",
                "unblock", ops.getUnblock(), cnToName);
        return rows;
    }

    private int writeOperation(CSVWriter w, String module, String section, String curve,
                               String keyLabel, String startVal, String endVal,
                               String blocked, String opName, Operation op,
                               Map<String, String> cnToName) {
        int rows = 0;
        String delay = String.valueOf(op.getDelayMillis());
        String timeLimit = String.valueOf(op.getTimeLimitMillis());

        List<Boundary> boundaries = op.getBoundaries();
        if (boundaries.isEmpty()) {
            // Write a single row indicating no boundaries configured
            w.writeNext(new String[]{
                    module, section, curve, keyLabel, startVal, endVal, blocked,
                    opName, delay, timeLimit,
                    "", "", "", "", "(no boundaries)"
            });
            return 1;
        }

        for (int bi = 0; bi < boundaries.size(); bi++) {
            Boundary boundary = boundaries.get(bi);
            String boundaryNum = String.valueOf(bi + 1);

            for (Group group : boundary.getGroups()) {
                String groupName = group.getName();
                String quorum = String.valueOf(group.getQuorum());

                // Member CNs
                for (String cn : group.getMemberCns()) {
                    String displayName = cnToName.getOrDefault(cn, cn);
                    String memberDisplay = displayName.equals(cn) ? cn : displayName + " (" + cn + ")";
                    w.writeNext(new String[]{
                            module, section, curve, keyLabel, startVal, endVal, blocked,
                            opName, delay, timeLimit,
                            boundaryNum, groupName, quorum,
                            "Member", memberDisplay
                    });
                    rows++;
                }

                // Key labels
                for (String kl : group.getKeyLabels()) {
                    w.writeNext(new String[]{
                            module, section, curve, keyLabel, startVal, endVal, blocked,
                            opName, delay, timeLimit,
                            boundaryNum, groupName, quorum,
                            "Key", kl
                    });
                    rows++;
                }

                // Empty group (no members and no keys)
                if (group.getMemberCns().isEmpty() && group.getKeyLabels().isEmpty()) {
                    w.writeNext(new String[]{
                            module, section, curve, keyLabel, startVal, endVal, blocked,
                            opName, delay, timeLimit,
                            boundaryNum, groupName, quorum,
                            "", "(empty group)"
                    });
                    rows++;
                }
            }
        }
        return rows;
    }

    // ─────────────────────────────────────────────────────────
    // User / certificate report
    // ─────────────────────────────────────────────────────────

    private static final String[] USER_HEADER = {
            "CN", "Name", "Email", "Organisation",
            "Has Certificate",
            "Subject", "Issuer",
            "Not Before", "Not After",
            "Key Usage", "Serial Number", "SHA-256 Fingerprint"
    };

    private int writeUserReport(List<User> users, File outFile) throws IOException {
        // Sort by CN for a clean report
        List<User> sorted = new ArrayList<>(users);
        sorted.sort(Comparator.comparing(User::getCn, String.CASE_INSENSITIVE_ORDER));

        try (CSVWriter w = new CSVWriter(new FileWriter(outFile))) {
            w.writeNext(USER_HEADER);

            for (User u : sorted) {
                String hasCert = (u.getCertificate() != null && !u.getCertificate().isBlank())
                        ? "Yes" : "No";

                CertUtils.CertInfo ci = CertUtils.parse(u.getCertificate());
                if (ci != null) {
                    w.writeNext(new String[]{
                            u.getCn(), u.getName(), u.getEmail(), u.getOrganisation(),
                            hasCert,
                            ci.subject, ci.issuer,
                            ci.notBefore, ci.notAfter,
                            ci.keyUsage, ci.serialNumber, ci.sha256Fingerprint
                    });
                } else {
                    w.writeNext(new String[]{
                            u.getCn(), u.getName(), u.getEmail(), u.getOrganisation(),
                            hasCert,
                            "", "", "", "", "", "", ""
                    });
                }
            }
        }
        return sorted.size();
    }
}
