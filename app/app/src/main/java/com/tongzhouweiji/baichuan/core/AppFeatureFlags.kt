package com.tongzhouweiji.baichuan.core

/**
 * 版本级功能开关。与用户设置分离：这些是产品路线决定的能力边界，
 * 不提供给普通用户切换，也不允许旧数据库状态绕过。
 */
object AppFeatureFlags {

    /**
     * Agent 自主工具执行。
     *
     * v0.4.0 暂停：即使旧数据库存在授权记录、旧会话 `agentMode=true`，
     * 也不能启动工具调用。助手、人设、会话与旧数据仍完整保留。
     */
    const val agentExecutionEnabled: Boolean = false
}
