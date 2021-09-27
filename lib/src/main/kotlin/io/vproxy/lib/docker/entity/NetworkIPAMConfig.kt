package vproxy.lib.docker.entity

import vjson.deserializer.rule.ObjectRule
import vjson.deserializer.rule.Rule
import vjson.deserializer.rule.StringRule

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
