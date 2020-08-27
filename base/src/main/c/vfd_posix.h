#ifndef VFD_POSIX_H
    #define VFD_POSIX_H 1
    #define byte char


    #ifdef FSTACK
        #define HAVE_FF_KQUEUE 1
        #include <ff_api.h>
    #endif
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
    #define V_IPPROTO_TCP  IPPROTO_TCP
    #ifdef SOL_IP
        #define V_SOL_IP   SOL_IP
    #else
        #define V_SOL_IP   IPPROTO_IP
    #endif
    #define V_SHUT_WR      SHUT_WR
    typedef struct linger v_linger;

    #ifdef FSTACK
        // disable, let it fail
        #define V_IP_TRANSPARENT -1
    #else
        #ifdef IP_TRANSPARENT
            #define V_IP_TRANSPARENT IP_TRANSPARENT
        #else
            // unsupported, let it fail
            #define V_IP_TRANSPARENT -1
        #endif
    #endif

    #ifdef FSTACK
        #define v_socket      ff_socket
        #define v_bind        ff_bind
        #define v_listen      ff_listen
        #define v_accept      ff_accept
        #define v_setsockopt  ff_setsockopt
        #define v_connect     ff_connect
        #define v_shutdown    ff_shutdown
        #define v_getsockname ff_getsockname
        #define v_getpeername ff_getpeername
        #define v_sendto      ff_sendto
        #define v_recvfrom    ff_recvfrom
        typedef struct linux_sockaddr v_sockaddr;
    #else
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
    #endif



    #include <netinet/tcp.h>
    #define V_TCP_NODELAY  TCP_NODELAY



    #include <unistd.h>

    #ifdef FSTACK
        #define v_close       ff_close
        #define v_read        ff_read
        #define v_write       ff_write
    #else
        #define v_close       close
        #define v_read        read
        #define v_write       write
        #define v_pipe        pipe
    #endif
    typedef struct sockaddr_in  v_sockaddr_in;
    typedef struct sockaddr_in6 v_sockaddr_in6;
    #include <sys/un.h>
    typedef struct sockaddr_un  v_sockaddr_un;
    #ifndef UNIX_PATH_MAX
        #define UNIX_PATH_MAX 100
    #endif



    #include <sys/time.h>

    #ifdef FSTACK
        #define v_gettimeofday ff_gettimeofday
    #else
        #define v_gettimeofday gettimeofday
    #endif
    typedef struct timeval v_timeval;



    // we cannot use ff_fcntl to set non-blocking
    // f-stack bug, use ioctl instead
    // https://github.com/F-Stack/f-stack/issues/146#issuecomment-356867119
    #include <sys/ioctl.h>
    #include <net/if.h>
    #define V_FIONBIO FIONBIO
    #define V_SIOCGIFFLAGS SIOCGIFFLAGS
    #define V_SIOCSIFFLAGS SIOCSIFFLAGS
    #ifdef FSTACK
        #define v_ioctl ff_ioctl
    #else
        #define v_ioctl ioctl
    #endif



    #include <arpa/inet.h>

    #define v_htons htons
    #define v_htonl htonl
    #define v_ntohl ntohl
    #define v_ntohs ntohs

    #define v_inet_pton inet_pton
    #define v_inet_ntop inet_ntop



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
    #define v_bzero bzero

    #include <fcntl.h>

    // for tap support
    #ifdef __linux__
      #include <string.h>
      #include <linux/if.h>
      #include <linux/if_tun.h>
    #endif
    #ifndef IFNAMSIZ
      #define IFNAMSIZ 16
    #endif


#endif
