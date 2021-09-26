package vproxy.base.util.coll

@Suppress("PropertyName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
open class Tuple<A, B>(
  @JvmField val _1: A,
  @JvmField val _2: B
) : java.util.Map.Entry<A, B> {
  @JvmField
  val left = this._1

  @JvmField
  val right = this._2

  override fun getKey(): A {
    return _1
  }

  override fun getValue(): B {
    return _2
  }

  override fun setValue(newValue: B): B {
    throw UnsupportedOperationException()
  }

  override fun toString(): String {
    return "Tuple($key, $value)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Tuple<*, *>

    if (key != other.key) return false
    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int {
    var result = key?.hashCode() ?: 0
    result = 31 * result + (value?.hashCode() ?: 0)
    return result
  }
}
