FROM wkgcass/vproxy-runtime:latest

RUN apt-get install -y ethtool

ADD init.sh /init.sh
ADD default-mirror.json /default-mirror.json
ADD vproxy.jar /vproxy.jar
ADD libvfdposix.so /libvfdposix.so
ADD libvpxdp.so /libvpxdp.so
ADD libbpf.so /libbpf.so

RUN chmod +x /init.sh

RUN mkdir -p /dev/net
RUN mkdir -p /var/run/docker
RUN mkdir -p /x-etc

ENTRYPOINT []
CMD []
