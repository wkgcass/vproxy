package io.vproxy.lib.docker.entity

import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule

data class NetworkIPAMConfig(
  var subnet: String? = null,
  var gateway: String? = null,
) {
  companion object {
    val rule: Rule<NetworkIPAMConfig> = ObjectRule { NetworkIPAMConfig() }
      .put("Subnet", StringRule) { subnet = it }
      .put("Gateway", StringRule) { gateway = it }
  }
}
