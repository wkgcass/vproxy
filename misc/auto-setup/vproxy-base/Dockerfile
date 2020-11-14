FROM ubuntu:18.04

COPY sources.list /etc/apt/sources.list

RUN apt-get update -y
RUN DEBIAN_FRONTEND="noninteractive" apt-get install -y lsof procps net-tools iproute2 tcpdump strace curl iputils-ping wget build-essential autoconf netcat binutils socat dnsutils vim iptables arping tzdata telnet
ENV TZ="Asia/Shanghai"
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
