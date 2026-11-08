package com.tongzhouweiji.baichuan.core.plugin

/** 未济接口权限说明（展示给用户）。 */
object PluginPermissions {

    private val descriptions = mapOf(
        "theme.apply" to "应用主题（修改界面主色，不读取聊天内容）",
        "prompt.read" to "读取智能体提示词（需要你授权）",
        "prompt.write" to "写入人设/提示词预设（需要你授权）",
        "data.read" to "读取该插件自己的数据空间",
        "data.write" to "写入该插件自己的数据空间",
        "network" to "访问网络（需要你授权，并有调用记录）",
    )

    fun describe(permission: String): String =
        descriptions[permission] ?: "未知权限：$permission（建议拒绝）"

    fun known(permissions: List<String>): Boolean = permissions.all { it in descriptions }
}
