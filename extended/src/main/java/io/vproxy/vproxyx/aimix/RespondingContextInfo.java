package io.vproxy.vproxyx.aimix;

public class RespondingContextInfo {
    public String id;
    public boolean isReasoningContent = false;
    public boolean isLineBeginning = false;
    public boolean isFirstResponse = true;
    public boolean isRoleSent = false;
    public final boolean isStream;

    public RespondingContextInfo(boolean isStream) {
        this.isStream = isStream;
    }

    public void checkAndSetId(String id) {
        if (this.id == null) {
            this.id = id;
        }
    }
}
