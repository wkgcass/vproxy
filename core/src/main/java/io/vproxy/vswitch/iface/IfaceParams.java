package io.vproxy.vswitch.iface;

import io.vproxy.vswitch.util.CSumRecalcType;

public class IfaceParams {
    private Integer baseMTU;
    private Boolean floodAllowed;
    private CSumRecalcType csumRecalc;

    public IfaceParams() {
    }

    public void set(IfaceParams params) {
        this.baseMTU = params.baseMTU;
        this.floodAllowed = params.floodAllowed;
        this.csumRecalc = params.csumRecalc;
    }

    public void fillNullFields(IfaceParams params) {
        if (this.baseMTU == null) {
            this.baseMTU = params.baseMTU;
        }
        if (this.floodAllowed == null) {
            this.floodAllowed = params.floodAllowed;
        }
        if (this.csumRecalc == null) {
            this.csumRecalc = params.csumRecalc;
        }
    }

    public boolean isBaseMTUPresent() {
        return baseMTU != null;
    }

    public int getBaseMTU() {
        return baseMTU == null ? 1500 : baseMTU;
    }

    public void setBaseMTU(int baseMTU) {
        this.baseMTU = baseMTU;
    }

    public boolean isFloodAllowedPresent() {
        return floodAllowed != null;
    }

    public boolean isFloodAllowed() {
        return floodAllowed == null || floodAllowed;
    }

    public void setFloodAllowed(boolean floodAllowed) {
        this.floodAllowed = floodAllowed;
    }

    public boolean isCSumRecalcPresent() {
        return csumRecalc != null;
    }

    public CSumRecalcType getCSumRecalc() {
        return csumRecalc == null ? CSumRecalcType.none : csumRecalc;
    }

    public void setCSumRecalc(CSumRecalcType csumRecalc) {
        this.csumRecalc = csumRecalc;
    }

    @Override
    public String toString() {
        return "mtu " + getBaseMTU()
            + " flood " + (isFloodAllowed() ? "allow" : "deny")
            + " csum-recalc " + getCSumRecalc();
    }

    public String toCommand() {
        return "mtu " + getBaseMTU()
            + " flood " + (isFloodAllowed() ? "allow" : "deny")
            + " csum-recalc " + getCSumRecalc().name();
    }
}
