package io.vproxy.vproxyx;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.vproxyx.aimix.AiMixServer;
import io.vproxy.vproxyx.aimix.ConfigBuilder;
import vjson.CharStream;
import vjson.JSON;
import vjson.parser.ParserOptions;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class AiMix {
    private static final String HELP_STR = """
        Usage:
            conf=${path-to-config-file}
        
        {
        listen = ${listening port}
        name = aimix # exposed model name
        multimodal {
            name = ${name of the model}
            servers = [
                {
                    type = ${openai or ollama}
                    address = 127.0.0.1:11434
                    ollama_options = {
                        num_thread = 33
                    }
                }
            ]
        }
        reasoning {
            name = ${name of the model}
            servers = [
                {
                    type = ${openai or ollama}
                    address = 127.0.0.1:10002
                }
            ]
            reasoning_tag = "<think>"      # optional
            end_reasoning_tag = "</think>" # optional
        }
        language {
            name = ${name of the model}
            servers = [
                {
                    type = ${openai or ollama}
                    address = 127.0.0.1:10003
                }
            ]
        }
        prompt {
            # ...... see class Prompt in ConfigBuilder.java
        }
        sys {
            # ...... see class Sys in ConfigBuilder.java
        }
        }
        """;

    public static void main0(String[] args) throws Exception {
        String confPath = null;
        for (var a : args) {
            if (a.equals("help") || a.equals("-h") || a.equals("-help") || a.equals("--help")) {
                System.out.println(HELP_STR);
                return;
            }
            if (a.startsWith("conf=")) {
                var v = a.substring("conf=".length()).trim();
                if (v.isEmpty()) {
                    System.err.println("conf={empty path} is not allowed");
                    System.exit(1);
                    return;
                }
                confPath = v;
            }
        }
        if (confPath == null) {
            System.err.println("conf=${...} is not specified");
            System.exit(1);
            return;
        }
        String content;
        try (var fis = new FileInputStream(confPath)) {
            content = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }
        var configBuilder = JSON.deserialize(CharStream.from(content), ConfigBuilder.rule, ParserOptions.allFeatures());
        configBuilder.validate();

        var el = new EventLoopGroup("ai-mix");
        el.add("ai-mix-" + 0);
        var config = configBuilder.build(el);
        var server = new AiMixServer(el, config);
        server.start();
    }
}
