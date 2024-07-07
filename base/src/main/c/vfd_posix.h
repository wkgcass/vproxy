#ifndef VFD_POSIX_H
    #define VFD_POSIX_H 1
    #define byte char

    #include "ae.h"
    #ifdef __linux__
        #include <sys/eventfd.h>
    #endif



    #include <sys/socket.h>
    #include <netinet/ip.h>

    #define V_AF_INET      AF_INET
    #define V_AF_INET6     AF_INET6
    #define V_AF_UNIX      AF_UNIX
    #define V_SOCK_STREAM  SOCK_STREAM
    #define V_SOCK_DGRAM   SOCK_DGRAM
    #define V_SOL_SOCKET   SOL_SOCKET
    #define V_SO_LINGER    SO_LINGER
    #define V_SO_REUSEPORT SO_REUSEPORT
    #define V_SO_REUSEADDR SO_REUSEADDR
    #define V_SO_RCVBUF    SO_RCVBUF
    #define V_SO_BROADCAST SO_BROADCAST
    #define V_IPPROTO_TCP  IPPROTO_TCP
    #ifdef SOL_IP
        #define V_SOL_IP   SOL_IP
    #else
        #define V_SOL_IP   IPPROTO_IP
    #endif
    #define V_SHUT_WR      SHUT_WR
    typedef struct linger v_linger;

        #ifdef IP_TRANSPARENT
            #define V_IP_TRANSPARENT IP_TRANSPARENT
        #else
            // unsupported, let it fail
            #define V_IP_TRANSPARENT -1
        #endif

        #define v_socket      socket
        #define v_bind        bind
        #define v_listen      listen
        #define v_accept      accept
        #define v_setsockopt  setsockopt
        #define v_connect     connect
        #define v_shutdown    shutdown
        #define v_getsockname getsockname
        #define v_getpeername getpeername
        #define v_sendto      sendto
        #define v_recvfrom    recvfrom
        typedef struct sockaddr v_sockaddr;



    #include <netinet/tcp.h>
    #define V_TCP_NODELAY  TCP_NODELAY



    #include <unistd.h>

        #define v_close       close
        #define v_read        read
        #define v_write       write
        #define v_pipe        pipe

    typedef struct sockaddr_in  v_sockaddr_in;
    typedef struct sockaddr_in6 v_sockaddr_in6;
    #include <sys/un.h>
    typedef struct sockaddr_un  v_sockaddr_un;
    #ifndef UNIX_PATH_MAX
        #define UNIX_PATH_MAX 100
    #endif



    #include <sys/time.h>
        #define v_gettimeofday gettimeofday
    typedef struct timeval v_timeval;



    // we cannot use ff_fcntl to set non-blocking
    // f-stack bug, use ioctl instead
    // https://github.com/F-Stack/f-stack/issues/146#issuecomment-356867119
    #include <sys/ioctl.h>
    #include <net/if.h>
    #define V_FIONBIO FIONBIO
    #define V_SIOCGIFFLAGS SIOCGIFFLAGS
    #define V_SIOCSIFFLAGS SIOCSIFFLAGS
        #define v_ioctl ioctl



    #include <arpa/inet.h>

    #define v_htons htons
    #define v_htonl htonl
    #define v_ntohl ntohl
    #define v_ntohs ntohs

    #define v_inet_pton inet_pton
    #define v_inet_ntop inet_ntop

    void j2cSockAddrIPv4(v_sockaddr_in* name, int32_t addrHostOrder, uint16_t port);
    int j2cSockAddrIPv6(v_sockaddr_in6* name, char* fullAddrCharArray, uint16_t port);

    #include <errno.h>
    #ifndef EWOULDBLOCK
        #define V_EWOULDBLOCK EAGAIN
    #else
        #define V_EWOULDBLOCK EWOULDBLOCK
    #endif
    #define V_EAGAIN EAGAIN
    #define V_EINPROGRESS EINPROGRESS



    #include <strings.h>
    #include <stdio.h>
    #include <stdlib.h>
    #define v_bzero bzero

    #include <fcntl.h>

    // for tap support
    #ifdef __linux__
      #include <string.h>
      #include <linux/if.h>
      #include <linux/if_tun.h>
    #endif
    #ifdef __APPLE__
      #include <sys/kern_control.h>
      #include <net/if_utun.h>
      #include <sys/sys_domain.h>
    #endif
    #ifndef IFNAMSIZ
      #define IFNAMSIZ 16
    #endif

    // pthread
    #include <pthread.h>

    // util functions
    static inline int v_str_starts_with(const char* str, const char* prefix) {
      int prelen = strlen(prefix);
      int len = strlen(str);
      return len < prelen ? 0 : memcmp(prefix, str, prelen) == 0;
    }

#endif
