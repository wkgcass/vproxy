module vproxy {
    requires jdk.unsupported;
    requires java.scripting;
    requires java.management;
    // we now definitely need nashorn
    //noinspection removal
    requires jdk.scripting.nashorn;
}
