FROM vproxyio/runtime

ADD preset /preset
ADD vproxy.jar /vproxy.jar

ENTRYPOINT ["/usr/bin/env", "--", "java", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED", "-XX:+CriticalJNINatives", "-Djava.library.path=/usr/lib/`uname -m`-linux-gnu", "-jar", "/vproxy.jar"]
