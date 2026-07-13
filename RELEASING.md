# 自动构建与发版

本体仓库和相机库使用同一套发版规则。向受支持分支推送提交时，GitHub Actions 只进行构建，并在对应的 Workflow Run 中提供保留 30 天的 JAR Artifact；只有推送符合规范的标签才会创建 GitHub Release。

## 标签格式

| 平台 | 标签示例 | JAR 内版本 |
| --- | --- | --- |
| NeoForge 1.21.1 | `Neo-1.21.1-v0.3.1-Alpha1` | `0.3.1-Alpha1` |
| NeoForge 26.1.2 | `Neo-26.1.2-v0.0.2-PreAlpha1` | `2612-0.0.2-PreAlpha1` |
| Forge 1.20.1 | `Forge-1.20.1-v0.1.1` | `1.20.1-0.1.1` |

标签中的版本是发版版本的唯一来源，发版前不需要专门修改 `gradle.properties` 中的 `mod_version`。版本只能包含字母、数字、点、下划线和连字符。

## 发版步骤

先确认需要发布的提交已经推送到对应分支，然后在该提交上创建并推送标签：

```powershell
git status
git push origin HEAD
git tag Neo-1.21.1-v0.3.1-Alpha1
git push origin Neo-1.21.1-v0.3.1-Alpha1
```

推送后在仓库的 **Actions** 页面检查构建，再到 **Releases** 页面确认标题、说明和 JAR。Release 标题由标签中的连字符替换为空格生成；说明会列出同系列上次成功发布以来的非合并提交标题、正文和提交链接。第一次使用某个标签系列时只记录当前提交。

所有自动 Release 都是正式发布。NeoForge 1.21.1 Release 会成为仓库的 Latest，其他平台不会覆盖 Latest 标记。

## NeoForge 1.21.1 与 main

在 `neo-1.21.1-dev2` 合并前，`Neo-1.21.1-v*` 标签会 target 该开发分支。标签提交进入 `main` 后，Workflow 会自动优先 target `main`；即使之后删除开发分支也不需要修改 Workflow 或标签格式。

## 失败重试

构建或上传失败时，在 Actions 页面重新运行原 Workflow 即可。流程会更新已有 Release 并覆盖同名 JAR，不会重复创建 Release。不要为了重试而创建另一个名称不同但版本相同的标签；如果标签指向了错误提交，应先处理错误标签，再使用正确提交重新发版。
