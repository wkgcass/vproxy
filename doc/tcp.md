# TCP stack

## Intro

vproxy implements a simple tcp stack in vswitch sub module.

The stack is very simple. Some modifications are made to the tcp state machine to simplify the implementation.

Only passive socket is supported due to the lack of use cases. But it's easy to implement one because all the codes can be directly reused after state transfers into `ESTABLISHED` state.

Only `PSH` and `FIN` packets are retransmitted, dropped `SYN-ACK` packets are re-sent only when receiving retransmitted `SYN` packets.

Congestion control is very simple which is only based on un-acked bytes.

Since vproxy tcp stack is running inside the user space application, the stack will ack a segment after it's retrieved by the upper level code. It is different from most kernel tcp implementations which acks a packet when data is written to the kernel receiving buffer.

Because the stack works for application, when application says `close()`, the connection will reset immediately, `TIME_WAIT` state does not exist. If the user calls `close()` while there's still data to be transferred, the statm first transfers into `FIN_WAIT_1` just like calling `shutdown(W)`, then the connection will be reset after all sending data acknowledged.

## Statem

There are some differences between traditional kernel tcp stack and what vproxy implements.

```
                                 CLOSED
                                   |
                      appl: listen |
                                   |
                                   v
                                 LISTEN


                    +----------- CLOSED <------------------------------+
                   /                                                   |
   recv:SYN       /                                                    |
   send:SYN,ACK  /                                                     |
                +                                                      |
                |                                                      |
                v                                                      ^
             SYN_RCVD              active socket not implemented       |
                |                                                      |
                +                                                      |
               / \  recv: ACK                                          |
              /   \ send: nothing                                      |
             /     \                                                   |
            /       +-------> ESTABLISHED                              |
           +                       |    \                appl: close   ^
           |                       |     +------->------------->-------+
 appl: close(lsn_fd)      +--------+--------+            send: RST     |
 send: RST |             /                   \                         |
           |  appl: shutdown         recv: FIN\                        |
           |  send: FIN/             send: ACK \                       |
           |          /                         \       appl: close    |
           |     FIN_WAIT_1                 CLOSE_WAIT -------->-------+
           v         |                           |      send: RST      ^
           |     recv: ACK                  appl:shutdown              |
           |     send: nothing              send:FIN                   |
           |         |                           |                     |
           v         |                           v     appl: close     |
           |         |                        CLOSING -----------------+
           |         |                           ^     send: RST       |
           |         v           recv: FIN       |                     |
           |     FIN_WAIT_2 ---------------------+                     ^
           v                     send: RST                             |
           |                                                           |
           +--------------->----------------->---------------->--------+
```
