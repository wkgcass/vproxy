package io.vproxy.vpacket.conntrack.tcp

class TcpTimeout(
  val synSent: Int = 120,
  val synRecv: Int = 60,
  val established: Int = 432000,
  val finWait: Int = 120,
  val lastAck: Int = 30,
  val closeWait: Int = 60,
  val close: Int = 10,
  val timeWait: Int = 120,
  val unack: Int = 300,
) {
  companion object {
    @JvmField
    val DEFAULT = TcpTimeout()
  }
}
