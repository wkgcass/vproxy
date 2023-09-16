package io.vproxy.lib.docker.entity

import vjson.deserializer.rule.ArrayRule
import vjson.deserializer.rule.ObjectRule
import vjson.deserializer.rule.Rule

data class NetworkIPAM(
  var config: List<NetworkIPAMConfig>? = null,
) {
  companion object {
    val rule: Rule<NetworkIPAM> = ObjectRule { NetworkIPAM() }
      .put(
        "Config",
        ArrayRule<MutableList<NetworkIPAMConfig>, NetworkIPAMConfig>({ ArrayList() }, NetworkIPAMConfig.rule) { add(it) })
      { config = it }
  }
}
