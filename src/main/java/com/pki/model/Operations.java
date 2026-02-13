package com.pki.model;

/**
 * Container for the four operation types: use, modify, block, unblock.
 */
public class Operations {

    private Operation use = new Operation();
    private Operation modify = new Operation();
    private Operation block = new Operation();
    private Operation unblock = new Operation();

    public Operation getUse() { return use; }
    public void setUse(Operation use) { this.use = use; }

    public Operation getModify() { return modify; }
    public void setModify(Operation modify) { this.modify = modify; }

    public Operation getBlock() { return block; }
    public void setBlock(Operation block) { this.block = block; }

    public Operation getUnblock() { return unblock; }
    public void setUnblock(Operation unblock) { this.unblock = unblock; }
}
