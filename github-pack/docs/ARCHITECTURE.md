# 架构说明

## 总体

百川是单 Activity 的 Compose 应用，采用 MVVM + 单向数据流。Gradle 为单模块，包边界严格分层：

```
com.tongzhouweiji.baichuan
├── app/                Application、依赖容器
├── core/               数据库、网络、安全、日志、备份、插件运行时、Agent
├── model/              统一模型适配层（OpenAI 兼容 / OP 预留）
├── chat/               会话、消息树、分支、提示词组装
├── ui/                 主题、导航、各功能页面与组件
└── （feature 包）      记忆、搜索、备份、插件、MCP、Agent
```

约束：`core` 不依赖任何 feature；feature 之间通过接口与依赖容器通信；聊天核心不感知具体供应商。

## 数据流

1. UI 通过 ViewModel 读取 StateFlow 状态。
2. ViewModel 调用 ChatRepository / DAO 更新数据库。
3. Room 的 Flow 触发界面刷新（消息、分支、附件）。
4. 模型请求经 `ModelAdapter` 统一发出，供应商差异在适配层转换。

## 数据库

- Room 单库 `baichuan.db`，当前版本 v6。
- 显式迁移：v1→v2→v3→v4→v5→v6，覆盖思考链、附件、备份、插件、Agent。
- 兜底：无法迁移的旧库会清库重建，保证应用可启动（开发期策略）。
- 关键表：agents、conversations、messages（消息树）、providers、models、memories、error_events、plugins、mcp_servers、agent_consents/agent_runs/agent_actions。

## 消息与分支

- `messages.parentId` 构成消息树，`conversations.activeLeafMessageId` 记录当前路径末端。
- 编辑/重新生成创建同级新消息，不覆盖原对话；界面可切换分支。
- 消息时间只作为元数据，不进入提示词前缀，避免破坏缓存。

## 模型适配

- `ModelAdapter` 统一接口：listModels、testConnection、chat、streamChat。
- OpenAI 兼容适配器处理 SSE 流、reasoning_content 思考链、tools 工具调用。
- OP 协议仅占位，调用明确报「暂不可用」。
- API Key 仅在内存中出现，落库前经 Keystore 加密。

## 插件（未济接口）

- 插件包为 ZIP，manifest 声明 id/版本/API 版本/权限/类型。
- 安装校验 → 解包到私有目录 → 记录权限 → 启用时用户确认。
- 配置型插件可提供主题与角色卡预设；代码型插件不执行。
- 插件数据与核心库隔离。

## MCP

- 服务器登记 + 连接状态 + 工具清单缓存（initialize / tools.list）。
- 凭据加密，调用记录只存摘要。

## Agent（实验性）

- 按智能体授权：`agent_consents.agentId`，撤销即删除授权。
- 免责说明模态 + 10 秒门槛。
- Tool Calling 闭环：工具定义随请求下发 → 模型返回 tool_calls → 服务层校验授权并真实执行 → 结果作为 tool 消息回传 → 循环直至最终回复。
- 每次运行写 `agent_runs`，每次工具调用写 `agent_actions`（参数/结果脱敏）。
