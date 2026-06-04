# Chat-AI 素材交付说明

把你准备好的图片放在本目录：`gpt_mobile/asset-drop/`。这个目录不参与 Android 资源编译，先放这里最稳，不会因为同名资源或密度资源冲突导致 CI 失败。拿到素材后，再按下面的目标用途接入到 `res`。

## App 图标和启动页

| 文件名 | 尺寸 | 背景 | 用途 |
| --- | --- | --- | --- |
| `app-icon-foreground.png` | 1024 x 1024 | 透明 | 系统启动器自适应图标前景、启动闪屏图标 |
| `app-icon-preview.png` | 1024 x 1024 | 带完整圆角底色 | 给我对照你期望的最终桌面图标观感 |
| `home-logo.png` | 1024 x 1024 | 可透明，也可带完整底色 | 新安装首页顶部大图标 |

`app-icon-foreground.png` 的关键内容请放在中心安全区，建议主体控制在 680 到 720 px 宽高内。不要把四条竖线撑满整张图，否则 Android 自适应裁切后会显得过大。底色会由应用使用 `#FDE9D9` 或主题色生成。

如果你想直接给 Android 分密度图，也可以额外提供这些前景图：

| 文件夹 | 文件名 | 尺寸 | 中心安全区 |
| --- | --- | --- | --- |
| `mipmap-mdpi` | `ic_gpt_mobile_foreground.png` | 108 x 108 | 72 x 72 |
| `mipmap-hdpi` | `ic_gpt_mobile_foreground.png` | 162 x 162 | 108 x 108 |
| `mipmap-xhdpi` | `ic_gpt_mobile_foreground.png` | 216 x 216 | 144 x 144 |
| `mipmap-xxhdpi` | `ic_gpt_mobile_foreground.png` | 324 x 324 | 216 x 216 |
| `mipmap-xxxhdpi` | `ic_gpt_mobile_foreground.png` | 432 x 432 | 288 x 288 |

当前项目 `minSdk = 31`，所以优先用自适应图标，不需要再做老式 48/72/96/144/192 px 的 legacy launcher 图标。

## 聊天头像

| 文件名 | 尺寸 | 背景 | 用途 |
| --- | --- | --- | --- |
| `provider-openai.png` | 512 x 512 | 透明或纯色圆形 | OpenAI 聊天头像、会话默认头像 |
| `provider-claude.png` | 512 x 512 | 透明或纯色圆形 | Claude 聊天头像、会话默认头像 |
| `provider-deepseek.png` | 512 x 512 | 透明或纯色圆形 | DeepSeek 聊天头像、会话默认头像 |
| `provider-qwen.png` | 512 x 512 | 透明或纯色圆形 | 千问聊天头像、会话默认头像 |

头像会在聊天气泡里显示为 38 dp 圆形，在会话列表显示为 40 dp 圆形。图案请保持居中，主要内容建议控制在 440 到 480 px 内，不要贴边。

## 接入后的目标资源路径

这些是我接入时会使用的目标路径，你不用现在直接覆盖：

| 用途 | 目标路径 |
| --- | --- |
| 启动器图标前景 | `app/src/main/res/drawable-nodpi/app_icon_foreground.png` 或 `app/src/main/res/mipmap-*/ic_gpt_mobile_foreground.png` |
| 首页大图标 | `app/src/main/res/drawable-nodpi/home_logo.png` |
| OpenAI 头像 | `app/src/main/res/drawable-nodpi/provider_openai.png` |
| Claude 头像 | `app/src/main/res/drawable-nodpi/provider_claude.png` |
| DeepSeek 头像 | `app/src/main/res/drawable-nodpi/provider_deepseek.png` |
| 千问头像 | `app/src/main/res/drawable-nodpi/provider_qwen.png` |
