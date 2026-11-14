# UI 基线与整改对照（v0.3.0）

> 依据：`docs/视觉规范.md`、《UI 设计与交互专项报告 v0.2》、UI 工程 Skill。
> 本文件是 UI 整改的基线记录，不是新的视觉规范。

## 1. 基线信息

- 起始 commit：`7aecd24`（v0.2.0）
- 当前版本：0.3.0（进行中）
- 构建：`assembleDebug`（本轮 TODO：重新构建）
- 真机截图：**NOT RUN**（需发起者提供或设备验证）

## 2. 颜色 token 对照

| Token | 视觉规范（浅/深） | 代码修正前 | 代码修正后 | 状态 |
|---|---|---|---|---|
| brand | `#2F5BEA` / `#7C93FF` | `#2F5BEA` / `#2F5BEA` | `#2F5BEA` / `#7C93FF` | 已修 |
| bg（深色） | `#0F1115` | `#111111` | `#0F1115` | 已修 |
| surface（深色） | `#191C22` | `#191919` | `#191C22` | 已修 |
| divider（深色） | `#262A31` | `#2A2A2A` | `#262A31` | 已修 |
| ds_user_bubble（浅） | `#F2F3F5` | `#E8EEFB` | `#F2F3F5` | 已修 |
| ds_user_bubble（深） | `#26282C` | `#2A2A2A` | `#26282C` | 已修 |
| ds_line（浅） | `#E5E7EB`（规范 ds 分割线） | `#E5E5E5` | `#E5E7EB` | 已修 |
| ds_line（深） | `#262A31` | `#333333` | `#262A31` | 已修 |

未覆盖：`brand_container`、状态色、scrim、glass 配方等，后续按页面需要补齐。

## 3. 主题模式

- 新增设置 `theme_mode = system | light | dark`（默认 system）
- 外观页新增「主题」分组，三项可选
- 原 `ui_style`（wechat/deepseek）与 `theme_mode` 独立存储

## 4. 已知 UI 问题状态

| 编号 | 问题 | 状态 |
|---|---|---|
| UI-01 | 风格 B 未进入选项 | 保持「规划中」文案，不放入选择项 |
| UI-02 | 主题 token 偏离规范 | 已修（见上表） |
| UI-03 | 无主题模式入口 | 已修（theme_mode） |
| UI-04 | 消息页搜索空操作 | 已修（移除未实现入口） |
| UI-05 | 流式时无条件滚动到底 | 已修（near-bottom 跟随，两种风格） |
| UI-06 | 列表行密度不统一 | 待修 |
| UI-07 | 列表图标全用主色 | 待修 |
| UI-08 | 图标未统一 Material Symbols | 待修（需图标子集） |
| UI-09 | 无障碍语义逐页验收 | 待修（NOT RUN） |
| UI-10 | 个人页固定占位文案 | 待修 |
| UI-11 | 两套聊天结构复用 | 待修（提取共享呈现组件） |

## 5. 验收矩阵（NOT RUN）

| 场景 | 状态 |
|---|---|
| A 微信式 + 浅色 | NOT RUN |
| A 微信式 + 深色 | NOT RUN |
| C DeepSeek 式 + 浅色 | NOT RUN |
| C DeepSeek 式 + 深色 | NOT RUN |
| 360dp 窄屏 | NOT RUN |
| 系统字号 1.3x / 2.0x | NOT RUN |
| TalkBack 走查 | NOT RUN |
| IME / 手势导航 / 三键导航 | NOT RUN |

## 6. 本轮改动清单

- `ui/theme/Color.kt`：深色 token 与 DeepSeek token 按规范修正
- `ui/theme/Theme.kt`：深色 primary 使用提亮川蓝
- `MainActivity.kt`：读取 `theme_mode` 应用主题
- `ui/screen/settings/AppearanceScreen.kt`：新增主题模式选项
- `ui/screen/messages/MessagesScreen.kt`：移除空操作搜索按钮
- `ui/screen/chat/ChatScreen.kt`、`DeepSeekChat.kt`：near-bottom 滚动跟随

## 7. 下一步

1. 列表行密度与图标层级（UI-06/07）
2. 个人页真实状态文案（UI-10）
3. 聊天 Composer 状态机与活动条（UI-2）
4. C 风格长文本排版（UI-3）
5. 截图与真机验收（需设备）
