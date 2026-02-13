package com.pki.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of members (people) or keys that must satisfy a quorum.
 * A group contains either member CNs or key labels (not both in practice).
 */
public class Group {

    private int quorum = 1;
    private String name = "";
    private List<String> memberCns = new ArrayList<>();
    private List<String> keyLabels = new ArrayList<>();

    public int getQuorum() { return quorum; }
    public void setQuorum(int quorum) { this.quorum = quorum; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getMemberCns() { return memberCns; }
    public void setMemberCns(List<String> memberCns) { this.memberCns = memberCns; }

    public List<String> getKeyLabels() { return keyLabels; }
    public void setKeyLabels(List<String> keyLabels) { this.keyLabels = keyLabels; }
}
