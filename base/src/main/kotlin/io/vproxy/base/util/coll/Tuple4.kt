package vproxy.base.util.coll

data class Tuple4<A, B, C, D>(
  @JvmField
  val _1: A,
  @JvmField
  val _2: B,
  @JvmField
  val _3: C,
  @JvmField
  val _4: D
) {
  override fun toString(): String {
    return "Tuple($_1, $_2, $_3, $_4)"
  }
}
