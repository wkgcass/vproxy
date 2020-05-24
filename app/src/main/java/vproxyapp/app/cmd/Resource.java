package vproxyapp.app.cmd;

public class Resource {
    public ResourceType type;
    public String alias;
    public Resource parentResource;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.fullname).append(" ").append(alias);
        if (parentResource != null) {
            sb.append(" in ").append(parentResource);
        }
        return sb.toString();
    }
}
