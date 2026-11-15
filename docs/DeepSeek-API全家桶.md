# DeepSeek API 全家桶接入说明

> 依据：DeepSeek 官方文档（2026-11-13 全家桶应用 API 发布）
> - 发布公告：https://api-docs.deepseek.com/zh-cn/news/news261113/
> - 构建 Chatbot：https://api-docs.deepseek.com/zh-cn/guides/build_a_chatbot
> - 联网工具：https://api-docs.deepseek.com/zh-cn/guides/web_tools
> - TTS：https://api-docs.deepseek.com/zh-cn/guides/tts
> - 服务协议：https://cdn.deepseek.com/policies/zh-CN/deepseek-open-platform-terms-of-service.html
>
> 本文只记录接口事实与百川的适配计划，不代表百川与深度求索存在合作或授权关系。

## 1. 接口清单

| 能力 | 接口/参数 | 说明 |
|---|---|---|
| 联网搜索（非思考） | Chat Completions 声明 `tools: [{"type":"deepseek_web_search","name":"deepseek_web_search"}]` | 模型自动搜索并带引用作答，服务端完成 |
| 深度思考搜索 | 同上 + 开启思考 | 多步推理中自主搜索、打开网页、页内查找，最多约 20 次工具调用 |
| 阅读链接 | 声明 `deepseek_read_url` | 识别最后一条用户消息中的 URL，抓取网页作答 |
| 模型内容安全 | `safety_check: {"level": "high"}` | 服务端审核输入输出，命中返回 `refusal`/`Content Exists Risk` |
| 会话标题 | `title: {"enabled": true}` | 回答结束时生成会话标题 |
| 文件输入 | Files API / 文件解析 | PDF、Office、图片解析后拼入上下文 |
| 语音合成 | `POST https://api.deepseek.com/v1/tools/tts` | 整段或 WebSocket 流式，音色 `stella` / `tide`，wav/pcm |
| 语音识别 | ASR 接口 | 语音转文字 |
| OCR | OCR 接口 | 图片文字识别 |
| 搜索/抓取独立接口 | `/v1/tools/*` | 不经过 Chat 直接调用 |

## 2. 关键约束

- 联网工具与 `safety_check`、`title` 同时使用时，`temperature`、`top_p`、`stop` 等服务端会忽略。
- `tool_choice` 不能指定 `deepseek_web_search` / `deepseek_read_url`（返回 400）；`none` 关闭全部工具。
- 多轮对话必须把上一轮 assistant 的响应块原样回传，保留 `kind` 与 `signature`，否则阅读链接的网页不会拼回上下文。
- 使用联网工具后不能续写（400），只能重发上一次请求。
- 联网工具只能与 `deepseek-flash` 模型一起使用。
- 搜索结果、网页正文、文件内容均为不可信输入，必须作为数据传递，不能当系统指令执行。

## 3. 百川现状与差距

| 能力 | 百川现状 | 差距 |
|---|---|---|
| 联网搜索 | 使用旧 `POST /v1/tools/search` | 未使用 `deepseek_web_search` 工具与引用块；无 thinking 模式深度搜索 |
| 阅读链接 | 未实现 | 需要声明 `deepseek_read_url` 并回传 `signature` |
| 内容安全 | 未启用 `safety_check` | 对外服务场景需开启 |
| 会话标题 | 本地/模型生成 | 未用 `title` 参数 |
| 文件输入 | 仅图片多模态 | 无 PDF/Office 解析 |
| TTS | 已接入 DeepSeek TTS（wav 整段） | v0.4.0 修复播放失败可见、WAV 头与计费截断 |
| ASR / OCR | 未实现 | 需要独立接口 |

## 4. 优化计划

### 第一批（搜索与阅读链接）

1. ChatViewModel 在开启搜索时，于 ChatRequest 增加服务端工具声明（`deepseek_web_search` / `deepseek_read_url`）。
2. 适配层解析 `server_tool_use`、`web_search_tool_result`、`web_fetch_tool_result` 内容块，提取标题/URL/摘要作为引用展示。
3. 多轮对话保留块结构（或至少保留 citations 与 signature），避免阅读链接失效。
4. 流式解析支持 `citations_delta`，界面显示引用角标。
5. 思考模式 + 搜索 = 深度思考搜索，UI 明确标识。

### 第二批（内容安全与标题）

1. 设置项「内容安全校验」默认关闭，对外分发场景建议开启。
2. 处理 `refusal` / `Content Exists Risk`，显示明确拒答原因，不当作普通错误。
3. 可选开启 `title` 生成会话标题。

### 第三批（TTS / OCR / ASR / Files）

1. TTS：接入 `/v1/tools/tts`（wav 整段），保留系统 TTS 回退；设置项选择音色。
2. OCR：图片文字识别工具。
3. ASR：语音输入。
4. Files API：PDF/Office 上传解析。

## 5. 合规要点（来自 DeepSeek 服务协议）

- 百川是独立项目，与深度求索无隶属、合作或授权关系；文档与 UI 中不得暗示官方合作。
- 对外提供服务时应开启内容安全校验，并依法履行内容标识（AI 生成标识）等义务。
- API Key 由用户自行保管，不得共享或公开；百川不收集用户 Key。
- 输出内容由 AI 生成，可能不准确，需向终端用户明确披露。

## 6. 实施状态

- [x] 第一批：服务端工具声明（`deepseek_web_search` / `deepseek_read_url`）
- [x] 第一批：搜索来源展示（标题 + 链接，折叠活动条）
- [x] 第二批：`safety_check` 参数（设置项 `safety_level`）
- [x] 第三批：TTS 接入（v0.4.0 修复失败可见、WAV 头、计费字符截断）
- [ ] 第一批：服务端工具内容块的结构化解析（目前仍以普通文本 + 来源条呈现）
- [ ] 第三批：OCR / ASR / Files
