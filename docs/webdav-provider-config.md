# WebDAV Provider Config

WebDAV 同步只读写一个文件：

```text
chat-ai-provider-config.json
```

这个文件只保存供应商 API 与模型标识符，不保存聊天记录。示例文件见：

```text
docs/webdav-provider-config.example.json
```

## 字段

顶层字段：

- `version`: 当前为 `1`。
- `updatedAt`: Unix 秒级时间戳，手工编辑时可以不改。
- `platforms`: 供应商数组。

供应商字段：

- `name`: 展示名称，例如 `千问`、`DeepSeek`。
- `compatibleType`: 必填，可选 `OPENAI`、`ANTHROPIC`、`DEEPSEEK`、`QWEN`、`CUSTOM`。
- `apiUrl`: API 地址。
- `token`: API Key，可以留空或省略，但留空后该供应商无法正常请求。
- `model`: 当前默认模型标识符。
- `modelPresets`: 聊天框模型切换里展示的模型列表，每项包含 `model` 和 `remark`。

可选字段：

- `uid`: 已存在供应商的唯一 ID。手工新增时可以省略，应用会自动生成。
- `enabled`: 是否启用，默认 `true`。
- `systemPrompt`: 系统提示词，默认 `null`。
- `stream`: 是否流式输出，默认 `true`。
- `timeout`: 请求超时时间，默认 `30`。

手工编辑时，`compatibleType` 支持大小写自动规范化，字段前后空格也会被自动裁掉。`model` 不能为空，否则拉取时会报错，避免保存坏配置。
