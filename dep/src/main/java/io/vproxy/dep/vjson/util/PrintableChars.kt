package io.vproxy.dep.vjson.util

typealias PrintableCharFunc = (Char) -> Boolean

object PrintableChars {
  /*#ifndef KOTLIN_NATIVE {{ */@JvmField/*}}*/
  // https://en.wikipedia.org/wiki/List_of_Unicode_characters
  val EveryCharExceptKnownUnprintable: PrintableCharFunc = {
    /*#ifndef KOTLIN_NATIVE {{ */
    @Suppress("DEPRECATION")/*}}*/ val c = it.toInt()
    (c in 32..126) || (c >= 161)
  }
}
