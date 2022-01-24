package io.vproxy.base.util.coll

data class Tuple3<A, B, C>(
  @JvmField
  val _1: A,
  @JvmField
  val _2: B,
  @JvmField
  val _3: C
) {
  override fun toString(): String {
    return "Tuple($_1, $_2, $_3)"
  }
}
