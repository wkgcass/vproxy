FROM ubuntu:18.04

COPY sources.list /etc/apt/sources.list

RUN apt-get update -y
RUN apt-get install -y iproute2

ADD vproxy /vproxy
ADD libvfdposix.so /libvfdposix.so

RUN mkdir -p /run/docker/plugins
RUN mkdir -p /var/vproxy/docker-network-plugin/post-scripts
RUN mkdir -p /var/run/netns
RUN mkdir -p /var/run/docker/netns
RUN mkdir -p /dev/net
