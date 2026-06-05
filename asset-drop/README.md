# Chat-AI 素材交付说明

把要替换的源图放在 `gpt_mobile/asset-drop/`。这个目录不直接参与 Android 编译，最终会从这里生成 `app/src/main/res/` 里的 Android 资源。

## App 图标

当前图标结构参考 FLClash：

- `mipmap-anydpi-v26/ic_gpt_mobile.xml` 是 Android 8+ 使用的 adaptive icon。
- adaptive icon 的背景是 `#FBF8F2`。
- adaptive icon 的前景引用 `@mipmap/ic_gpt_mobile_foreground`，这个资源由各密度透明底 PNG 提供，只包含图案主体，并且缩进到 adaptive icon 安全区。
- 启动页引用单独的 `@drawable/ic_gpt_mobile_splash`，背景色用 App 浅色主题背景 `#FBF8F2`，避免 launcher 安全区缩放影响开屏大小。
- legacy fallback 使用 `mipmap-mdpi` 到 `mipmap-xxxhdpi` 的完整 PNG 图标，不再使用 anydpi 矢量 fallback。

推荐源图：

| 文件名 | 尺寸 | 用途 |
| --- | --- | --- |
| `app-icon-preview.png` | 1024 x 1024 | 最终桌面观感，带完整底色 |
| `图标尺寸/*.png` | 48/72/96/108/144/162/192/216/324/432 | Android launcher 分密度图标源图 |
| `home-logo.png` | 1024 x 1024 | 新安装首页顶部图标 |
| `provider-qwen.png` | 512 x 512 或更高方图 | 千问聊天头像 |

由 `app-icon-preview.png` 生成的资源：

| 目录 | 文件 | 尺寸 |
| --- | --- | --- |
| `mipmap-anydpi` | `ic_gpt_mobile.xml` / `ic_gpt_mobile_round.xml` | vector fallback |
| `app/src/main` | `ic_gpt_mobile-playstore.png` | 512 x 512 |

adaptive foreground 不是最终展示图，而是 Android launcher 会裁切和套 mask 的前景层，所以主体必须比最终预览图更小。不要把带底色、带棋盘格或完整圆角底板的图片接成 adaptive foreground，否则会被系统当作整张前景层参与 mask，造成贴边。

## 聊天头像

聊天头像实际显示很小，源图可以给大一点，但接入时会压缩，避免运行时解码大图影响流畅度。

| 文件名 | 推荐源图尺寸 | 当前接入尺寸 | 用途 |
| --- | --- | --- | --- |
| `provider-qwen.png` | 512 x 512 或更高方图 | 216 x 216 | 千问聊天头像、会话默认头像 |
| `provider-openai.png` | 512 x 512 | 约 216 x 216 | OpenAI 头像 |
| `provider-claude.png` | 512 x 512 | 约 216 x 216 | Claude 头像 |
| `provider-deepseek.png` | 512 x 512 | 约 216 x 216 | DeepSeek 头像 |

头像请保持主体居中，不要贴边。透明背景或纯色圆形背景都可以。
