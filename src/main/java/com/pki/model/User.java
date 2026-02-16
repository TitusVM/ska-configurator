package com.pki.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A user (person) in the SKA configuration.
 * Maps to a {@code <user>} element in the XML and a row in the Jira CSV export.
 */
public class User {

    private String email = "";
    private String userId = "";
    private String userIdIntegration = "";
    private String cn = "";          // e.g. "Baesler Boris KJBDG0"
    private String name = "";        // e.g. "Baesler Boris"
    private String organisation = "";
    private String certificate = ""; // PEM-encoded X.509 certificate

    // Role assignments parsed from CSV (Org Owner / Org SecOff / Org Op columns).
    // Values are module identifiers like "CVCA PP (Prod)".
    private Set<String> orgOwnerOf = new LinkedHashSet<>();
    private Set<String> orgSecOffOf = new LinkedHashSet<>();
    private Set<String> orgOpOf = new LinkedHashSet<>();

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserIdIntegration() { return userIdIntegration; }
    public void setUserIdIntegration(String userIdIntegration) { this.userIdIntegration = userIdIntegration != null ? userIdIntegration : ""; }

    public String getCn() { return cn; }
    public void setCn(String cn) { this.cn = cn; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOrganisation() { return organisation; }
    public void setOrganisation(String organisation) { this.organisation = organisation; }

    public String getCertificate() { return certificate; }
    public void setCertificate(String certificate) { this.certificate = certificate; }

    public Set<String> getOrgOwnerOf() { return orgOwnerOf; }
    public void setOrgOwnerOf(Set<String> orgOwnerOf) { this.orgOwnerOf = orgOwnerOf; }

    public Set<String> getOrgSecOffOf() { return orgSecOffOf; }
    public void setOrgSecOffOf(Set<String> orgSecOffOf) { this.orgSecOffOf = orgSecOffOf; }

    public Set<String> getOrgOpOf() { return orgOpOf; }
    public void setOrgOpOf(Set<String> orgOpOf) { this.orgOpOf = orgOpOf; }

    @Override
    public String toString() {
        return cn.isEmpty() ? name : cn;
    }
}
