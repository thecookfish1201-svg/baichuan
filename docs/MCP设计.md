# MCP 连接管理设计（筹备稿）

> MCP（Model Context Protocol）负责连接外部工具与服务，与未济接口的权限和调用记录分开管理。
> 第一版：连接配置 + 工具清单展示 + 调用记录基础；实际调用在阶段五后半或阶段六接入。

## 1. 数据表

```sql
mcp_servers(
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  transport TEXT NOT NULL,        -- stdio / sse / http
  endpoint TEXT NOT NULL,         -- 命令或 URL
  headers_ref TEXT,               -- 加密的凭据引用
  enabled INTEGER NOT NULL,
  last_status TEXT,               -- connected / failed / unknown
  last_connected_at INTEGER,
  created_at INTEGER NOT NULL
)

mcp_tools(
  id INTEGER PRIMARY KEY,
  server_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  description TEXT,
  schema_json TEXT,               -- 工具入参 schema
  enabled INTEGER NOT NULL,
  FOREIGN KEY(server_id) REFERENCES mcp_servers(id) ON DELETE CASCADE
)

mcp_call_logs(
  id INTEGER PRIMARY KEY,
  server_id INTEGER NOT NULL,
  tool_name TEXT NOT NULL,
  arguments_digest TEXT,          -- 参数摘要（脱敏）
  result_digest TEXT,             -- 结果摘要（脱敏）
  success INTEGER NOT NULL,
  error_code TEXT,
  duration_ms INTEGER,
  created_at INTEGER NOT NULL
)
```

## 2. 权限与记录

- MCP 凭据用 `SecretStore` 加密，不落明文。
- 调用参数与结果只存摘要（截断 + 脱敏），避免把用户数据写进日志。
- 与未济接口分别管理授权：启用 MCP 服务器 ≠ 授权智能体使用其工具。
- 智能体级授权：`agent_mcp_grants(agent_id, server_id, tool_name, granted)`。

## 3. 连接流程

1. 用户添加服务器（名称、传输方式、地址/命令、凭据）。
2. 客户端连接 → 获取工具清单 → 缓存到 `mcp_tools`。
3. 展示工具列表，用户可为智能体勾选可用工具。
4. 调用时记录到 `mcp_call_logs`，失败给出明确错误码。

## 4. 第一版范围

- 连接配置与状态展示（连接/断开）。
- 工具清单读取与展示。
- 调用记录表结构就绪，实际调用在阶段六 Agent 工具调用基础设施中启用。
- 不在第一版实现 MCP 的采样/通知等高级能力。

## 5. 安全约束

- 外部工具调用必须经过用户确认（与 Agent 权限模型一致）。
- 高风险操作（文件系统、系统设置）不得由 MCP 工具直接执行，需要映射到受控工具接口。
- 不绕过 Android 系统授权机制。
