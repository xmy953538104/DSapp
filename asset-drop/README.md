# Chat-AI 素材交付说明

把要替换的源图放在 `gpt_mobile/asset-drop/`。这个目录不直接参与 Android 编译，最终会从这里生成 `app/src/main/res/` 里的 Android 资源。

## App 图标

当前图标结构参考 FLClash：

- launcher icon 不使用 adaptive icon XML，避免系统二次缩放或 mask 裁切。
- `AndroidManifest.xml` 只设置 `android:icon="@mipmap/ic_gpt_mobile"`，不设置 `roundIcon`。
- `mipmap-mdpi` 到 `mipmap-xxxhdpi` 的 `ic_gpt_mobile.png` 是最终完整图标，已经带 `#FBF8F2` 圆角底板。
- 启动页引用单独的 `@drawable/ic_gpt_mobile_splash`，背景色用 App 浅色主题背景 `#FBF8F2`，避免 launcher 安全区缩放影响开屏大小。

推荐源图：

| 文件名 | 尺寸 | 用途 |
| --- | --- | --- |
| `app-icon-preview.png` | 1024 x 1024 | 最终桌面观感，带完整底色 |
| `图标尺寸/*.png` | 48/72/96/144/192 | Android launcher 分密度完整图标源图 |
| `home-logo.png` | 1024 x 1024 | 新安装首页顶部图标 |
| `provider-qwen.png` | 512 x 512 或更高方图 | 千问聊天头像 |

由 `app-icon-preview.png` 生成的资源：

| 目录 | 文件 | 尺寸 |
| --- | --- | --- |
| `mipmap-mdpi` 到 `mipmap-xxxhdpi` | `ic_gpt_mobile.png` | 最终 launcher 图标 |
| `app/src/main` | `ic_gpt_mobile-playstore.png` | 512 x 512 |

不要再提交 `mipmap-anydpi-v26/ic_gpt_mobile.xml`、`ic_gpt_mobile_foreground.png`、`ic_gpt_mobile_monochrome.png` 或 `roundIcon`。这套图标现在走完整密度 PNG，目的是让 launcher 直接使用最终图，不再经过 adaptive foreground/background 缩放。

## 聊天头像

聊天头像实际显示很小，源图可以给大一点，但接入时会压缩，避免运行时解码大图影响流畅度。

| 文件名 | 推荐源图尺寸 | 当前接入尺寸 | 用途 |
| --- | --- | --- | --- |
| `provider-qwen.png` | 512 x 512 或更高方图 | 216 x 216 | 千问聊天头像、会话默认头像 |
| `provider-openai.png` | 512 x 512 | 约 216 x 216 | OpenAI 头像 |
| `provider-claude.png` | 512 x 512 | 约 216 x 216 | Claude 头像 |
| `provider-deepseek.png` | 512 x 512 | 约 216 x 216 | DeepSeek 头像 |

头像请保持主体居中，不要贴边。透明背景或纯色圆形背景都可以。
