# WebDAV Provider Config

WebDAV 同步只读写一个文件：

```text
chat-ai-provider-config.json
```

这个文件只保存供应商 API、API 地址、当前模型标识符和模型备注，不保存聊天记录。示例文件见：

```text
docs/webdav-provider-config.example.json
```

## 字段

顶层字段：

- `version`: 当前为 `1`。
- `updatedAt`: Unix 秒级时间戳，手工编辑时可以不改。
- `platforms`: 供应商数组。

供应商字段：

- `uid`: 已存在供应商的唯一 ID。手工新增时可以省略，应用会自动生成。
- `name`: 展示名称，例如 `千问`、`DeepSeek`。
- `compatibleType`: 必填，可选 `OPENAI`、`ANTHROPIC`、`DEEPSEEK`、`QWEN`、`CUSTOM`。
- `enabled`: 是否启用，默认 `true`。
- `apiUrl`: API 地址。
- `token`: API Key。可以留空或省略，但留空后该供应商无法正常请求。
- `model`: 当前默认模型标识符，不能为空。
- `modelPresets`: 聊天框模型切换里展示的模型列表，每项包含 `model` 和 `remark`。
- `systemPrompt`: 系统提示词，默认 `null`。
- `stream`: 是否流式输出，默认 `true`。
- `timeout`: 请求超时秒数，默认 `30`。

手工编辑时，`compatibleType` 支持大小写自动规范化，字段前后空格会被自动裁掉。`model` 不能为空，否则拉取时会报错，避免保存坏配置。

## 千问推荐配置

当前内置千问推荐模型使用阿里云百炼官方 ID：

- 日常使用：`qwen3.7-plus`
- 专业应用：`qwen3.7-max`

应用也兼容手工写成 `qwen-3.7-plus` 和 `qwen-3.7-max` 的旧配置，拉取和请求前会自动规范化为官方 ID。
