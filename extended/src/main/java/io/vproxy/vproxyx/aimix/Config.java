package io.vproxy.vproxyx.aimix;

import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.connection.ServerSock;

public class Config {
    public ServerSock listen;
    public String name = DEFAULT_EXPOSED_MODEL_NAME;
    public Model multimodal;
    public Model reasoning;
    public Model text;

    public String imageDescriptionStartTag = DEFAULT_IMAGE_DESCRIPTION_START_TAG;
    public String imageDescriptionStopTag = DEFAULT_IMAGE_DESCRIPTION_STOP_TAG;

    public String imagePromptStartTag = DEFAULT_IMAGE_PROMPT_START_TAG;
    public String imagePromptStopTag = DEFAULT_IMAGE_PROMPT_STOP_TAG;

    public String imageToDescPrompt = DEFAULT_IMAGE_TO_DESC_PROMPT;
    public String imageAdditionalResponse = DEFAULT_IMAGE_ADDITIONAL_RESPONSE;
    public String imageDescriptionHeadResponseTemplate = DEFAULT_IMAGE_DESCRIPTION_HEAD_RESPONSE_TEMPLATE;
    public String titleGenerationMatching = DEFAULT_TITLE_GENERATION_MATCHING;
    public String tagsGenerationMatching = DEFAULT_TAGS_GENERATION_MATCHING;

    public boolean printReceivedPrompt = true;
    public boolean printOutputResponse = true;
    public String printPromptSymbol = DEFAULT_PRINT_PROMPT_SYMBOL;
    public String printResponseSymbol = DEFAULT_PRINT_RESPONSE_SYMBOL;
    public int generateTitleOrTagsPromptLengthThreshold = 8192;

    public Model getMultimodal() {
        return multimodal;
    }

    public Model getReasoning() {
        if (reasoning != null)
            return reasoning;
        if (text != null)
            return text;
        return multimodal;
    }

    public Model getText() {
        if (text != null)
            return text;
        if (reasoning != null)
            return reasoning;
        return multimodal;
    }

    public String getReasoningTag() {
        if (reasoning == null) {
            return "<think>";
        }
        return reasoning.reasoningTag;
    }

    public String getEndReasoningTag() {
        if (reasoning == null) {
            return "</think>";
        }
        return reasoning.endReasoningTag;
    }

    public static class Model {
        public String name;
        public ServerGroup servers;

        public String reasoningTag = "<think>";
        public String endReasoningTag = "</think>";
    }

    private static final String DEFAULT_EXPOSED_MODEL_NAME = "aimix";

    private static final String DEFAULT_IMAGE_DESCRIPTION_START_TAG = "<$ImageDescription$>";
    private static final String DEFAULT_IMAGE_DESCRIPTION_STOP_TAG = "</$ImageDescription$>";

    private static final String DEFAULT_IMAGE_PROMPT_START_TAG = "<$ImagePrompt$>";
    private static final String DEFAULT_IMAGE_PROMPT_STOP_TAG = "</$ImagePrompt$>";

    private static final String DEFAULT_IMAGE_TO_DESC_PROMPT = """
        Please help me check the images so I can complete the task.
        Begin with "Detailed description:" followed by a thorough objective depiction of the given image without including speculation or analysis.
        Focus solely on exhaustive factual details.
        If characters are present, briefly note their expressions or conveyed emotions.
        Conclude with "Keywords:" followed by a list of tags or keywords separated by commas on the same line.
        """;
    private static final String DEFAULT_IMAGE_ADDITIONAL_RESPONSE = """
        请注意，上述所有针对图片的描述可能无法确切地反映图片中的内容。
        在处理这些描述时，需要对它们进行适当的质疑和推理。
        """;
    private static final String DEFAULT_IMAGE_DESCRIPTION_HEAD_RESPONSE_TEMPLATE = "**如下为第{{ n }}/{{ total }}张图片的描述**";
    /**
     * copied from OpenWebUI
     */
    private static final String DEFAULT_TITLE_GENERATION_MATCHING = """
        ### Task:
        Generate a concise, 3-5 word title with an emoji summarizing the chat history.
        ### Guidelines:
        - The title should clearly represent the main theme or subject of the conversation.
        - Use emojis that enhance understanding of the topic, but avoid quotation marks or special formatting.
        - Write the title in the chat's primary language; default to English if multilingual.
        - Prioritize accuracy over excessive creativity; keep it clear and simple.
        ### Output:
        JSON format: { "title": "your concise title here" }
        ### Examples:
        - { "title": "📉 Stock Market Trends" },
        - { "title": "🍪 Perfect Chocolate Chip Recipe" },
        - { "title": "Evolution of Music Streaming" },
        - { "title": "Remote Work Productivity Tips" },
        - { "title": "Artificial Intelligence in Healthcare" },
        - { "title": "🎮 Video Game Development Insights" }
        ### Chat History:
        <chat_history>
        """;
    /**
     * copied from OpenWebUI
     */
    private static final String DEFAULT_TAGS_GENERATION_MATCHING = """
        ### Task:
        Generate 1-3 broad tags categorizing the main themes of the chat history, along with 1-3 more specific subtopic tags.
        
        ### Guidelines:
        - Start with high-level domains (e.g. Science, Technology, Philosophy, Arts, Politics, Business, Health, Sports, Entertainment, Education)
        - Consider including relevant subfields/subdomains if they are strongly represented throughout the conversation
        - If content is too short (less than 3 messages) or too diverse, use only ["General"]
        - Use the chat's primary language; default to English if multilingual
        - Prioritize accuracy over specificity
        
        ### Output:
        JSON format: { "tags": ["tag1", "tag2", "tag3"] }
        
        ### Chat History:
        <chat_history>
        """;

    private static final String DEFAULT_PRINT_PROMPT_SYMBOL = ">>>>>>>>>>>>";
    private static final String DEFAULT_PRINT_RESPONSE_SYMBOL = "<<<<<<<<<<<<";
}
