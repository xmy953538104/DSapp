# Chat-AI 素材交付说明

把你准备好的源图放在 `gpt_mobile/asset-drop/`。这个目录不直接参与 Android 编译，我会从这里生成真正放进 `app/src/main/res/` 的密度资源，避免 CI 因资源名、尺寸或格式问题失败。

## App 图标

Android 自适应图标的前景层和背景层都是 108dp 画布，中心 72dp 是主要可见区，四周 18dp 可能被系统裁切、缩放或用于动效。换成 1024 源图时，可以按这个比例理解：

- 源图尺寸：`1024 x 1024`
- 关键主体建议范围：中心约 `680 x 680` 到 `720 x 720`
- 主体不要贴边，也不要撑满整张图
- 桌面最终观感请用 `app-icon-preview.png` 给我确认

建议继续提供这些文件：

| 文件名 | 尺寸 | 背景 | 用途 |
| --- | --- | --- | --- |
| `app-icon-preview.png` | 1024 x 1024 | 带完整底色和圆角观感 | 桌面图标最终观感参考，当前用于生成 launcher 前景 |
| `app-icon-foreground.png` | 1024 x 1024 | 透明 | 如果之后要做真正分层前景，把主体放在中心安全区 |
| `home-logo.png` | 1024 x 1024 | 透明 | 新安装首页顶部图标，当前已使用这一版 |

项目会生成这些 Android 密度资源：

| 目录 | 文件名 | 实际尺寸 |
| --- | --- | --- |
| `mipmap-mdpi` | `ic_gpt_mobile_foreground.png` | 108 x 108 |
| `mipmap-hdpi` | `ic_gpt_mobile_foreground.png` | 162 x 162 |
| `mipmap-xhdpi` | `ic_gpt_mobile_foreground.png` | 216 x 216 |
| `mipmap-xxhdpi` | `ic_gpt_mobile_foreground.png` | 324 x 324 |
| `mipmap-xxxhdpi` | `ic_gpt_mobile_foreground.png` | 432 x 432 |

Chatbox 本地也保留了多尺寸图标，例如 `assets/icons/1024x1024.png`、`512x512.png`、`256x256.png`、`128x128.png`、`96x96.png`、`72x72.png`、`48x48.png`、`32x32.png`、`24x24.png`、`16x16.png`。我们这边是 Android 应用，自适应图标优先使用上面那组 `mipmap` 密度资源。

## 聊天头像

聊天头像实际显示很小：聊天气泡里约 38dp，会话列表约 40dp。源图可以给大一点，但接入时会压缩，避免运行时解码大图影响流畅度。

| 文件名 | 推荐源图尺寸 | 当前接入尺寸 | 用途 |
| --- | --- | --- | --- |
| `provider-qwen.png` | 512 x 512 或更高方图 | 216 x 216 | 千问聊天头像、会话默认头像 |
| `provider-openai.png` | 512 x 512 | 约 216 x 216 即可 | OpenAI 头像 |
| `provider-claude.png` | 512 x 512 | 约 216 x 216 即可 | Claude 头像 |
| `provider-deepseek.png` | 512 x 512 | 约 216 x 216 即可 | DeepSeek 头像 |

头像请保持居中，主体不要贴边。透明背景或纯色圆形背景都可以。
