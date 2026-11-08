# 评审意见 · Agent 能力按智能体授权

- 评审：哦鲸鲸（计划与方案协作）
- 日期：2026-11-08
- 对象：阶段六 Agent 重构方案（consent 绑定智能体、授权弹窗、聊天页调用、运行记录）
- 依据：当前工作树代码、`docs/Agent安全设计.md`、`docs/计划.md` 9.6 节
- 结论先行：方向正确，可以按方案走；但工作树当前状态与方案、与计划.md 的记录不一致，先恢复构建，再补服务层闭环。

## 一、核对结论：方案与代码的差异

写评审前逐项核对了代码，方案里第 1、2 条已经落地，第 3、4 条只完成一半，另有一个会导致构建失败的文件没有清理。

**已完成：**

- 数据库已是 v6，`MIGRATION_5_6` 已执行 `ALTER TABLE agent_consents ADD COLUMN agentId INTEGER NOT NULL DEFAULT 0` 并建索引。
- `AgentConsentEntity` 已含 `agentId/docVersion/permissionLevel/acceptedAt`；`AgentRunDao` 已按 `agentId` 查询与删除。
- `AgentManager` 已改为 `isAuthorized(agentId)`、`levelOf(agentId)`、`authorize(agentId, level)`、`revoke(agentId)`。
- `AgentEditScreen` 已有"Agent 权限"区块：未授权文案、授予按钮、已授权等级、撤销二次确认。
- `AgentPermissionDialog` 已实现居中模态、遮罩、全文滚动、A/B 等级、10 秒倒计时、取消与确认。
- `ProfileScreen` 与 `BaichuanApp` 在 03:34 已移除"我的 → 实验性 Agent"的引用。

**未完成或有问题：**

1. `ChatScreen` 只有 `agentAuthorized` 和 `showAgentTask` 两个状态变量，没有任何地方把 `showAgentTask` 置为 true，顶栏和输入区都没有 Agent 入口。`agentAuthorized` 算出来之后也没有用于界面。
2. `ui/screen/agentlab/AgentLabScreen.kt` 还在，仍调用 `AgentManager.isEnabled()`、`permissionLevel()`、`enable()`、`disable()` 四个已经删掉的方法。全仓库没有其他文件引用它，但它仍在 source set 里，Kotlin 会编译，当前工作树构建会失败。构建产物 `app-debug.apk` 停在 03:18，早于 03:32 的改造。
3. `AgentTaskDialog` 虽然有 `agentId` 形参，但函数体里一次都没用。授权校验只存在于 UI 的显示逻辑，执行路径上没有任何检查。
4. `AgentTaskDialog` 执行后写 `AgentActionEntity(runId = 0)`，`agent_runs` 从来没有被插入过（全仓库没有调用 `insertRun`）。审计链条是断的。
5. `observeRuns()` 和 `observeActions()` 没有被任何界面使用，运行记录查看入口不存在。
6. `docs/计划.md` 9.6 节把"聊天页顶栏 Agent 入口"标成已完成，与代码不符。计划里写"顶栏入口"，方案里写"输入区按钮"，两处口径也不一致，需要先定。
7. 迁移只加了列，没有清理旧的全局授权记录。旧记录迁移后变成 `agentId = 0` 的孤儿，`latestConsent()` 仍能查到。

## 二、数据模型：consent 按 agentId 的方向对，但还差三样

按智能体授权是对的，比全局开关准确得多。但按当前字段，不够用。

**1. 撤销建议用 `revokedAt`，不要物理删除。**

理由有三个：

- 撤销授权是一次安全事件，将来要能回答"这个智能体什么时候授权、什么时候撤销、当时确认的是哪一版说明"。物理删除后这段历史没了。
- `agent_runs` 与 `agent_actions` 是长期保留的，但里面没有记录当时用的是哪条授权。保留 consent 才能对上。
- 发起者要求"授权/撤销"是明确操作，撤销留痕比静默删除更符合这个语义。

表结构建议：

```sql
agent_consents(
  id INTEGER PRIMARY KEY,
  agentId INTEGER NOT NULL,
  docVersion TEXT NOT NULL,
  permissionLevel TEXT NOT NULL,   -- A / B，建议枚举
  acceptedAt INTEGER NOT NULL,
  revokedAt INTEGER,               -- 新增，null 表示有效
  revokeReason TEXT,               -- 可选：user / agent_deleted / doc_updated
  FOREIGN KEY(agentId) REFERENCES agents(id) ON DELETE CASCADE
)
```

对应查询改成：

```kotlin
@Query("SELECT * FROM agent_consents WHERE agentId = :agentId AND revokedAt IS NULL ORDER BY acceptedAt DESC LIMIT 1")
suspend fun findActiveConsent(agentId: Long): AgentConsentEntity?

@Query("SELECT * FROM agent_consents WHERE agentId = :agentId AND revokedAt IS NULL ORDER BY acceptedAt DESC LIMIT 1")
fun observeActiveConsent(agentId: Long): Flow<AgentConsentEntity?>
```

授权与撤销都放在一个 `@Transaction` 里：先把该智能体所有 `revokedAt IS NULL` 的记录置为当前时间，再插入新记录。这样天然保证每个智能体只有一条有效授权，不依赖部分唯一索引（Room 对 `WHERE` 条件的部分索引支持不好，硬加会和 schema 校验打架）。

**2. 必须校验 `docVersion`。**

现在 `isAuthorized` 只看记录是否存在。免责说明更新后，旧记录仍然是"已授权"，这与设计稿"文档更新后需要重新阅读确认"冲突。建议：

```kotlin
suspend fun isAuthorized(agentId: Long): Boolean {
    val consent = dao.findActiveConsent(agentId) ?: return false
    if (consent.docVersion != DOC_VERSION) return false
    val agent = agentDao.findById(agentId) ?: return false
    return agent.deletedAt == null && agent.enabled && !agent.blocked
}
```

**3. 授权要跟智能体生命周期联动。**

`agent_consents` 加外键只能处理物理删除。百川的智能体是软删除，`deletedAt` 非空后 consent 仍然有效。另外智能体停用（`enabled = false`）、拉黑（`blocked = true`）之后也不应该还能调用 Agent。上面那段联查就是补这个口子。如果智能体被彻底删除，可以在删除流程里顺手把 consent 置 `revokedAt`，不依赖数据库级联。

**迁移的额外要求**：v5 到 v6 的 `ALTER TABLE` 之后，补一句 `DELETE FROM agent_consents WHERE agentId = 0`。旧的全局授权没法映射到具体智能体，保留下来只会变成幽灵授权。删除前可以在界面上提示一次"Agent 授权方式已改为按智能体授权，请重新授权"，不要静默。

**关于 `agentId NOT NULL DEFAULT 0`**：能跑，但 0 作为哨兵值容易和真实 id 混淆（SQLite 自增从 1 开始，暂时不会撞）。如果这次还要再改一版表，建议直接重建表、把 `agentId` 设为 NOT NULL 无默认值，把清理逻辑放进迁移，比长期留着一个 0 值干净。

## 三、聊天页调用落点：用消息卡片和底部面板，不要 Dialog

`AgentTaskDialog` 现在是一个 `Dialog`，覆盖整个聊天页，里面从目标输入到计划确认到执行结果全在这一个框里。这正是发起者说的"独立窗口"的变体：它虽然没有独立页面路由，但用户看到的是一个盖住聊天的模态框，任务结束就消失，和对话没有关系。

更稳妥的落点分三层：

**入口**：输入区 `+` 菜单里增加"Agent 任务"一项，和"图片""文件"并列。不要放顶栏，顶栏已经承担智能体名和返回，再塞功能入口会让聊天页变成工具箱。也不要在输入框旁边加一个常驻大按钮，会抢主聊天。

**计划确认**：用 `ModalBottomSheet` 承载，高度约屏高 60%，背景保留聊天内容可见。内容分三段：目标输入、计划步骤列表（每步显示工具名、参数摘要、风险等级）、底部"逐步执行"按钮。高风险步骤在列表里单独标色并要求逐条确认，不要把"全部执行"做成默认。

**执行结果**：作为一条消息卡片回到对话流，而不是留在弹窗里。消息表已经有 `contentType`，建议加一个 `agent_run` 类型，`content` 存 runId 或摘要，渲染成 `AgentRunCard`：标题显示目标，状态显示规划中/待确认/执行中/完成/失败/已取消，展开后是步骤与结果。这样任务天然属于这段对话，切换会话、重启应用后都还在。

**运行中**：输入区上方一条细状态条，显示当前步骤和"取消"按钮。任务结束自动收起。

对应组件与函数：

```kotlin
AgentTaskSheet(agentId: Long, conversationId: Long, onDismiss: () -> Unit)
AgentRunCard(message: MessageEntity, run: AgentRunEntity, actions: List<AgentActionEntity>)
AgentTaskBar(run: AgentRunEntity?, onOpen: () -> Unit, onCancel: () -> Unit)
```

数据层要配合：`agent_runs` 增加 `conversationId` 与 `messageId`，把运行记录和聊天绑定。现在 `AgentRunEntity` 只有 `agentId`，任务和对话是两条平行线。

如果坚持用 Dialog，至少要做两件事：把 `agentId` 真正传进 `AgentManager` 并在服务层校验；把每次执行写进一个真实的 run。否则这个对话框就是个绕过审计的执行入口。

## 四、10 秒门槛：现在的实现基本对，但四个细节会漏

现有 `AgentPermissionDialog` 的骨架是对的，问题都在边界上。

**1. 计时状态不能用 `remember` 重置。**
现在 `countdown = remember { 10 }` 加 `LaunchedEffect(Unit)`。在当前 `AgentEditScreen` 的场景里，弹窗从无到有会新建状态，问题不大。但只要以后在列表页对多个智能体连续授权、或者弹窗被复用，`countdown` 会保留上一次的 0，变成"打开即可确认"。必须在调用处用 `key(agentId, docVersion, requestId)` 包住，或者把 `requestId` 作为参数并写进 `LaunchedEffect` 的 key。

**2. 剩余时间要按截止时间算，不能靠累减。**
`while (countdown > 0) { delay(1000); countdown -= 1 }` 在协程被调度延迟时会漂移，10 次累减实际可能超过或不足 10 秒。建议保存 `deadline = SystemClock.elapsedRealtime() + 10_000` 到 `rememberSaveable`，用 `LaunchedEffect(deadline)` 每 200ms 刷新 `remaining = deadline - now`，配置变更（旋转、字体变化）后按 deadline 恢复，不会重新计时 10 秒。

**3. 只在页面可见时计时。**
`LaunchedEffect` 在 Dialog 存活期间会一直跑。用户切到后台 10 秒再回来，按钮就可点了，这不符合"停留阅读"的意图。用 `LocalLifecycleOwner` 配合 `repeatOnLifecycle(Lifecycle.State.RESUMED)` 包住计时循环，离开 `RESUMED` 时暂停，回来继续。

**4. 确认要防双击、要能失败。**
`onConfirm` 里先置 `submitting = true` 禁用按钮，写库成功再关闭；写库失败保留弹窗并提示。现在 `AgentEditScreen` 里是 `authorize()` 之后直接置状态、关弹窗，异常时协程崩掉，用户看不到原因。

**测试方式**：把倒计时抽成纯状态类，或者给弹窗注入 `now: () -> Long`。Compose 测试里用 `mainClock.autoAdvance = false` 加 `advanceTimeBy`，断言 9.9 秒时 `enabled == false`、10.1 秒时 `enabled == true`。不要写一个真的 sleep 10 秒的测试。

**文案与资源**：免责说明原文放在 `assets/agent_disclaimer.txt`（1785 字节，含署名和日期），弹窗要完整展示、可滚动、不截断。版本号 `DOC_VERSION` 与资源内容要对得上，建议加一条单元测试，断言资源里包含版本日期，避免以后改了文案忘了改版本。

## 五、未授权状态：锁定态，不要隐藏

发起者要求"检查未授权状态"，隐藏按钮做不到这一点。建议：

- 输入区 `+` 菜单里的"Agent 任务"常驻。未授权时置灰、带锁图标、副标题写"该智能体未授权"，点击不执行任何 Agent 逻辑，只弹一个说明框：一句话解释 Agent 是实验能力、需要在智能体设置里授权，下面给"去授权"按钮跳到 `AgentEditScreen` 的 Agent 权限区块。
- 已授权时正常可点，副标题显示当前等级（A 或 B）。
- 聊天页历史里已有的任务卡片保持只读可查看，未授权时不能发起新任务，也不隐藏旧记录。
- 智能体列表里给已授权的智能体加一个标签，便于一眼检查。
- 状态用 Flow 而不是一次性查询：`LaunchedEffect(agentId)` 只在进入页面时算一次，撤销授权后回到聊天页不会刷新。改成 `agentManager.observeAuthorization(agentId).collectAsState(initial = null)`，撤销后菜单项立刻变灰。

服务层必须双保险：`plan`、`startRun`、`execute` 三个入口都校验 `isAuthorized(agentId)` 和等级。UI 的锁定态是提示，不是安全边界。

## 六、安全漏洞与遗漏

**P0，必须先修：**

1. **授权校验不在服务层。** `AgentTaskDialog` 的 `agentId` 形参没使用，`AgentManager.execute(call)` 也不接收 `agentId`。现在能拦住未授权调用的只有"界面上没有入口"，而入口马上要加。所有方法签名都要带 `agentId`，内部先 `requireAuthorized`。
2. **审计链条断裂。** `agent_runs` 从未插入，`agent_actions.runId` 固定为 0。正确顺序是 `startRun` 先建 run（state=running），每次确认执行写 action（带真实 runId），结束更新 run（done/cancelled/failed）。运行记录页要能按智能体、按时间查到。
3. **计划不过滤未注册工具。** `plan()` 把模型输出的 `tool` 字段直接透传，`execute()` 才在注册表里找不到时抛异常。模型输出是不可信数据，应该在 `plan()` 里就 `AgentToolRegistry.find(tool) == null` 的步骤直接丢弃并计数提示；同时限制步数（建议 8 步以内）和单步参数大小。
4. **等级 A/B 形同虚设。** 现有三个工具都是 low，A 级也能执行，B 级也没有额外能力或确认要求。工具定义里要加 `minLevel`，执行前比较等级；没有 B 级工具时，B 级授权应在 UI 上说明"当前无可用的系统级工具"，而不是让用户以为 B 级更强。
5. **撤销不中断运行中的任务。** 设计要求"撤销后立即停止新任务，运行中任务标记为已取消"。现在 `revoke` 只删记录，没有取消逻辑。需要 `AgentManager.cancel(runId, reason)` 和 UI 的取消按钮。
6. **免责版本不校验。** 见第二节第 2 点。文档更新后必须重新确认。
7. **智能体删除/停用后授权仍有效。** 见第二节第 3 点。

**P1，本轮内处理：**

8. 旧全局授权 `agentId = 0` 的孤儿记录没有清理，`latestConsent()` 还在。
9. `plan()` 用默认模型而不是该智能体的模型，`agent_runs` 也没记录用了哪个模型，出问题时无法复现。
10. 用户目标直接拼进规划提示词，存在提示词注入风险，模型可能输出"忽略以上指令"之类的计划。约束方式：工具白名单、步数上限、执行前逐条展示、参数只允许 JSON 标量，不做自由文本拼接。
11. `argsSummary` / `resultSummary` 直接 `take(200/300)`，没有统一脱敏。参数来自模型输出，可能带入用户目标里的敏感内容，建议过一遍 `Redactor`。
12. `AgentTaskDialog` 关闭即丢状态，没有落库，重启后什么都查不到。改成 run 落库加卡片消息后自然解决。
13. `AgentPermissionDialog` 的倒计时存在配置变更重置、切后台继续、复用不重置三个边界，见第四节。

**P0 交付风险：**

14. `AgentLabScreen.kt` 是孤儿文件且调用已删除的方法，当前工作树构建会失败。两个选择：直接删除；或者改造成运行记录页（从智能体设置进入，不用独立导航入口）。无论哪种，先做，恢复可构建状态。

## 七、建议实施顺序与验收

1. **恢复构建（当天）**：处理 `AgentLabScreen.kt`，确认 `assembleDebug` 通过，APK 重新产出。
2. **服务层闭环（P0）**：所有 Agent 方法带 `agentId`，内部校验授权、等级、工具、版本；run 生命周期完整；取消逻辑；工具过滤与步数上限。
3. **数据层 v7**：`agent_consents` 加 `revokedAt/revokeReason`，`agent_runs` 加 `conversationId/messageId`，迁移清理 `agentId = 0`，补迁移测试。
4. **聊天页落点（P1）**：锁定态入口、`AgentTaskSheet`、`AgentRunCard`、状态条；`+` 菜单与官方风格输入条不打架。
5. **运行记录页（P1）**：从智能体设置进入，列该智能体的 runs 与 actions，只读，支持删除（删除前二次确认）。
6. **验收用例**：未授权时入口为锁定态且服务层拒绝；授权 10 秒内确认按钮不可点、取消不留记录；同一智能体重复授权只保留一条有效记录；撤销后入口变灰且运行中任务被标记取消；计划里出现未注册工具时被丢弃并提示；运行记录能按智能体查到完整步骤；免责版本更新后要求重新确认。

方案本身不用推倒重来，数据方向、弹窗形态、入口位置都对。真正要补的是三件事：把授权校验从 UI 挪到服务层，把 run 的生命周期补齐，把聊天页的调用从 Dialog 改成"卡片 + 底部面板"。这三件做完，再收尾入口和记录页，阶段六就能验收。
