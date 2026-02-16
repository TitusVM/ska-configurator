package com.pki.model;

import java.io.File;
import java.util.*;

/**
 * Manages multiple {@link SkaConfigEntry} instances that belong to the same
 * working folder, together with a <em>master user pool</em> that merges all
 * users across entries by CN (unique key).
 *
 * <p>The master user pool is the single source-of-truth for user details
 * (name, email, certificate, …). Individual SKA entries hold only the set of
 * CNs that participate in that configuration; the actual {@link User} data is
 * looked up from the pool.</p>
 */
public class SkaWorkspace {

    private final List<SkaConfigEntry> entries = new ArrayList<>();
    private final List<User> masterUserPool = new ArrayList<>();
    private int activeIndex = -1;

    // ---- entries management ------------------------------------------------

    public List<SkaConfigEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void addEntry(SkaConfigEntry entry) {
        entries.add(entry);
        if (activeIndex < 0) {
            activeIndex = 0;
        }
    }

    public void removeEntry(SkaConfigEntry entry) {
        int idx = entries.indexOf(entry);
        entries.remove(entry);
        if (entries.isEmpty()) {
            activeIndex = -1;
        } else if (idx <= activeIndex) {
            activeIndex = Math.max(0, activeIndex - 1);
        }
    }

    public SkaConfigEntry getActiveEntry() {
        return (activeIndex >= 0 && activeIndex < entries.size())
                ? entries.get(activeIndex) : null;
    }

    public int getActiveIndex() { return activeIndex; }

    public void setActiveIndex(int activeIndex) {
        if (activeIndex < -1 || activeIndex >= entries.size()) {
            throw new IndexOutOfBoundsException("Invalid active index: " + activeIndex);
        }
        this.activeIndex = activeIndex;
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public boolean hasAnyDirty() {
        return entries.stream().anyMatch(SkaConfigEntry::isDirty);
    }

    // ---- master user pool --------------------------------------------------

    public List<User> getMasterUserPool() {
        return masterUserPool;
    }

    /**
     * Find a user in the master pool by CN.
     */
    public User findUserByCn(String cn) {
        for (User u : masterUserPool) {
            if (u.getCn().equals(cn)) return u;
        }
        return null;
    }

    /**
     * Rebuild the master user pool from all entries' user lists.
     * Users with the same CN are merged: the first occurrence supplies the
     * base data, later occurrences may fill in missing fields.
     *
     * <p>Call this after loading a folder of SKA files.</p>
     */
    public void rebuildMasterUserPool() {
        Map<String, User> byCn = new LinkedHashMap<>();

        for (SkaConfigEntry entry : entries) {
            for (User u : entry.getConfig().getUsers()) {
                String cn = u.getCn();
                if (cn == null || cn.isEmpty()) continue;

                if (!byCn.containsKey(cn)) {
                    // first time – deep copy so pool owns the data
                    byCn.put(cn, deepCopyUser(u));
                } else {
                    // merge: fill in blanks from later occurrence
                    mergeUser(byCn.get(cn), u);
                }
            }
        }

        masterUserPool.clear();
        masterUserPool.addAll(byCn.values());
    }

    /**
     * Returns the set of CNs assigned to the given entry.
     */
    public Set<String> getCnsForEntry(SkaConfigEntry entry) {
        Set<String> cns = new LinkedHashSet<>();
        for (User u : entry.getConfig().getUsers()) {
            if (u.getCn() != null && !u.getCn().isEmpty()) {
                cns.add(u.getCn());
            }
        }
        return cns;
    }

    /**
     * Synchronise an entry's user list from the master pool, keeping only
     * the given set of CNs. This is called before saving.
     */
    public void syncEntryUsersFromPool(SkaConfigEntry entry, Set<String> cns) {
        List<User> entryUsers = new ArrayList<>();
        for (User poolUser : masterUserPool) {
            if (cns.contains(poolUser.getCn())) {
                entryUsers.add(deepCopyUser(poolUser));
            }
        }
        entry.getConfig().setUsers(entryUsers);
    }

    // ---- helpers ------------------------------------------------------------

    private static User deepCopyUser(User src) {
        User copy = new User();
        copy.setCn(src.getCn());
        copy.setName(src.getName());
        copy.setEmail(src.getEmail());
        copy.setOrganisation(src.getOrganisation());
        copy.setUserId(src.getUserId());
        copy.setUserIdIntegration(src.getUserIdIntegration());
        copy.setCertificate(src.getCertificate());
        copy.setOrgOwnerOf(new LinkedHashSet<>(src.getOrgOwnerOf()));
        copy.setOrgSecOffOf(new LinkedHashSet<>(src.getOrgSecOffOf()));
        copy.setOrgOpOf(new LinkedHashSet<>(src.getOrgOpOf()));
        return copy;
    }

    /**
     * Merge {@code src} into {@code target}: only fill fields that are blank
     * in target but present in src.
     */
    private static void mergeUser(User target, User src) {
        if (target.getName().isEmpty() && !src.getName().isEmpty())
            target.setName(src.getName());
        if (target.getEmail().isEmpty() && !src.getEmail().isEmpty())
            target.setEmail(src.getEmail());
        if (target.getOrganisation().isEmpty() && !src.getOrganisation().isEmpty())
            target.setOrganisation(src.getOrganisation());
        if (target.getUserId().isEmpty() && !src.getUserId().isEmpty())
            target.setUserId(src.getUserId());
        if (target.getUserIdIntegration().isEmpty() && !src.getUserIdIntegration().isEmpty())
            target.setUserIdIntegration(src.getUserIdIntegration());
        if (target.getCertificate().isEmpty() && !src.getCertificate().isEmpty())
            target.setCertificate(src.getCertificate());
        target.getOrgOwnerOf().addAll(src.getOrgOwnerOf());
        target.getOrgSecOffOf().addAll(src.getOrgSecOffOf());
        target.getOrgOpOf().addAll(src.getOrgOpOf());
    }

    /**
     * Reset workspace to empty state.
     */
    public void clear() {
        entries.clear();
        masterUserPool.clear();
        activeIndex = -1;
    }
}
