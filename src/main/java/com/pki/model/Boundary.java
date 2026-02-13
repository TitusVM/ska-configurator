package com.pki.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A boundary defines a set of groups that must each meet their quorum
 * for the enclosing operation to be authorized.
 * An operation can have multiple boundaries (e.g., modify has a key-based boundary + a member-based one).
 */
public class Boundary {

    private List<Group> groups = new ArrayList<>();

    public List<Group> getGroups() { return groups; }
    public void setGroups(List<Group> groups) { this.groups = groups; }
}
