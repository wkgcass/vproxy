package io.vproxy.vproxyx.aimix

import io.vproxy.base.component.elgroup.EventLoopGroup
import io.vproxy.base.component.svrgroup.SvrHandleConnector
import io.vproxy.base.connection.ConnectionOpts
import io.vproxy.base.connection.NetEventLoop
import io.vproxy.base.http.HttpParserHelper
import io.vproxy.base.http.HttpRespParser
import io.vproxy.base.processor.http1.entity.Chunk
import io.vproxy.base.util.ByteArray
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.RingBuffer
import io.vproxy.base.util.exception.XException
import io.vproxy.lib.common.launch
import io.vproxy.lib.http.HttpServerResponse
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http.Tool
import io.vproxy.lib.http1.CoroutineHttp1ClientConnection
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.lib.tcp.CoroutineConnection
import io.vproxy.lib.tcp.CoroutineServerSock
import io.vproxy.vfd.IPPort
import vjson.JSON
import vjson.simple.SimpleString
import java.util.*

class AiMixServer(
  private val elg: EventLoopGroup,
  private val config: Config,
) {
  private lateinit var el: NetEventLoop

  fun start() {
    el = elg.next()
    val sockServer = CoroutineServerSock(el, config.listen)
    val server = CoroutineHttp1Server(sockServer)

    server.all("/*", Tool.bodyJsonHandler())
    server.all("/*", ::accessLog)
    server.get("/v1/models", ::getModels)
    server.post("/v1/chat/completions", ::chatCompletions)

    el.selectorEventLoop.launch { server.start() }
  }

  fun accessLog(ctx: RoutingContext) {
    Logger.access("${ctx.req.method()} ${ctx.req.uri()}")
    ctx.allowNext()
  }

  suspend fun getModels(ctx: RoutingContext) {
    val getModelResp = OpenAiApi.GetModel()
    val model = OpenAiApi.Model()
    model.id = config.name
    getModelResp.data.add(model)
    ctx.conn.response(200).header("Content-Type", "application/json").send(getModelResp)
  }

  suspend fun chatCompletions(ctx: RoutingContext) {
    val json = ctx.get(Tool.bodyJson) ?: throw XException("body is not provided or is not json")
    json as JSON.Object

    val cc = OpenAiApi.ChatCompletion()

    cc.stream = if (json.containsKey("stream")) {
      json.getBool("stream")
    } else {
      false
    }
    val messages = json.getArray("messages")
    for (i in 0 until messages.length()) {
      val m = OpenAiApi.ChatCompletionMessage()
      cc.messages.add(m)

      val msg = messages.getObject(i)

      m.role = Role.valueOf(msg.getString("role"))

      val content = msg.get("content")
      if (content is JSON.Array) {
        m.content = ArrayList()
        for (j in 0 until content.length()) {
          val contentObj = OpenAiApi.ChatCompletionContent()
          m.content.add(contentObj)

          val c = content.getObject(j)
          val type = c.getString("type")
          if (type == "text") {
            contentObj.text = c.getString("text")
          } else if (type == "image_url") {
            contentObj.imageUrl = OpenAiApi.ChatCompletionImageUrl()
            val imageUrl = c.getObject("image_url")
            contentObj.imageUrl.url = imageUrl.getString("url")
          } else {
            throw XException("unknown chat completion content type ${contentObj.type}")
          }
          contentObj.type = OpenAiApi.ChatCompletionContentType.valueOf(type)
        }
      } else {
        m.simpleContent = (content as JSON.String).toJavaObject()
      }
    }

    if (json.containsKey("stream_options")) {
      val streamOptions = json.getObject("stream_options")
      cc.streamOptions.includeUsage = streamOptions.getBool("include_usage")
    }

    if (config.printReceivedPrompt) {
      cc.printToStdout(config)
    }

    val reqCtx = ReqContext()
    ctx.put(ReqContext.KEY, reqCtx)

    convertReqTokens(cc)
    processReq(ctx, cc)
  }

  private fun convertReqTokens(c: OpenAiApi.ChatCompletion) {
    for ((idx, msg) in c.messages.withIndex()) {
      if (msg.simpleContent != null) {
        msg.simpleContent = convertReqTokens(msg.simpleContent, msg.role, idx == c.messages.size - 1)
      } else {
        for (cc in msg.content) {
          if (cc.type == OpenAiApi.ChatCompletionContentType.text) {
            cc.text = convertReqTokens(cc.text, msg.role, idx == c.messages.size - 1)
          }
        }
      }
    }
  }

  private fun convertReqTokens(c: String, role: Role, isLast: Boolean): String {
    if (role == Role.assistant) {
      return convertReqTokensAssistant(c)
    } else {
      return convertReqTokensSystemOrUser(c, isLast)
    }
  }

  private fun convertReqTokensSystemOrUser(c: String, isLast: Boolean): String {
    if (isLast) {
      // the last image prompt will be used to query multimodal models, so do not remove it for now
      return c
    }
    return removeImagePrompt(c)
  }

  private fun convertReqTokensAssistant(c: String): String {
    var isReasoning = false
    var isImageDescription = false
    var modified = false
    val lines = c.split("\n")
    val newLines = ArrayList<String>(lines.size)
    for (line in lines) {
      newLines.add(line)

      if (isReasoning) {
        if (line.startsWith("<summary>")) {
          newLines.removeLast()
        } else if (line.startsWith("> ")) {
          newLines.removeLast()
          val withoutPrefix = line.substring("> ".length)

          if (config.keepReasoningInPrompt) {
            newLines.add(withoutPrefix)
            continue
          }
          // do not keep reasoning in prompt, so we need to extract image description tags
          if (isImageDescription) {
            newLines.add(withoutPrefix)
            if (withoutPrefix == config.imageDescriptionStopTag) {
              isImageDescription = false
            }
          } else {
            if (withoutPrefix == config.imageDescriptionStartTag) {
              isImageDescription = true
              newLines.add(withoutPrefix)
            }
          }
        } else if (line == "</details>") {
          isReasoning = false
          if (isImageDescription) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "image description is not ended properly, but reasoning is stopped")
            isImageDescription = false
          }
          if (!config.keepReasoningInPrompt) {
            newLines.removeLast()
          }
        } else {
          Logger.warn(LogType.INVALID_EXTERNAL_DATA, "unexpected line for reasoning: should start with `> `, but got: $line")
        }
      } else {
        if (line.startsWith("<details type=\"reasoning\"")) {
          isReasoning = true
          modified = true
          newLines.removeLast()
          if (config.keepReasoningInPrompt) {
            newLines.add("<details type=\"reasoning\">")
          }
        }
      }
    }

    if (!modified) {
      return c
    }

    return newLines.joinToString("\n")
  }

  private fun removeLastImagePrompt(c: OpenAiApi.ChatCompletion) {
    val last = c.messages.last()
    if (last.role != Role.assistant) {
      removeImagePrompt(last)
    }
  }

  private fun removeImagePrompt(c: OpenAiApi.ChatCompletionMessage) {
    if (c.simpleContent != null) {
      c.simpleContent = removeImagePrompt(c.simpleContent)
    } else {
      for (cc in c.content) {
        if (cc.type == OpenAiApi.ChatCompletionContentType.text) {
          cc.text = removeImagePrompt(cc.text)
        }
      }
    }
  }

  private fun removeImagePrompt(msg: String): String {
    var c = msg
    val sb = StringBuilder()
    while (true) {
      var idx = c.indexOf(config.imagePromptStartTag)
      if (idx == -1) {
        sb.append(c)
        break
      }
      sb.append(c.substring(0, idx))
      c = c.substring(idx + config.imagePromptStartTag.length)
      idx = c.indexOf(config.imagePromptStopTag)
      if (idx == -1) {
        Logger.error(LogType.INVALID_EXTERNAL_DATA, "got only image prompt start tag, but no stop tag: $msg")
        break
      }
      c = c.substring(idx + config.imagePromptStopTag.length)
    }
    return sb.toString()
  }

  private fun convertRespTokens(c: OpenAiApi.CompletionResponse) {
    for (ch in c.choices) {
      val msg = if (ch.message == null) ch.delta else ch.message
      if (msg == null) {
        // both message and delta are null
        continue
      }
      if (msg.content == null) {
        continue
      }
      msg.content = convertRespTokens(msg.content)
    }
  }

  private fun convertRespTokens(c: String): String = c // nothing to be done for now

  private suspend fun processReq(ctx: RoutingContext, c: OpenAiApi.ChatCompletion) {
    @Suppress("NAME_SHADOWING") var c = c
    val last = c.messages.last()
    val needToHandleImage = (last.role != Role.assistant && last.content != null &&
        last.content.any { it.type == OpenAiApi.ChatCompletionContentType.image_url })
    var resp: HttpServerResponse? = null
    if (needToHandleImage) {
      val (r, cc) = handleImage(ctx, c)
      resp = r
      c = cc
    } else {
      c = buildNoImageChatCompletion(c)
    }
    handleChat(ctx, c, resp)
  }

  private suspend fun handleImage(
    ctx: RoutingContext,
    c: OpenAiApi.ChatCompletion
  ): Pair<HttpServerResponse?, OpenAiApi.ChatCompletion> {
    val noImageChat = buildNoImageChatCompletion(c)

    val noImageResult = Pair(null, noImageChat)
    val last = c.messages.last()
    if (last.role == Role.assistant) {
      return noImageResult
    }
    if (last.content == null) {
      return noImageResult
    }
    val images = last.content.filter { it.type == OpenAiApi.ChatCompletionContentType.image_url }
    if (images.isEmpty()) {
      return noImageResult
    }
    var imageUserPrompt = last.content.filter { it.type == OpenAiApi.ChatCompletionContentType.text }.map { it.text }.joinToString("\n")
    val promptStartTagIdx = imageUserPrompt.indexOf(config.imagePromptStartTag)
    val promptStopTagIdx = imageUserPrompt.lastIndexOf(config.imagePromptStopTag)
    if (promptStartTagIdx == -1 || promptStopTagIdx == -1 || promptStartTagIdx + config.imagePromptStartTag.length >= promptStopTagIdx) {
      imageUserPrompt = config.imageToDescPrompt
    } else {
      imageUserPrompt = imageUserPrompt.substring(promptStartTagIdx + config.imagePromptStartTag.length, promptStopTagIdx)
    }

    val model = config.getMultimodal()
      ?: throw XException("no multimodal model specified")
    val multimodalConnector = model.servers.next(null)
      ?: throw XException("no available multimodal server")
    Objects.requireNonNull(multimodalConnector.data)
    val meta = multimodalConnector.data as ModelServerMetadata

    val imagePromptTemplate = OpenAiApi.ChatCompletion()
    imagePromptTemplate.stream = c.stream
    imagePromptTemplate.model = model.name
    val imageMsgTemplate = OpenAiApi.ChatCompletionMessage()
    imageMsgTemplate.role = Role.system
    imageMsgTemplate.content = ArrayList()
    val imageDescPromptContent = OpenAiApi.ChatCompletionContent()
    imageDescPromptContent.type = OpenAiApi.ChatCompletionContentType.text
    imageDescPromptContent.text = imageUserPrompt
    imageMsgTemplate.content.add(images[0]) // the first element is the image
    imageMsgTemplate.content.add(imageDescPromptContent)
    imagePromptTemplate.messages = listOf(imageMsgTemplate)

    if (meta.type == ModelServerType.OLLAMA) {
      if (c.stream) {
        val resp = sendStreamingResponse(ctx)
        var content: String
        var result = ""

        content = config.reasoningTag + "\n" + config.imageDescriptionStartTag + "\n"
        // do not add reasoning tag to result
        result += config.imageDescriptionStartTag + "\n"
        respondStreamChunk(ctx, content)
        ctx.get(ReqContext.KEY)!!.reasoningTagResponded = true

        for ((i, img) in images.withIndex()) {
          if (i > 0) {
            content = "\n---\n\n"
            result += content
            respondStreamChunk(ctx, content)
          }

          content = config.imageDescriptionHeadResponseTemplate
            .replace("{{ n }}", "${i + 1}")
            .replace("{{ total }}", "${images.size}") + "\n"
          result += content
          respondStreamChunk(ctx, content)

          imageMsgTemplate.content.removeFirst()
          imageMsgTemplate.content.addFirst(img)

          val proxyResult =
            proxyStreamRequestToOllama(ctx, imagePromptTemplate, model, multimodalConnector, meta, resp, terminatesRequest = false)
          Objects.requireNonNull(proxyResult)
          proxyResult!!
          result += proxyResult

          if (!proxyResult.endsWith("\n") && !proxyResult.endsWith("\r\n")) {
            content = "\n"
            result += content
            respondStreamChunk(ctx, content)
          }
        }
        content = "\n---\n\n" + config.imageAdditionalResponse + "\n"
        result += content
        respondStreamChunk(ctx, content)

        content = config.imageDescriptionStopTag + "\n"
        result += content
        respondStreamChunk(ctx, content)

        content = config.endReasoningTag + "\n"
        // do not add endReasoning tag to result
        if (config.reasoning != null) { // has reasoning model
          content = "\n---\n\n" // markdown separator
        }
        // if no reasoning model, the end reasoning tag would be responded
        // otherwise, the markdown separator would be responded
        respondStreamChunk(ctx, content)

        val msg = OpenAiApi.ChatCompletionMessage()
        msg.role = Role.assistant
        msg.simpleContent = result
        noImageChat.messages.add(msg)

        return Pair(resp, noImageChat)
      } else {
        TODO("non stream ollama multimodal req")
      }
    } else {
      TODO("openai multimodal req")
    }
  }

  private suspend fun respondStreamChunk(ctx: RoutingContext, content: String) {
    val chunk = Chunk()
    chunk.content = ByteArray.from(
      "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":${
        SimpleString(content).stringify()
      },\"role\":\"assistant\"}}],\"created\":${
        System.currentTimeMillis() / 1000
      },\"model\":${
        SimpleString(config.name).stringify()
      }}\r\n"
    )
    ctx.conn.coconn().write(chunk.toByteArray())
    if (config.printOutputResponse) {
      print(content)
    }
  }

  private fun buildNoImageChatCompletion(c: OpenAiApi.ChatCompletion): OpenAiApi.ChatCompletion {
    @Suppress("NAME_SHADOWING") val c = c.copy()
    removeLastImagePrompt(c)
    val ls = ArrayList<OpenAiApi.ChatCompletionMessage>()
    for (msg in c.messages) {
      if (msg.simpleContent != null) {
        ls.add(msg)
        continue
      }
      msg.content = msg.content.filter { it.type != OpenAiApi.ChatCompletionContentType.image_url }
      if (msg.content.isEmpty()) {
        continue
      }
      if (msg.content.size == 1) {
        msg.simpleContent = msg.content[0].text
      } else {
        msg.simpleContent = msg.content.joinToString("\n")
      }
      msg.content = null
      ls.add(msg)
    }
    c.messages = ls
    return c
  }

  private suspend fun handleChat(ctx: RoutingContext, c: OpenAiApi.ChatCompletion, resp: HttpServerResponse?) {
    if (modelContentPredicate(c, ::isTitleGeneration) || modelContentPredicate(c, ::isTagsGeneration)) {
      if (modelContentPredicate(c) { it.length > config.generateTitleOrTagsPromptLengthThreshold }) {
        throw XException("content too long")
      }
    }
    val model = chooseChatModel(c)
    sendRequestToLLMApi(ctx, c, model, resp)
  }

  private fun chooseChatModel(c: OpenAiApi.ChatCompletion): Config.Model {
    if (modelContentPredicate(c, ::useTextModel)) {
      return config.getText()
    }
    return config.getReasoning()
  }

  private fun modelContentPredicate(c: OpenAiApi.ChatCompletion, predicate: (String) -> Boolean): Boolean {
    for (msg in c.messages) {
      if (msg.simpleContent != null) {
        if (predicate(msg.simpleContent)) {
          return true
        }
      } else {
        for (cc in msg.content) {
          if (cc.type == OpenAiApi.ChatCompletionContentType.text) {
            if (predicate(cc.text)) {
              return true
            }
          }
        }
      }
    }
    return false
  }

  private fun useTextModel(content: String): Boolean {
    if (isTitleGeneration(content)) {
      return true
    }
    if (isTagsGeneration(content)) {
      return true
    }
    return false
  }

  private fun isTitleGeneration(content: String): Boolean {
    return content.contains(config.titleGenerationMatching)
  }

  private fun isTagsGeneration(content: String): Boolean {
    return content.contains(config.tagsGenerationMatching)
  }

  private suspend fun sendStreamingResponse(ctx: RoutingContext): HttpServerResponse {
    val resp = ctx.conn.response(200)
    resp.header("Content-Type", "text/event-stream")
      .sendHeadersBeforeChunks()
    if (config.printOutputResponse) {
      Logger.alert(config.printResponseSymbol + " Completion")
    }
    return resp
  }

  private suspend fun sendRequestToLLMApi(
    ctx: RoutingContext,
    c: OpenAiApi.ChatCompletion, model: Config.Model,
    resp: HttpServerResponse?
  ) {
    val connector = model.servers.next(IPPort.bindAnyAddress()) ?: throw XException("no healthy LLM api for ${model.name}")
    Objects.requireNonNull(connector.data)

    val meta = connector.data as ModelServerMetadata
    if (meta.type == ModelServerType.OPENAI) {
      if (c.stream) {
        proxyStreamRequestToOpenAI(ctx, c, model, connector, meta, resp)
      } else {
        proxyNonStreamRequestToOpenAI(ctx, c, model, connector)
      }
    } else {
      if (c.stream) {
        proxyStreamRequestToOllama(ctx, c, model, connector, meta, resp)
      } else {
        proxyNonStreamRequestToOllama(ctx, c, model, connector, meta)
      }
    }
  }

  private fun stripReasoningTagIfRequired(ctx: RoutingContext, c: OpenAiApi.CompletionResponse) {
    if (!ctx.get(ReqContext.KEY)!!.reasoningTagResponded) {
      return
    }
    val content = c.choices[0].delta.content
    if (content.startsWith(config.reasoningTag + "\n")) {
      c.choices[0].delta.content = content.substring(config.reasoningTag.length + 1)
    } else if (content.startsWith(config.reasoningTag)) {
      c.choices[0].delta.content = content.substring(config.reasoningTag.length)
    }
  }

  private suspend fun proxyStreamRequestToOpenAI(
    ctx: RoutingContext,
    c: OpenAiApi.ChatCompletion, model: Config.Model, connector: SvrHandleConnector, meta: ModelServerMetadata,
    resp: HttpServerResponse?
  ) {
    @Suppress("NAME_SHADOWING") var resp = resp

    val conn = connector.connect(ConnectionOpts.getDefault(), RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384))
    val coconn = CoroutineConnection(el, conn)
    var isDone = false
    try {
      coconn.connect()

      val httpconn = CoroutineHttp1ClientConnection(coconn)
      c.model = model.name
      httpconn.post("/v1/chat/completions").addHostHeader().header("Content-Type", "application/json").send(c)

      val parser = HttpRespParser(HttpRespParser.Params().setSegmentedParsing(true))
      var isFirstResponse = true
      while (true) {
        coconn.read(parser, alwaysRaiseEOF = true)
        val res = parser.builder
        when (parser.state) {
          HttpParserHelper.STATE_END_ALL_HEADERS -> {
            if (res.statusCode.toString() != "200") {
              throw XException("response status code is not 200: ${res.statusCode}")
            }
            val transferEncoding = res.headers.firstOrNull { it.key.toString().equals("Transfer-Encoding", ignoreCase = true) }
              ?: throw XException("response doesn't contain header Transfer-Encoding")
            if (transferEncoding.value.toString() != "chunked") {
              throw XException("response Transfer-Encoding is not chunked: $transferEncoding")
            }

            if (resp == null) {
              resp = sendStreamingResponse(ctx)
            }
          }

          HttpParserHelper.STATE_CHUNK_CONTENT -> {
            res.chunk.content = coconn.read(res.dataLength)
            var s = res.chunk.content.toString()
            if (!s.startsWith("data:")) {
              throw XException("chunked response does not start with `data:`")
            }
            s = s.substring("data:".length).trim()
            if (s != "[DONE]") {
              val r = JSON.deserialize(s, OpenAiApi.CompletionResponse.rule)
              if (isFirstResponse) {
                isFirstResponse = false
                stripReasoningTagIfRequired(ctx, r)
              }
              convertRespTokens(r)
              val chunk = res.chunk.build()
              chunk.size = 0
              chunk.content = ByteArray.from("data: " + r.toJson().stringify() + "\r\n")
              ctx.conn.coconn().write(chunk.toByteArray())

              if (r.choices.isNotEmpty() && config.printOutputResponse && r.choices[0].delta.content != null) {
                print(r.choices[0].delta.content)
              }
            } else {
              ctx.conn.coconn().write(res.chunk.build().toByteArray())

              if (config.printOutputResponse) {
                println()
                Logger.alert("${config.printResponseSymbol} END")
              }
            }
          }

          HttpParserHelper.STATE_END_ALL_TRAILERS -> {
            resp!!.endChunks(listOf())
            isDone = true
            break
          }
        }
      }
    } catch (t: Throwable) {
      Logger.error(LogType.ALERT, "failed to handle request", t)
      throw t
    } finally {
      if (!isDone && meta.nonStopping) {
        val id = UUID.randomUUID().toString()
        println()
        Logger.warn(LogType.ALERT, "${coconn.remote()} ${meta.type} is still responding tokens, tracking_id=${id}")
        try {
          while (coconn.read() != null) {
            conn.inBuffer.clear()
          }
        } catch (e: Exception) {
          Logger.error(LogType.SOCKET_ERROR, "failed to read from ${coconn.remote()} $id", e)
        }
        Logger.warn(LogType.ALERT, "${coconn.remote()} ${meta.type} $id connection closed")
      }
      coconn.close()
    }
  }

  private suspend fun sendNonStreamRequestToOpenAI(
    c: OpenAiApi.ChatCompletion, model: Config.Model, connector: SvrHandleConnector
  ): OpenAiApi.CompletionResponse {
    return proxyNonStreamRequestToOpenAI(null, c, model, connector)!!
  }

  private suspend fun proxyNonStreamRequestToOpenAI(
    ctx: RoutingContext?, c: OpenAiApi.ChatCompletion, model: Config.Model, connector: SvrHandleConnector,
  ): OpenAiApi.CompletionResponse? {
    val conn = connector.connect(ConnectionOpts.getDefault(), RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384))
    CoroutineConnection(el, conn).use { coconn ->
      coconn.connect()

      val httpconn = CoroutineHttp1ClientConnection(coconn)
      c.model = model.name
      httpconn.post("/v1/chat/completions").addHostHeader().header("Content-Type", "application/json").send(c)
      val r = httpconn.readResponse()
      val body = JSON.deserialize(r.body.toString(), OpenAiApi.CompletionResponse.rule)
      if (ctx == null) {
        return body
      } else {
        convertRespTokens(body)
        ctx.conn.response(r.statusCode).header("Content-Type", "application/json").send(body)

        if (config.printOutputResponse) {
          body.printToStdout(config)
        }
        return null
      }
    }
  }

  private suspend fun proxyStreamRequestToOllama(
    ctx: RoutingContext,
    c: OpenAiApi.ChatCompletion, model: Config.Model, connector: SvrHandleConnector, meta: ModelServerMetadata,
    resp: HttpServerResponse?, terminatesRequest: Boolean = true
  ): String? {
    @Suppress("NAME_SHADOWING") var resp = resp

    val conn = connector.connect(ConnectionOpts.getDefault(), RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384))
    val coconn = CoroutineConnection(el, conn)
    var isDone = false
    try {
      coconn.connect()

      val httpconn = CoroutineHttp1ClientConnection(coconn)
      c.model = model.name
      httpconn.post("/api/chat").addHostHeader().header("Content-Type", "application/json")
        .send(c.toOllamaChatRequest(meta.ollamaOptions))

      val parser = HttpRespParser(HttpRespParser.Params().setSegmentedParsing(true))
      var result = ""
      var isFirstResponse = true
      while (true) {
        coconn.read(parser, alwaysRaiseEOF = true)
        val res = parser.builder
        when (parser.state) {
          HttpParserHelper.STATE_END_ALL_HEADERS -> {
            if (res.statusCode.toString() != "200") {
              throw XException("response status code is not 200: ${res.statusCode}")
            }
            val transferEncoding = res.headers.firstOrNull { it.key.toString().equals("Transfer-Encoding", ignoreCase = true) }
              ?: throw XException("response doesn't contain header Transfer-Encoding")
            if (transferEncoding.value.toString() != "chunked") {
              throw XException("response Transfer-Encoding is not chunked: $transferEncoding")
            }

            if (resp == null) {
              resp = sendStreamingResponse(ctx)
            }
          }

          HttpParserHelper.STATE_CHUNK_CONTENT -> {
            val data = coconn.read(res.dataLength).toString()
            val ollamaChunk = JSON.deserialize(data, OllamaApi.OllamaChatResponse.rule)
            if (ollamaChunk.error != null) {
              throw XException("received error from ollama: ${ollamaChunk.error.message}")
            }
            if (!terminatesRequest) {
              result += ollamaChunk.message.content
            }
            val chunk = Chunk()
            val r = ollamaChunk.toOpenAICompletionResponse(true)
            if (isFirstResponse) {
              isFirstResponse = false
              stripReasoningTagIfRequired(ctx, r)
            }
            chunk.content = ByteArray.from("data: " + r.toJson().stringify() + "\r\n")
            ctx.conn.coconn().write(chunk.toByteArray())

            if (config.printOutputResponse && ollamaChunk.message.content != null) {
              print(ollamaChunk.message.content)
            }
          }

          HttpParserHelper.STATE_END_ALL_TRAILERS -> {
            if (terminatesRequest) {
              val chunk = Chunk()
              chunk.content = ByteArray.from("data: [DONE]\r\n")
              ctx.conn.coconn().write(chunk.toByteArray())
              resp!!.endChunks(listOf())

              if (config.printOutputResponse) {
                println()
                Logger.alert("${config.printResponseSymbol} END")
              }
              isDone = true
              return null
            } else {
              isDone = true
              return result
            }
          }
        }
      }
    } catch (t: Throwable) {
      Logger.error(LogType.ALERT, "failed to handle request", t)
      throw t
    } finally {
      if (!isDone && meta.nonStopping) {
        val id = UUID.randomUUID().toString()
        println()
        Logger.warn(LogType.ALERT, "${coconn.remote()} ${meta.type} is still responding tokens, tracking_id=${id}")
        try {
          while (coconn.read() != null) {
            conn.inBuffer.clear()
          }
        } catch (e: Exception) {
          Logger.error(LogType.SOCKET_ERROR, "failed to read from ${coconn.remote()} $id", e)
        }
        Logger.warn(LogType.ALERT, "${coconn.remote()} ${meta.type} $id connection closed")
      }
      coconn.close()
    }
  }

  private suspend fun sendNonStreamRequestToOllama(
    c: OpenAiApi.ChatCompletion, model: Config.Model, connector: SvrHandleConnector, meta: ModelServerMetadata
  ): OpenAiApi.CompletionResponse {
    return proxyNonStreamRequestToOllama(null, c, model, connector, meta)!!
  }

  private suspend fun proxyNonStreamRequestToOllama(
    ctx: RoutingContext?, c: OpenAiApi.ChatCompletion, model: Config.Model, connector: SvrHandleConnector, meta: ModelServerMetadata
  ): OpenAiApi.CompletionResponse? {
    val conn = connector.connect(ConnectionOpts.getDefault(), RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384))
    CoroutineConnection(el, conn).use { coconn ->
      coconn.connect()

      val httpconn = CoroutineHttp1ClientConnection(coconn)
      c.model = model.name
      httpconn.post("/api/chat").addHostHeader().header("Content-Type", "application/json")
        .send(c.toOllamaChatRequest(meta.ollamaOptions))
      val r = httpconn.readResponse()
      val ollamaResp = JSON.deserialize(r.body.toString(), OllamaApi.OllamaChatResponse.rule)
      if (ollamaResp.error != null) {
        throw XException("received error from ollama: ${ollamaResp.error.message}")
      }
      val body = ollamaResp.toOpenAICompletionResponse(false)
      if (ctx == null) {
        return body
      } else {
        convertRespTokens(body)
        ctx.conn.response(r.statusCode).header("Content-Type", "application/json").send(body)

        if (config.printOutputResponse) {
          ollamaResp.printToStdout(config)
        }
        return null
      }
    }
  }
}
