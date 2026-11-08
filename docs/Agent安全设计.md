# 实验性 Agent 安全设计（筹备稿）

> Agent 是实验性功能，默认关闭，不属于核心聊天系统的必要依赖。
> 本文是阶段六实施前的设计稿，核心原则：受控工具、用户确认、可撤销、可审计。

## 1. 权限等级

| 等级 | 名称 | 能力范围 |
|---|---|---|
| A | 仅操控 APP | 只能通过百川内部受控接口操作应用自身（发消息、切换页面、读写记忆等），不能触达系统 |
| B | 授权系统操作 | 在 A 的基础上，通过 Android 系统授权访问文件、通知、无障碍等能力；**不得解释为无限制系统访问** |

- 等级由用户在启用时选择，随时可撤销。
- 高风险操作（删除数据、发送消息、支付、文件覆盖）必须逐次确认，不允许 Agent 自行批准。
- Agent 不得通过普通插件获得额外系统权限。

## 2. 工具调用接口（受控）

Agent 不直接执行命令，只能调用注册在 `ToolProvider` 中的受控工具：

```kotlin
interface AgentTool {
    val id: String
    val title: String
    val risk: RiskLevel        // LOW / MEDIUM / HIGH
    suspend fun execute(input: JsonObject): ToolResult
}
```

- 工具清单由百川核心维护，插件与 MCP 工具需要映射为受控工具后才能被 Agent 使用。
- 每个工具声明必填参数、风险等级与是否需要确认。
- 不提供任意 shell、任意 HTTP、任意文件路径的通用工具。

## 3. 确认流程

1. **执行前说明**：展示任务目标、将调用的工具、影响范围与撤销方式。
2. **操作确认**：高风险操作弹窗确认；确认记录写入 `agent_actions`。
3. **执行记录**：每次工具调用记录参数摘要、结果摘要、耗时与确认状态。
4. **取消任务**：运行中的任务可随时取消，已执行操作保留记录。
5. **错误处理**：工具失败不中断整个会话，记录错误并允许用户重试或放弃。
6. **权限撤销**：撤销后立即停止新任务，运行中任务标记为已取消。

## 4. 数据表

```sql
agent_consents(
  id INTEGER PRIMARY KEY,
  doc_version TEXT NOT NULL,     -- 免责说明版本
  accepted_at INTEGER NOT NULL
)

agent_runs(
  id INTEGER PRIMARY KEY,
  agent_id INTEGER NOT NULL,
  goal TEXT NOT NULL,
  permission_level TEXT NOT NULL,  -- A / B
  state TEXT NOT NULL,             -- running / done / cancelled / failed
  started_at INTEGER NOT NULL,
  ended_at INTEGER,
  cancel_reason TEXT
)

agent_actions(
  id INTEGER PRIMARY KEY,
  run_id INTEGER NOT NULL,
  tool_name TEXT NOT NULL,
  args_summary TEXT NOT NULL,      -- 脱敏摘要
  confirmed INTEGER NOT NULL,
  result_summary TEXT,
  created_at INTEGER NOT NULL
)
```

## 5. 免责说明阅读流程

- 首次启用 Agent 前展示项目方提供的《注意事项与免责说明》全文。
- 文本展示后停留至少 10 秒，确认按钮在 10 秒后才可点击。
- 该流程是阅读提示，不能代替实际权限控制。
- 免责说明更新后（版本变化）需要重新阅读确认。

## 6. 安全边界

- Agent 只能调用百川注册的受控工具，不提供不受限制的系统命令执行入口。
- 系统操作走 Android 原生权限（存储、通知、无障碍），不绕过系统授权。
- 工具参数与结果落库前脱敏，不保存 API Key、密码等敏感信息。
- Agent 运行记录可导出与删除，删除前二次确认。

## 7. 阶段六实施清单

1. 数据库 v5：agent_consents、agent_runs、agent_actions
2. 免责说明资源文件 + 10 秒阅读门槛
3. 权限模型（A/B）与启用流程
4. ToolProvider 与工具注册表（内置低风险工具）
5. 确认弹窗与执行记录 UI
6. 与 MCP 工具的映射（MCP 工具必须包装为受控工具）
