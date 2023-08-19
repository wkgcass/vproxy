FROM vproxyio/runtime

ADD preset /preset
ADD vproxy.jar /vproxy.jar

ENTRYPOINT ["/init.sh", "-jar", "/vproxy.jar"]
