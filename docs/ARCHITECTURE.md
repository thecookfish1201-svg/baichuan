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

1. UI 通过 ViewModel 读取 StateFlow 状态；`ComposerState` 是唯一的任务状态事实源。
2. ViewModel 调用 ChatRepository / DAO 更新数据库；消息树完整性由 `MessageTree` 统一校验。
3. Room 的 Flow 触发界面刷新（消息、分支、附件）。
4. 模型请求经 `ModelAdapter` 统一发出，供应商差异在适配层转换。

## 数据库

- Room 单库 `baichuan.db`，当前版本 v6。
- 显式迁移：v1→v2→v3→v4→v5→v6，覆盖思考链、附件、备份、插件、Agent。
- 兜底：迁移失败不删库、不清数据；打开数据库失败时展示恢复界面并允许重试。
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

## MCP（v0.4.x 暂停执行）

- 服务器登记 + 连接状态 + 工具清单缓存（initialize / tools.list）的代码保留。
- v0.4.x 不向普通用户开放 MCP 工具执行，管理页不展示实验入口。

## Agent（v0.4.x 暂停执行）

- `AppFeatureFlags.agentExecutionEnabled=false`：服务层硬拦截，旧授权与旧 `agentMode` 都不能启动工具调用。
- 助手、人设、会话、授权记录、运行记录与备份数据保留；聊天页不再提供 Agent 模式入口。
- 未来重新开放前，需要重新评估权限模型、免责门槛与用户理解成本。
- 设计细节见 `docs/Agent安全设计.md`（历史设计，当前不开放）。
