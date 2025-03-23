package io.vproxy.vproxyx.aimix;

public record ModelServerMetadata(
    ModelServerType type,
    boolean nonStopping,
    OllamaApi.OllamaOptions ollamaOptions
) {
}
