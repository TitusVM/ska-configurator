package com.pki.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single operation (use, modify, block, or unblock).
 * Contains timing attributes and one or more boundaries defining the segregation of duties.
 */
public class Operation {

    private long delayMillis = 0;
    private long timeLimitMillis = 0;
    private List<Boundary> boundaries = new ArrayList<>();

    public long getDelayMillis() { return delayMillis; }
    public void setDelayMillis(long delayMillis) { this.delayMillis = delayMillis; }

    public long getTimeLimitMillis() { return timeLimitMillis; }
    public void setTimeLimitMillis(long timeLimitMillis) { this.timeLimitMillis = timeLimitMillis; }

    public List<Boundary> getBoundaries() { return boundaries; }
    public void setBoundaries(List<Boundary> boundaries) { this.boundaries = boundaries; }
}
