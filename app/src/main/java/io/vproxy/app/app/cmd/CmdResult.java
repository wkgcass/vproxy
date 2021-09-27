package vproxy.app.app.cmd;

public class CmdResult {
    public final Object originalResult;
    public final Object processedResult; // primitive and string types or list of them
    public final String strResult;

    // simply for null result
    public CmdResult() {
        this(null, null, "");
    }

    // simply for string result value
    public CmdResult(String strResult) {
        this(strResult, strResult, strResult);
    }

    public CmdResult(Object originalResult, Object processedResult, String strResult) {
        this.originalResult = originalResult;
        this.processedResult = processedResult;
        this.strResult = strResult;
    }

    @Override
    public String toString() {
        return strResult;
    }
}
