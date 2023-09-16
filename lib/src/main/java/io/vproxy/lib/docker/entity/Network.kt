package io.vproxy.lib.docker.entity

import vjson.deserializer.rule.ArrayRule
import vjson.deserializer.rule.ObjectRule
import vjson.deserializer.rule.Rule
import vjson.deserializer.rule.StringRule

data class Network(
  var id: String? = null,
  var driver: String? = null,
  var ipam: NetworkIPAM? = null,
  var options: Map<String, String>? = null,
) {
  companion object {
    val rule: Rule<Network> = ObjectRule { Network() }
      .put("Id", StringRule) { id = it }
      .put("Driver", StringRule) { driver = it }
      .put("IPAM", NetworkIPAM.rule) { ipam = it }
      .put("Options", { options = it }) {
        ObjectRule<MutableMap<String, String>> { HashMap() }
          .addExtraRule { key, value -> put(key, value!! as String) }
      }
    val arrayRule = ArrayRule<MutableList<Network>, Network>({ ArrayList() }, { add(it) }, rule)
  }
}
