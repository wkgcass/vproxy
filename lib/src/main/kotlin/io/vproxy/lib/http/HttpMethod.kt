package io.vproxy.lib.http

enum class HttpMethod {
  GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH, CONNECT, TRACE;

  companion object {
    @JvmField
    val ALL_METHODS = arrayOf(GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH, CONNECT, TRACE)
  }
}
