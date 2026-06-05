# Chat-AI 素材交付说明

把要替换的源图放在 `gpt_mobile/asset-drop/`。这个目录不直接参与 Android 编译，最终会从这里生成 `app/src/main/res/` 里的 Android 资源。

## App 图标

当前图标结构：

- `AndroidManifest.xml` 只设置 `android:icon="@mipmap/ic_gpt_mobile"`，不设置 `roundIcon`。
- `mipmap-anydpi-v26/ic_gpt_mobile.xml` 是独立矢量 launcher 图标，不使用 adaptive icon。
- launcher 图标的圆角底板和四根条都在同一个 vector 里，背景色为 `#FBF8F2`。
- 项目 `minSdk = 31`，不保留 `mipmap-mdpi` 到 `mipmap-xxxhdpi` 的低清 PNG fallback。
- 启动页同样引用 `@drawable/ic_gpt_mobile_splash`，背景色用 App 浅色主题背景 `#FBF8F2`。

推荐源图：

| 文件名 | 尺寸 | 用途 |
| --- | --- | --- |
| `app-icon-preview.png` | 1024 x 1024 | 桌面预览图，带完整底色 |
| `图标尺寸/*.png` | 48/72/96/144/192 | 历史 PNG 源图，当前 launcher 不再直接使用 |
| `home-logo.png` | 1024 x 1024 | 新安装首页顶部图标 |
| `provider-qwen.png` | 512 x 512 或更高方图 | 千问聊天头像 |

当前生成的资源：

| 目录 | 文件 | 用途 |
| --- | --- | --- |
| `mipmap-anydpi-v26` | `ic_gpt_mobile.xml` | 高清独立矢量 launcher 图标 |
| `drawable` | `ic_gpt_mobile_splash.xml` | 开屏图标 |
| `app/src/main` | `ic_gpt_mobile-playstore.png` | 512 x 512 |

不要提交 adaptive icon、`ic_gpt_mobile.png` 密度 fallback、`ic_gpt_mobile_foreground.png`、`ic_gpt_mobile_monochrome.png`、`ic_gpt_mobile_round.png` 或 `roundIcon`。这些资源会让不同 launcher 重新走不受控的裁切、缩放、低清或主题图标逻辑。

## 聊天头像

聊天头像实际显示很小，源图可以给大一点，但接入时会压缩，避免运行时解码大图影响流畅度。

| 文件名 | 推荐源图尺寸 | 当前接入尺寸 | 用途 |
| --- | --- | --- | --- |
| `provider-qwen.png` | 512 x 512 或更高方图 | 216 x 216 | 千问聊天头像、会话默认头像 |
| `provider-openai.png` | 512 x 512 | 约 216 x 216 | OpenAI 头像 |
| `provider-claude.png` | 512 x 512 | 约 216 x 216 | Claude 头像 |
| `provider-deepseek.png` | 512 x 512 | 约 216 x 216 | DeepSeek 头像 |

头像请保持主体居中，不要贴边。透明背景或纯色圆形背景都可以。
