FROM debian:9.7-slim

## apt
RUN apt-get update -y
RUN apt-get install -y wget curl

## use the / directory
WORKDIR /

## download the vproxy runtime
RUN wget -q https://github.com/wkgcass/vproxy/releases/download/1.0.0-ALPHA-4/vproxy-runtime-linux.tar.gz
## extract (the extract result would be a directory "./vproxy-runtime-linux")
RUN tar zxf vproxy-runtime-linux.tar.gz

## download the latest vproxy
RUN curl https://raw.githubusercontent.com/wkgcass/vproxy/master/src/main/java/net/cassite/vproxy/app/Application.java 2>/dev/null \
    | grep '_THE_VERSION_' \
    | awk '{print $3}' \
    | cut -d '"' -f 2 \
    1> /version
RUN wget -q https://github.com/wkgcass/vproxy/releases/download/$(cat /version)/vproxy-$(cat /version).jar
RUN mv vproxy-$(cat /version).jar vproxy.jar

## set path variable
ENV PATH="/vproxy-runtime-linux/bin:$PATH"

## default we start the vproxy with a resp-controller
CMD ["java", \
     "-jar", "/vproxy.jar", \
     "resp-controller", "0.0.0.0:16379", "123456", \
     "allowSystemCallInNonStdIOController", \
     "noStdIOController" \
    ]
