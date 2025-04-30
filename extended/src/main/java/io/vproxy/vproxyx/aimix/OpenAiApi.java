package io.vproxy.vproxyx.aimix;

import io.vproxy.base.util.Logger;
import vjson.JSON;
import vjson.JSONObject;
import vjson.deserializer.rule.*;
import vjson.util.ObjectBuilder;

import java.util.ArrayList;
import java.util.List;

public class OpenAiApi {
    private OpenAiApi() {
    }

    public static class Model implements JSONObject {
        public String id;

        @Override
        public JSON.Object toJson() {
            return new ObjectBuilder()
                .put("id", id)
                .build();
        }
    }

    public static class GetModel implements JSONObject {
        public List<Model> data = new ArrayList<>();

        @Override
        public JSON.Object toJson() {
            return new ObjectBuilder()
                .putArray("data", arr -> data.forEach(e -> arr.addInst(e.toJson())))
                .build();
        }
    }

    public static class ChatCompletion implements JSONObject {
        public boolean stream = true;
        public String model;
        public List<ChatCompletionMessage> messages = new ArrayList<>();
        public ChatCompletionStreamOptions streamOptions = new ChatCompletionStreamOptions();

        @Override
        public JSON.Object toJson() {
            return new ObjectBuilder()
                .put("stream", stream)
                .put("model", model)
                .putArray("messages", arr -> messages.forEach(e -> arr.addInst(e.toJson())))
                .putInst("stream_options", streamOptions.toJson())
                .build();
        }

        public OllamaApi.OllamaChatRequest toOllamaChatRequest(OllamaApi.OllamaOptions options) {
            var ollamaReq = new OllamaApi.OllamaChatRequest();
            ollamaReq.model = model;
            ollamaReq.messages = messages.stream().map(ChatCompletionMessage::toOllamaMessage).toList();
            ollamaReq.stream = stream;
            ollamaReq.options = options;
            return ollamaReq;
        }

        public ChatCompletion copy() {
            var c = new ChatCompletion();
            c.stream = stream;
            c.model = model;
            c.messages = new ArrayList<>(messages.stream().map(ChatCompletionMessage::copy).toList());
            if (c.streamOptions != null) {
                c.streamOptions = streamOptions.copy();
            }
            return c;
        }

        public void printToStdout(Config config) {
            Logger.alert(config.printPromptSymbol + " ChatCompletion " + model);
            var i = -1;
            for (var msg : messages) {
                ++i;
                if (i != 0) {
                    System.out.println();
                }
                Logger.alert(config.printPromptSymbol + " Message " + (i + 1) + "/" + messages.size());
                if (msg.simpleContent != null) {
                    System.out.print(msg.simpleContent);
                    continue;
                }
                if (msg.content.size() == 1 && msg.content.getFirst().type == ChatCompletionContentType.text) {
                    System.out.print(msg.content.getFirst().text);
                    continue;
                }
                var j = -1;
                for (var content : msg.content) {
                    ++j;
                    if (content.type == ChatCompletionContentType.image_url) {
                        var url = content.imageUrl.url;
                        var idx = url.indexOf("base64,");
                        url = url.substring(idx + "base64,".length()).trim();
                        var bytes = url.length();
                        if (url.endsWith("=="))
                            bytes -= 2;
                        else if (url.endsWith("="))
                            bytes -= 1;
                        bytes = bytes * 3 / 4;
                        if (j != 0) {
                            System.out.println();
                        }
                        Logger.alert("Content " + (j + 1) + "/" + msg.content.size() + " " + content.type + ": " + bytes + " bytes");
                    } else {
                        if (j != 0) {
                            System.out.println();
                        }
                        Logger.alert("Content " + (j + 1) + "/" + msg.content.size() + " " + content.type);
                        System.out.print(content.text);
                    }
                }
            }
            System.out.println();
            Logger.alert(config.printPromptSymbol + " END");
        }
    }

    public static class ChatCompletionMessage implements JSONObject {
        public Role role; // user, assistant, system
        public String simpleContent;
        public List<ChatCompletionContent> content;

        @Override
        public JSON.Object toJson() {
            var ob = new ObjectBuilder();
            ob.put("role", role.toString());
            if (simpleContent != null) {
                ob.put("content", simpleContent);
            } else {
                ob.putArray("content", arr -> content.forEach(e -> arr.addInst(e.toJson())));
            }
            return ob.build();
        }

        public OllamaApi.OllamaMessage toOllamaMessage() {
            var ollamaMsg = new OllamaApi.OllamaMessage();
            ollamaMsg.role = role;
            if (simpleContent != null) {
                ollamaMsg.content = simpleContent;
            } else {
                var images = content.stream().filter(it -> it.type == OpenAiApi.ChatCompletionContentType.image_url).map(it -> {
                    var url = it.imageUrl.url;
                    var idx = url.indexOf("base64,");
                    if (idx < 0) {
                        throw new RuntimeException("image url is not a base64 encoded url: $url");
                    }
                    return url.substring(idx + "base64,".length()).trim();
                }).toList();
                if (!images.isEmpty()) {
                    ollamaMsg.images = images;
                }
                var texts = content.stream().filter(it -> it.type == OpenAiApi.ChatCompletionContentType.text).map(it -> it.text).toList();
                if (texts.isEmpty()) {
                    ollamaMsg.content = "";
                } else if (texts.size() == 1) {
                    ollamaMsg.content = texts.getFirst();
                } else {
                    ollamaMsg.content = String.join("\n", texts);
                }
            }
            return ollamaMsg;
        }

        public ChatCompletionMessage copy() {
            var c = new ChatCompletionMessage();
            c.role = role;
            c.simpleContent = simpleContent;
            if (content != null) {
                c.content = new ArrayList<>(content.stream().map(ChatCompletionContent::copy).toList());
            }
            return c;
        }
    }

    public static class ChatCompletionContent implements JSONObject {
        public ChatCompletionContentType type; // text or image_url
        public String text;
        public ChatCompletionImageUrl imageUrl;

        @Override
        public JSON.Object toJson() {
            var ob = new ObjectBuilder();
            ob.put("type", type.toString());
            if (type.equals(ChatCompletionContentType.text)) {
                ob.put("text", text);
            } else if (type.equals(ChatCompletionContentType.image_url)) {
                ob.putInst("image_url", imageUrl.toJson());
            }
            return ob.build();
        }

        public ChatCompletionContent copy() {
            var c = new ChatCompletionContent();
            c.type = type;
            c.text = text;
            c.imageUrl = imageUrl;
            return c;
        }
    }

    public enum ChatCompletionContentType {
        text,
        image_url,
    }

    public static class ChatCompletionImageUrl implements JSONObject {
        public String url; // data:image/xxx;base64,xxx

        @Override
        public JSON.Object toJson() {
            return new ObjectBuilder()
                .put("url", url)
                .build();
        }
    }

    public static class ChatCompletionStreamOptions implements JSONObject {
        public boolean includeUsage = true;

        @Override
        public JSON.Object toJson() {
            return new ObjectBuilder()
                .put("include_usage", includeUsage)
                .build();
        }

        public ChatCompletionStreamOptions copy() {
            var o = new ChatCompletionStreamOptions();
            o.includeUsage = includeUsage;
            return o;
        }
    }

    public static class CompletionResponse implements JSONObject {
        public String id;
        public List<CompletionChoice> choices = new ArrayList<>();
        public long created;
        public String model;
        public CompletionUsage usage;

        public static final Rule<CompletionResponse> rule = new ObjectRule<>(CompletionResponse::new)
            .put("id", (o, v) -> o.id = v, StringRule.get())
            .put("choices", (o, v) -> o.choices = v, new ArrayRule<List<CompletionChoice>, CompletionChoice>(
                ArrayList::new, List::add, CompletionChoice.rule))
            .put("created", (o, v) -> o.created = v, LongRule.get())
            .put("model", (o, v) -> o.model = v, StringRule.get())
            .put("usage", (o, v) -> o.usage = v, new NullableRule<>(CompletionUsage.rule));

        @Override
        public JSON.Object toJson() {
            var ob = new ObjectBuilder()
                .put("id", id)
                .putArray("choices", arr -> choices.forEach(e -> arr.addInst(e.toJson())))
                .put("created", created)
                .put("model", model);
            if (usage != null) {
                ob.putInst("usage", usage.toJson());
            }
            return ob.build();
        }

        public void printToStdout(Config config) {
            Logger.alert(config.printResponseSymbol + " CompletionResponse");
            if (!choices.isEmpty()) {
                var c = choices.getFirst();
                System.out.print(c.message.content);
            }
            System.out.println();
            Logger.alert(config.printResponseSymbol + " END");
        }

        public boolean isNotEmpty() {
            for (var c : choices) {
                if (c.delta != null) {
                    if (c.delta.content != null && !c.delta.content.isEmpty()) {
                        return true;
                    }
                }
                if (c.message != null) {
                    if (c.message.content != null && !c.message.content.isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static class CompletionChoice implements JSONObject {
        public String finishReason;
        public int index;
        public CompletionChoiceMessage message;
        public CompletionChoiceMessage delta;

        public static final Rule<CompletionChoice> rule = new ObjectRule<>(CompletionChoice::new)
            .put("finish_reason", (o, v) -> o.finishReason = v, NullableStringRule.get())
            .put("index", (o, v) -> o.index = v, IntRule.get())
            .put("message", (o, v) -> o.message = v, CompletionChoiceMessage.rule)
            .put("delta", (o, v) -> o.delta = v, CompletionChoiceMessage.rule);

        @Override
        public JSON.Object toJson() {
            var ob = new ObjectBuilder()
                .put("finish_reason", finishReason)
                .put("index", index);
            if (message != null) {
                ob.putInst("message", message.toJson());
            }
            if (delta != null) {
                ob.putInst("delta", delta.toJson());
            }
            return ob.build();
        }
    }

    public static class CompletionChoiceMessage implements JSONObject {
        public String content;
        public Role role;

        public static final Rule<CompletionChoiceMessage> rule = new ObjectRule<>(CompletionChoiceMessage::new)
            .put("content", (o, v) -> o.content = v, StringRule.get())
            .put("role", (o, v) -> {
                if (v != null) o.role = Role.valueOf(v);
            }, NullableStringRule.get());

        @Override
        public JSON.Object toJson() {
            var ob = new ObjectBuilder()
                .put("content", content);
            if (role != null) {
                ob.put("role", role.toString());
            }
            return ob.build();
        }
    }

    public static class CompletionUsage implements JSONObject {
        public int completionTokens;
        public int promptTokens;
        public int totalTokens;

        public static final Rule<CompletionUsage> rule = new ObjectRule<>(CompletionUsage::new)
            .put("completion_tokens", (o, v) -> o.completionTokens = v, IntRule.get())
            .put("prompt_tokens", (o, v) -> o.promptTokens = v, IntRule.get())
            .put("total_tokens", (o, v) -> o.totalTokens = v, IntRule.get());

        @Override
        public JSON.Object toJson() {
            return new ObjectBuilder()
                .put("completion_tokens", completionTokens)
                .put("prompt_tokens", promptTokens)
                .put("total_tokens", totalTokens)
                .build();
        }
    }
}
