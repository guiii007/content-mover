# 内容迁移

选中文字，右键点“内容迁移”，附上备注，把原文、标题和出处追加到桌面的 Markdown 文件，例如 `内容迁移2026-10-7.md`。

## 下载与安装

**[下载 Windows 安装程序](https://github.com/guiii007/content-mover/releases/latest/download/ContentMoverSetup.exe)**

双击安装程序，按“下一步 → 安装 → 完成”。默认创建桌面快捷方式，可选择开机自动启动。适用于 Windows 10/11 x64，使用系统自带的 .NET Framework 4.x，无需浏览器扩展。

**[下载安卓内容迁移 APK](https://github.com/guiii007/content-mover/releases/download/v1.3.1/ContentMover-Android.apk)**（安卓 8.0 及以上）。Tailscale 是单独的远程连接工具，不是内容迁移 App。

安卓 1.2.1 数字键盘与发送队列测试版：[下载 APK](https://github.com/guiii007/content-mover/releases/download/v1.3.2-beta.2/ContentMover-Android.apk)。覆盖安装保留已有配对与密码，用数字键盘输入 4 位密码后无需再次扫码。发送内容加入后台迁移列表，可以继续编辑下一条；首次配对仍需扫码一次。电脑端无需升级，详见[手机使用说明](手机使用说明.md)。

远程连接需要的第三方工具：[Tailscale 安卓 1.104.1 安装包镜像](https://github.com/guiii007/content-mover/releases/download/v1.3.1/tailscale-android-universal-1.104.1.apk)。**Tailscale 由 Tailscale Inc. 及其贡献者开发，非本项目原创；这里只提供未修改的官方安装包镜像。** 来源、校验值和版权许可见[第三方软件说明](third-party/tailscale/README.md)。

![内容迁移图标](assets/content-mover.png)

## 使用

1. 在浏览器、Word 或 AI 客户端中选中文字，点击右键。
2. 点击菜单旁的“内容迁移 ↗”。没有选区时不显示，也可以按 **Ctrl+Alt+M**。
3. 填写备注，核对标题、出处和原文，点“保存到 Markdown”。每次追加，不覆盖旧内容。

备注框支持 Markdown：`**加粗**`、`# 标题`。复制截图后，在图片粘贴框按 Ctrl+V，可连续粘贴多张；选中后按 Delete 删除。保存时图片写入摘录文件旁的 `assets2026-10-7` 这样的日期文件夹。移动摘录时请一起移动该文件夹。

从托盘菜单打开摘录时，优先使用标准位置安装的 Visual Studio Code，否则使用系统关联的程序。在 VS Code 中按 **Ctrl+Shift+V** 查看 Markdown 预览。VS Code 需要自行安装。

普通选区直接打开备注，出处在后台补齐。没有可读取的选区文字时会尝试复制；如果仍无法读取，请先 Ctrl+C，再按快捷键，核对兼容预览并确认。弹窗自动进入备注框；需要补充聊天标题时先进入标题框。

Word 尝试记录文档路径、页码和字符位置；浏览器尝试读取网页地址；AI 客户端尝试取得聊天标题，无法取得时可手动补充。

## 原文与格式

软件保留应用提供的选区文本和 Markdown 语法，不改写文字。网页的视觉加粗、图片和字体不会自动转换成 Markdown；可以在备注中添加格式和图片。PDF 自身的字符映射错误仍可能导致复制乱码，请核对预览。本版本已移除框选识别，不自动识别或纠正字符。

升级后新摘录保存到 `.md`，原来的 TXT 保留，内容不会自动导入。默认文件已存在时继续追加；删除默认文件后按当天日期重建。自定义 TXT 路径升级为同名 MD。

## 安卓手机迁移（局域网与远程）

电脑版 1.3.1 已整合手机接收，只有一个托盘图标。电脑托盘菜单 → **手机连接 / 扫码配对…**。配对窗口关闭后仍后台接收；退出内容迁移时一起停止。已开启的手机接收随电脑版启动恢复，也可配合电脑版开机自启。手机安装 `ContentMover-Android.apk`，选中文字或分享一张 / 多张截图到内容迁移，扫码后发送到电脑桌面的日期 Markdown。离线内容先保存在手机，恢复连接后重试。详见 [手机使用说明](手机使用说明.md)。

安卓版 1.1.0 支持局域网与 Tailscale 远程连接，Windows 1.3.1 使用同一个安卓 APK，手机无需重新安装或重新配对。已由用户实测：手机使用运营商流量、电脑连接校园 Wi-Fi，文字与图片成功写入 `.md`。详见 [远程连接说明](远程连接说明.md)。旧版安装包继续保留。

## 设置与卸载

双击桌面快捷方式启动；已运行时打开设置。右键托盘图标可以更改保存位置、开关右键按钮、开关自启、打开摘录或退出。在 Windows“设置 → 应用”中卸载，个人摘录和设置保留。

工具在本机运行，不上传摘录。禁止复制或不提供选区信息的应用可能无法摘录。

## 开发

```powershell
.\build.ps1
.\package.ps1
.\build-installer.ps1
Start-Process .\内容迁移.exe --self-test -Wait
Start-Process .\内容迁移.exe --render-test -Wait
```

安装程序内置完整程序包，无需联网下载组件。个人配置、摘录和诊断不属于发布内容。
