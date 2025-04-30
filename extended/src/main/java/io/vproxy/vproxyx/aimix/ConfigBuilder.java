package io.vproxy.vproxyx.aimix;

import io.vproxy.base.component.check.CheckProtocol;
import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.IPPort;
import vjson.deserializer.rule.*;

import java.util.ArrayList;
import java.util.List;

public class ConfigBuilder {
    public int listen;
    public String name;
    public Model multimodal;
    public Model reasoning;
    public Model text;
    public Prompt prompt;
    public Sys sys;

    public static final Rule<ConfigBuilder> rule = new ObjectRule<>(ConfigBuilder::new)
        .put("listen", (o, v) -> o.listen = v, IntRule.get())
        .put("name", (o, v) -> o.name = v, StringRule.get())
        .put("multimodal", (o, v) -> o.multimodal = v, Model.rule)
        .put("reasoning", (o, v) -> o.reasoning = v, Model.rule)
        .put("text", (o, v) -> o.text = v, Model.rule)
        .put("prompt", (o, v) -> o.prompt = v, Prompt.rule)
        .put("sys", (o, v) -> o.sys = v, Sys.rule);

    public void validate() throws XException {
        if (listen == 0)
            throw new XException("missing listening port");
        if (listen < 1 || listen > 65535)
            throw new XException("invalid listening port, out of range: " + listen);
        if (multimodal != null)
            if (!multimodal.validate("multimodal")) {
                multimodal = null;
            }
        if (reasoning != null)
            if (!reasoning.validate("reasoning")) {
                reasoning = null;
            }
        if (text != null)
            if (!text.validate("language")) {
                text = null;
            }
        if (multimodal == null && reasoning == null && text == null) {
            throw new XException("at least one of multimodal/reasoning/text should be specified");
        }
    }

    public static class Model {
        public String name;
        public List<ModelServer> servers;
        public String reasoningTag;
        public String endReasoningTag;

        public static final Rule<Model> rule = new ObjectRule<>(Model::new)
            .put("name", (o, v) -> o.name = v, StringRule.get())
            .put("servers", (o, v) -> o.servers = v, new ArrayRule<List<ModelServer>, ModelServer>(
                ArrayList::new, List::add, ModelServer.rule
            ))
            .put("reasoning_tag", (o, v) -> o.reasoningTag = v, StringRule.get())
            .put("end_reasoning_tag", (o, v) -> o.endReasoningTag = v, StringRule.get());

        public boolean validate(String section) throws XException {
            if (name == null)
                throw new XException("missing model name to use for " + section);

            if (servers == null || servers.isEmpty())
                return false;
            for (int i = 0; i < servers.size(); i++) {
                var s = servers.get(i);
                s.validate(section + ".servers[" + i + "]");
            }
            return true;
        }

        public Config.Model build(EventLoopGroup elg) throws Exception {
            var model = new Config.Model();
            model.name = name;
            model.servers = new ServerGroup("sg-" + name, elg,
                new HealthCheckConfig(2000, 5000, 2, 3, CheckProtocol.tcp),
                Method.wrr);
            for (var s : servers) {
                s.build(model.servers);
            }
            if (reasoningTag != null)
                model.reasoningTag = reasoningTag;
            if (endReasoningTag != null)
                model.endReasoningTag = endReasoningTag;
            return model;
        }

        @Override
        public String toString() {
            return "Model{" +
                   "name=" + (name == null ? "null" : ("'" + name + "'")) +
                   ", servers=" + servers +
                   ", reasoningTag=" + (reasoningTag == null ? "null" : ("'" + reasoningTag + "'")) +
                   ", endReasoningTag=" + (endReasoningTag == null ? "null" : ("'" + endReasoningTag + "'")) +
                   '}';
        }
    }

    public static class ModelServer {
        public String type;
        public String address;
        public boolean nonStopping = false;
        public OllamaApi.OllamaOptions ollamaOptions;

        public static final Rule<ModelServer> rule = new ObjectRule<>(ModelServer::new)
            .put("type", (o, v) -> o.type = v, StringRule.get())
            .put("address", (o, v) -> o.address = v, StringRule.get())
            .put("non_stopping", (o, v) -> o.nonStopping = v, BoolRule.get())
            .put("ollama_options", (o, v) -> o.ollamaOptions = v, OllamaApi.OllamaOptions.rule);

        public void validate(String path) throws XException {
            if (type == null)
                throw new XException("missing model type for " + path);
            if (!type.equals("openai") && !type.equals("ollama"))
                throw new XException("invalid model type for " + path + ", expecting openai or ollama, but got " + type);
            if (address == null)
                throw new XException("missing model address for " + path);
            if (!IPPort.validL4AddrStr(address))
                throw new XException("invalid model address for " + path + ": " + address);
            if (ollamaOptions != null)
                ollamaOptions.validate(path + ".ollama_options");
        }

        public void build(ServerGroup sg) throws Exception {
            var sh = sg.add(address, new IPPort(address), 10);
            if (type.equals("openai")) {
                sh.data = new ModelServerMetadata(ModelServerType.OPENAI, nonStopping, null);
            } else {
                sh.data = new ModelServerMetadata(ModelServerType.OLLAMA, nonStopping, ollamaOptions);
            }
        }

        @Override
        public String toString() {
            return "ModelServer{" +
                   "type=" + (type == null ? "null" : ("'" + type + "'")) +
                   ", address=" + (address == null ? "null" : ("'" + address + "'")) +
                   ", nonStopping=" + nonStopping +
                   ", ollamaOptions=" + ollamaOptions +
                   '}';
        }
    }

    public static class Prompt {
        public String imageDescriptionStartTag;
        public String imageDescriptionStopTag;
        public String imagePromptStartTag;
        public String imagePromptStopTag;
        public String imageToDescPrompt;
        public String imageAdditionalResponse;
        public String imageDescriptionHeadResponseTemplate;
        public String titleGenerationMatching;
        public String tagsGenerationMatching;
        public Boolean keepReasoningInPrompt;

        public static final Rule<Prompt> rule = new ObjectRule<>(Prompt::new)
            .put("image_description_start_tag", (o, v) -> o.imageDescriptionStartTag = v, StringRule.get())
            .put("image_description_stop_tag", (o, v) -> o.imageDescriptionStopTag = v, StringRule.get())
            .put("image_prompt_start_tag", (o, v) -> o.imagePromptStartTag = v, StringRule.get())
            .put("image_prompt_stop_tag", (o, v) -> o.imagePromptStopTag = v, StringRule.get())
            .put("image_to_desc_prompt", (o, v) -> o.imageToDescPrompt = v, StringRule.get())
            .put("image_additional_response", (o, v) -> o.imageAdditionalResponse = v, StringRule.get())
            .put("image_description_head_response_template", (o, v) -> o.imageDescriptionHeadResponseTemplate = v, StringRule.get())
            .put("title_generation_matching", (o, v) -> o.titleGenerationMatching = v, StringRule.get())
            .put("tags_generation_matching", (o, v) -> o.tagsGenerationMatching = v, StringRule.get())
            .put("keep_reasoning_in_prompt", (o, v) -> o.keepReasoningInPrompt = v, BoolRule.get());

        public void build(Config c) {
            if (imageDescriptionStartTag != null)
                c.imageDescriptionStartTag = imageDescriptionStartTag;
            if (imageDescriptionStopTag != null)
                c.imageDescriptionStopTag = imageDescriptionStopTag;
            if (imagePromptStartTag != null)
                c.imagePromptStartTag = imagePromptStartTag;
            if (imagePromptStopTag != null)
                c.imagePromptStopTag = imagePromptStopTag;
            if (imageToDescPrompt != null)
                c.imageToDescPrompt = imageToDescPrompt;
            if (imageAdditionalResponse != null)
                c.imageAdditionalResponse = imageAdditionalResponse;
            if (imageDescriptionHeadResponseTemplate != null)
                c.imageDescriptionHeadResponseTemplate = imageDescriptionHeadResponseTemplate;
            if (titleGenerationMatching != null)
                c.titleGenerationMatching = titleGenerationMatching;
            if (tagsGenerationMatching != null)
                c.tagsGenerationMatching = tagsGenerationMatching;
            if (keepReasoningInPrompt != null)
                c.keepReasoningInPrompt = keepReasoningInPrompt;
        }

        @Override
        public String toString() {
            return "Prompt{" +
                   "imageDescriptionStartTag=" + (imageDescriptionStartTag == null ? "null" : ("'" + imageDescriptionStartTag + "'")) +
                   ", imageDescriptionStopTag=" + (imageDescriptionStopTag == null ? "null" : ("'" + imageDescriptionStopTag + "'")) +
                   ", imagePromptStartTag=" + (imagePromptStartTag == null ? "null" : ("'" + imagePromptStartTag + "'")) +
                   ", imagePromptStopTag=" + (imagePromptStopTag == null ? "null" : ("'" + imagePromptStopTag + "'")) +
                   ", imageToDescPrompt=" + (imageToDescPrompt == null ? "null" : ("'" + imageToDescPrompt + "'")) +
                   ", imageAdditionalResponse=" + (imageAdditionalResponse == null ? "null" : ("'" + imageAdditionalResponse + "'")) +
                   ", imageDescriptionHeadResponseTemplate=" + (imageDescriptionHeadResponseTemplate == null ? "null" : ("'" + imageDescriptionHeadResponseTemplate + "'")) +
                   ", titleGenerationMatching=" + (titleGenerationMatching == null ? "null" : ("'" + titleGenerationMatching + "'")) +
                   ", tagsGenerationMatching=" + (tagsGenerationMatching == null ? "null" : ("'" + tagsGenerationMatching + "'")) +
                   ", keepReasoningInPrompt=" + keepReasoningInPrompt +
                   '}';
        }
    }

    public static class Sys {
        public Boolean removeReasoningContent;
        public Boolean printReceivedPrompt;
        public Boolean printOutputResponse;
        public String printPromptSymbol;
        public String printResponseSymbol;
        public Integer generateTitleOrTagsPromptLengthThreshold;

        public static final Rule<Sys> rule = new ObjectRule<>(Sys::new)
            .put("remove_reasoning_content", (o, v) -> o.removeReasoningContent = v, BoolRule.get())
            .put("print_received_prompt", (o, v) -> o.printReceivedPrompt = v, BoolRule.get())
            .put("print_output_response", (o, v) -> o.printOutputResponse = v, BoolRule.get())
            .put("print_prompt_symbol", (o, v) -> o.printPromptSymbol = v, StringRule.get())
            .put("print_response_symbol", (o, v) -> o.printResponseSymbol = v, StringRule.get())
            .put("generate_title_or_tags_prompt_length_threshold", (o, v) -> o.generateTitleOrTagsPromptLengthThreshold = v, IntRule.get());

        public void build(Config c) {
            if (removeReasoningContent != null)
                c.removeReasoningContent = removeReasoningContent;
            if (printReceivedPrompt != null)
                c.printReceivedPrompt = printReceivedPrompt;
            if (printOutputResponse != null)
                c.printOutputResponse = printOutputResponse;
            if (printPromptSymbol != null)
                c.printPromptSymbol = printPromptSymbol;
            if (printResponseSymbol != null)
                c.printResponseSymbol = printResponseSymbol;
            if (generateTitleOrTagsPromptLengthThreshold != null)
                c.generateTitleOrTagsPromptLengthThreshold = generateTitleOrTagsPromptLengthThreshold;
        }

        @Override
        public String toString() {
            return "Sys{" +
                   "removeReasoningContent=" + removeReasoningContent +
                   "  printReceivedPrompt=" + printReceivedPrompt +
                   ", printOutputResponse=" + printOutputResponse +
                   ", printPromptSymbol=" + (printPromptSymbol == null ? "null" : ("'" + printPromptSymbol + "'")) +
                   ", printResponseSymbol=" + (printResponseSymbol == null ? "null" : ("'" + printResponseSymbol + "'")) +
                   ", generateTitleOrTagsPromptLengthThreshold=" + generateTitleOrTagsPromptLengthThreshold +
                   '}';
        }
    }

    public Config build(EventLoopGroup elg) throws Exception {
        var config = new Config();

        var listenAddr = new IPPort("127.0.0.1", listen);
        ServerSock.checkBind(listenAddr);
        config.listen = ServerSock.create(listenAddr);
        if (name != null) {
            config.name = name;
        }
        config.multimodal = multimodal == null ? null : multimodal.build(elg);
        config.reasoning = reasoning == null ? null : reasoning.build(elg);
        config.text = text == null ? null : text.build(elg);
        if (prompt != null) {
            prompt.build(config);
        }
        if (sys != null) {
            sys.build(config);
        }
        return config;
    }

    @Override
    public String toString() {
        return "ConfigBuilder{" +
               "listen=" + listen +
               ", name=" + (name == null ? "null" : ("'" + name + "'")) +
               ", multimodal=" + multimodal +
               ", reasoning=" + reasoning +
               ", text=" + text +
               ", prompt=" + prompt +
               ", sys=" + sys +
               '}';
    }
}
