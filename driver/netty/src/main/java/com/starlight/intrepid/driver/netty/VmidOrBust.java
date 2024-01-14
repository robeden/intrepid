package com.starlight.intrepid.driver.netty;

import com.starlight.intrepid.VMID;

import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;

/**
 * Simple data class containing either a VMID or an Exception for use
 * with {@link NettyIntrepidDriver#VMID_SLOT_KEY}.
 */
class VmidOrBust {
    private final VMID vmid;
    private final Throwable error;

    VmidOrBust(VMID vmid) {
        this.vmid = requireNonNull(vmid);
        error = null;
    }

    VmidOrBust(Throwable t) {
        this.error = requireNonNull(t);
        vmid = null;
    }

    VMID get() throws ExecutionException {
        if (error != null) throw new ExecutionException(error);
        return vmid;
    }
}
