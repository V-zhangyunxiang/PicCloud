# Claude Code

Anthropic 推出的智能体(Agent)工具，主要用于 搜索+编码 的使用场景。网页版 ChatGPT、DeepSeek 本质上算是聊天机器人，CC 是直接运行在电脑里的执行者，可以对本地文件进行增删改查、安装依赖，只需要提出要求它就会一步步执行、迭代直到完成，这种能自主操作计算机、完成实际任务的形态，就是大家常说的 AI Agent。

# 使用方式

## 终端使用(CLI)

更灵活，可以接入第三方的 API，选择更便宜的模型购买更便宜的套餐。终端是一个文本窗口，专门用来运行各种命令行程序，输入命令程序把结果显示出来，

## 桌面应用使用(GUI)


# Prompt 提示词

就是你输入给大模型的内容。

## 不知道想要什么

  别装懂，告诉 AI，我的想法很模糊，请通过向我提问，帮我发散探索。
  
## 非常清楚要想什么

  逆向工程。从结果倒推 AI 需要哪些前提条件。
  目标：固定输入 -> 固定输出。
  
## 两种提示词
 
### 对话框提示词 

 临时的、发散的
 当次对话有效
 每次都要重新交代
 
### 系统提示词

AI 的底层人设
每次启动都生效
稳定输出的关键

## 最佳实践

通过逆向工程找到完美提示词 -> 写入系统提示词 -> AI 变成稳定输出的干活机器。

Prompt 越具体，它才能干的越好，具体的前提是花时间学习相关领域的知识，没有人不会写作就能指挥 AI 写出好文章，没有人不会写代码就能通过 AI 写出好代码，AI 能帮你学习但不是让你不用学习。

# 上下文窗口(Context Window)

Claude 的短期记忆。模型本质是"无状态"的，没有真正的记忆，每次发送新消息时系统把全部历史打包重发了一遍，对话越长，每次发送的包越大，聊天轮次越多 Token 指数级暴涨，注意力会涣散，记忆错乱幻觉频发。

上述问题在写代码时尤为突出，需要及时控制上下文，做好任务隔离。

1. 一个功能写完了，果断清空记忆，新开一个窗口。
2. 遇到一个新的 bug，开一个新的上下文，不要在旧对话继续。
3. 不要指望一个对话框从头到尾写完一个复杂的项目。

# Token

模型不是按字读，也不是按词读，是按 Token读。

Token 是大模型处理文本的最小单位。一个汉字通常是 1-2 个 Token，一个英文单词可能是 1-3 个 Token。代码、标点、特殊符号，各有各的切分规则。

大模型的一切都按 Token 计量:
- 计费按 Token 算。
- 上下文窗口的大小按 Token 算。
- API 的速率限制也按 Token 算。

# Tools

没有工具，cc 就只能输出文本，那它就是一个大语言模型。

1.文件操作
   读、写、删、搜。
   本地文件的生命周期管理。
2.搜索
   代码库、文件名、文档内容。
   精准定位任何资源。
3.执行
   shell 命令、运行测试、启动服务器。
4.网络搜索
   实时搜索网页内容、获取最新信息。
   
 有了 Tools 能感觉 cc 能操作我的电脑了，agent 能操作的是系统软件，不能操作下载的软件，比如剪辑、Word 等，第三方软件会有内置的 AI 功能，但是不太可能开方 API 给第三方 Agent 操作。
 
# Skill

Skill是 在 Agent 和 MCP 之上的进一步抽象。可以把 Skills 理解为 **“顶级的AI专家大脑”**，它是一个自包含的功能模块，包含的不仅仅是提示词，还有知识、方法和工具，这一切都被打包在一个结构清晰的文件夹里：

```text
skill-name/
├── SKILL.md        # 核心：工作指令、方法论和元数据[reference:1][reference:2]
├── scripts/        # 辅助脚本，Claude 可直接调用[reference:3]
├── templates/      # 文档、代码生成模板[reference:4]
└── resources/      # API 文档、架构图等参考材料[reference:5]
```
- **SKILL.md**：是整个 Skills 的 **“核心大脑”**，它用纯文本告诉 AI 这个技能是什么、什么情况下触发、该如何一步步完成任务。
    
- **辅助资源**：`scripts/`（比如执行复杂计算）、`templates/`（比如生成标准报告）、`resources/`（比如API规范）进一步支持任务执行。

Skill 把 Agent 的某个能力封装成一个可复用的"技能包"——就像手机上的 App，装上就能用。
每个 Skill 定义了：能做什么、需要什么输入、输出什么格式、依赖哪些工具。

有了 Skill:
  - 不同 Agent 之间可以共享能力。
  - 新 Agent 不用从零开发，组合现有 Skill 就行。
  - 能力可以独立升级，不影响其他部分。

# 快捷键命令

 模板：/ + 预设好的指令。
 
 /clear 清空上下文，开新对话
 /compact 压缩上下文，释放空间
 /context 查看上下文使用量
 /rewind 回滚到历史节点
 /resume 恢复上次会话
 /rename 为会话命名
 /model  切换 AI 模型
 /cost 查看费用和用量
 /plan 进入 plan 模式
 /init 初始化 claude.md
 /permissions 管理工具权限
 /mcp 管理 MCP 连接
 
# 历史记录和恢复
 
随时接力，无缝衔接上一次的工作。

claude -c  继续上次会话

cluade -r 找回历史会话

 /rename 给会话起名字

**ClAUDE.md**

项目背景每次启动都会读一遍

1.项目背景
2.依赖与命令
3.验证命令
4.风格与禁忌

**MEMORY.md**
自动记忆功能，干活的过程中，cc 会自己总结经验 ./claude/project name/memory/，自动触发无需手动干预

# MCP 服务器

突破本地边界，工具对接的"USB 协议"。

默认只能碰本地文件，与云端服务隔离。MCP 连接直接连通 Notion、GitHub 数据库、搜索，丝滑操作。

# SubAgent(子 Agent)
 
派一个干净的分身去干活，会继承主对话的权限和模型，向主代理进行单线程的汇报。
/plan 模式切换。

# Agent Teams(多会话组队协作)
   
实验性，行为复杂不可控

# 检查点:每次改动自动存档

按两下 ESC 或者 /rewind -> 弹出历史列表 -> 选择节点回滚。
只跟踪 cc 编辑过的文件，不跟踪 bash 命令，只保留 30 天。

大型项目要配合 Git 使用。

# 工具和模型是什么关系

## 关系

工具和模型是两个可以自由组合的独立部分。

这种“解耦”让工具的“大脑”不再是固定的，可以根据不同任务自由更换。你可以把它们想象成“工程大脑”和“专业知识大脑”的完美协作。

- **工程大脑：指挥系统 (AI 工具层)**  
    这部分像项目的总工程师，它不关心具体知识细节，只负责全局指挥。
    
    - **核心职责**：理解你的自然语言指令、在项目中自动翻阅分析代码、调用工具（修改文件、执行命令、搜索网络等）、规划任务步骤、并集成到你的开发流程（如 IDE 和 Git）。
        
    - **实现者**：Claude Code(内置强大工作流的典范)、OpenClaw(可托管的后台数字员工）、GitHub Copilot、Cursor 等。
- **专业知识大脑：执行引擎 (AI 模型层)**  
    这部分像各领域的“外聘专家”，在总工程师的指挥下，运用其专业知识具体执行任务。
    
    - **核心职责**：具体执行任务，如代码生成、错误分析、推理、文本润色、文件操作等。
        
    - **实现者**：各种大语言模型 (LLMs)，如 Claude Opus、GPT-5、DeepSeek-V4、智谱 GLM-5 等。

## 组合原理

那么，这两个独立的部分是如何组合在一起的？答案就在于 **统一“接口标准” + “中间层适配”** 的架构。

- **通过通用“API 协议”连接**：**“工程大脑”需要通过一个通用的接口 API 来调用 “专业知识大脑”**。只要模型提供商提供的 API 符合这个通用协议(如 OpenAI 或 Anthropic 的 API 规范），就能轻松对接。
- **借助中间层实现智能调度与核心替换**：更灵活的组合，离不开中间的“适配器”或“中间件”。它们主要负责：
    
    - **协议转换**：**将“工程大脑”的指令，转换成各种“专业大脑”都能理解的API调用格式**。
        
    - **智能路由**：**将任务请求智能转发并使用更好的模型处理特定任务**，降低切换成本。
        
    - **Claude Code变通接入国产模型**：尽管受限于地区，Claude Code还是能够通过设置 API Base URL 等环境变量变通接入兼容 Anthropic 协议的国产模型。

## 实战玩法：自由搭配的几种方式

- **方式一：直接替换
    
    - **配置**：在“工程大脑”(如Claude Code)的配置文件(`.claude.json`)里修改几行环境变量，指向另一个模型的 API 地址和密钥。
        
    - **示例**：从高昂的 Claude Opus 切换到国产 DeepSeek，可大幅降低成本。相比原生 Claude Opus 每百万 Tokens $15 的高昂 API 费用，开源方案甚至能实现长期低成本使用。
- **方式二：多模型 + 智能路由（“模型专家团”模式）**
    
    - **原理**：这是更高阶的模式。一个**智能路由器会在中间坐镇，分析每个任务的特点。
        
    - **示例**：综合运用 Claude 大上下文总结信息、国产开源模型自动化建表、联网搜索模型进行最新咨询搜索，发挥各模型优势。

# 统一“接口标准” + “中间层适配” 的架构如何理解

它本质上是**将“调用约定”与“内部实现”解耦**，使得前端工具(如Claude Code)和后端模型(如DeepSeek)可以像乐高一样自由组合。

## 1. 统一接口标准：定义“通用语言”

接口标准是一套**双方都认可的通信协议**，包括：

- **API 端点格式**（URL、HTTP 方法）
    
- **请求/响应数据结构**（JSON Schema）
    
- **认证方式**（API Key、Bearer Token）
    
- **错误码约定**
    
- **流式/非流式传输规则**

目前业界最流行的是 **OpenAI Chat Completion API 规范**（被绝大多数模型厂商支持），以及 **Anthropic 的 Message API 规范**。

举个例子（OpenAI 风格）

```java
POST https://api.openai.com/v1/chat/completions
Authorization: Bearer sk-xxx
Content-Type: application/json

{
  "model": "gpt-4",
  "messages": [{"role": "user", "content": "Hello"}],
  "stream": false
}
```

返回：
```java
{
  "id": "chatcmpl-xxx",
  "choices": [{"message": {"role": "assistant", "content": "Hi there!"}}]
}
```

**任何模型厂商只要实现这套 API 规范**（比如 DeepSeek、智谱、MiniMax 都提供了完全兼容的端点），那么原本为 OpenAI 写的工具就可以“零修改”接入这些模型。

## 2. 中间层适配器：解决“方言”差异的翻译官

虽然有了“通用语言”，但现实是：

- 某些工具(如 Claude Code)官方只支持 **Anthropic 的 Message API**(一种不同的接口标准)。
    
- 而国产模型大多提供 **OpenAI 风格**的 API。
    
- 甚至有些老模型只支持自定义的 RPC 格式。

于是就需要一个**中间层适配器**——它像“万能插头”：

- **输入端**：监听工具发来的请求(例如 Anthropic 风格)
    
- **输出端**：将请求转换成目标模型能理解的形式(例如 OpenAI 风格)，并把模型的响应再转换回工具期望的格式。

## 3. 实战流程：Claude Code → 中间层 → DeepSeek

有一个 Claude Code(只认 Anthropic API），想让它使用 DeepSeek(只认 OpenAI API)。步骤如下：

第一步：启动一个中间层适配器。
第二步：配置 Claude Code 指向该中间层。
第三步：调用流程详解：
1. **Claude Code 发起请求**（Anthropic 格式）  
    `POST /v1/messages`  
    Body: `{"model": "claude-3", "messages": [...]}`
2. **中间层收到后**：
    - 解析 Anthropic 格式 → 提取 `messages`、`system` 等。
        
    - 重构为 OpenAI 格式 → `{"model": "deepseek-chat", "messages": [...]}`
        
    - 附带你的 DeepSeek API Key，转发到 `https://api.deepseek.com/v1/chat/completions`
3. **DeepSeek 返回 OpenAI 格式**：  
    `{"choices": [{"message": {"content": "..."}}]}`
4. **中间层再次转换**：
    - 提取 `content` → 封装成 Anthropic 的 `{ "content": [{"text": "..."}] }`
        
    - 返回给 Claude Code
5. **Claude Code 完全无感知**，以为自己在和 Claude 模型对话。

# 如何创造一个 Skill 技能包

开发一个Skills技能包，确实有一套清晰的流程。简单来说就是**选择场景 → 创建骨架 → 填充内容 → 调试优化 → 分享**这五个步骤。

它的核心是一个包含结构化`SKILL.md`文件的文件夹，放在特定位置（如项目下的`.claude/skills/`），Claude 就能自动发现并使用了。

## 选择技能场景

一个好的技能源于一个**重复性**的任务。如果某个任务你经常需要向 Claude 解释怎么做，那么它就适合被打包成技能。

**如何选择：**

- **有固定流程**：比如固定的代码审查清单、生成特定格式的文档或报告。
    
- **可重复产出**：例如，总是按照某个格式写 Git 提交信息，或者生成统一风格的 PR 描述。
    
- **能被打包**：不需要复杂的外部服务调用，核心依赖是一套清晰的指导规则。
    
- **好的实践**：可以尝试从**代码审查** (Code Review) 或**提交信息生成** (Commit Message Generator) 这类场景开始，很容易看到效果。

## 创建技能包骨架

确定目标后，我们来创建这个技能的“骨架”。

**目录结构**：在项目根目录下创建`.claude/skills/`文件夹，并在其中为**每个技能创建一个子文件夹**，结构如下:

```text
.your-project/
└── .claude/
    └── skills/
        └── your-skill-name/    # 技能名称，使用小写和连字符
            ├── SKILL.md       # 核心指令文件 (必需)[reference:4]
            ├── scripts/       # 可执行的辅助脚本 (Python, Bash等)
            ├── templates/     # 代码或文档的生成模板
            └── references/    # 供参考的API文档、架构图等
```

## 编写核心文件 `SKILL.md`

这是技能的“大脑”，它由两部分组成：YAML 元数据(Frontmatter)和 Markdown 指令体(Body)。

**1. YAML 元数据 (Frontmatter)** - 技能的身份证

这个部分在文件顶部，由`---`包裹。它包含的信息虽然不直接给 Claude 看，但决定了技能是否能被正确触发。

**必填字段**：

- `name`: 技能的唯一标识，用短横线连接的小写英文，如`android-code-reviewer`。
- `description`: **最重要的字段**，它告诉 Claude 这个技能的功能和使用时机。为了 Claude 能更准确地自动调用，描述可以**略微“夸张”**，**明确列出触发关键词**(如`react-native`, `Android`, `performance`等），并简洁说明核心价值。

**可选字段**：
- `allowed-tools`: 限制技能可用的工具，提高安全性。  
- `context`: 设为`fork`可让技能在隔离环境运行。

**2. Markdown 指令体(Body)** - 技能的操作手册

Frontmatter 之后的内容是具体的操作指令，是 Claude 在技能被调用时会加载的核心“知识”。一个高质量的操作手册通常包含以下几个部分：

- **角色设定**：声明技能的身份。
    
- **核心原则**：列出最重要的准则，可用列表或强调块。
    
- **可分步执行的具体操作步骤**：以编号列表清晰呈现，核心结构。
    
- **示例对话**：为用户和 Claude 提供清晰的互动示例，展示“如何开始”，非常关键。
    
- **约束与限制**：描述禁止的行为，限定操作的边界。

**一个完整的`SKILL.md`文件结构示例（Android 代码审查）**：

```markdown
---
name: android-code-reviewer
description: Enforce Android/Kotlin best practices for code reviews. Use this when the user says \"review this code\", \"check my PR\", or mentions Android, Kotlin, or Compose.
allowed-tools: Read, Grep, Bash
---

# Android Code Reviewer Skill

You are an expert Android engineer specializing in Kotlin, Jetpack Compose, and architecture patterns.

## Core Principles
- Prioritize readability and maintainability over cleverness.
- Ensure all coroutines are scoped to a ViewModel or lifecycle.
- Adhere to Material Design guidelines for UI.

## Review Checklist
1.  **Architecture**: Is the logic separated from UI (e.g., ViewModel holding state)?
2.  **Coroutines**: Are suspend functions called from the correct scope? Is exception handling present?
3.  **Compose**: Are recompositions minimized? Are unstable parameters avoided?
4.  **Performance**: Are there any memory leaks (e.g., contexts in long-lived objects)?
5.  **Testing**: Is the logic testable? Are there clear boundaries for mocking?

## Example Interaction
**User**: "Review this composable function."
**Assistant**: "Of course. I'll analyze the code based on the Android Code Review Skill's checklist. Here are my findings..."

## Constraints
- Do not suggest major architectural changes unless the codebase clearly suffers from them.
- Focus on actionable feedback, not style preferences.
```
## 测试与调试技能包

- 在项目目录下启动 Claude，用`/你的技能名`(即你在`SKILL.md`中定义的`name`)来强制调用，验证功能是否符合预期。

- 使用`/skills`命令查看你的技能是否被 Claude 成功发现和加载。

- 用自然语言提问，看 Claude 是否会**自动触发**你的技能。

- 确保`SKILL.md`文件在正确的位置。

## 部署和分享

技能包制作完成并测试满意后，就可以分享给团队或社区了。

将技能打包成 Claude Code 插件，发布到内部或公开的插件市场，这是推荐的方式。

使用 `SKILL.md` 规范编写时，若需分发，可将整个技能文件夹打包为`.zip`文件。

#  Vibe Coding 是什么

Vibe Coding更像是编程的一种**特定风格或哲学**，而AI Coding则是一个更**宽泛的技术概念**。

定义很明确：**不审查、不理解、直接 Accept AI 生成的代码**。
 
![[Vide Coding VS AI Coding.png]]

# AI 越来越强，你的优势到底是什么

## 问题定义能力

AI 能解决你定义好的问题，但**定义问题本身就是最难的部分**。

AI 很强，但它需要精确的问题定义。我的优势在于能把模糊的需求拆解成清晰的技术规格——边界条件、异常处理、业务规则，这些 AI 不会主动问你，但不说清楚代码一定写不对。

## 上下文构建能力

AI 的输出质量，直接取决于你给的上下文质量。

同样的需求，两种人用 AI：

- A 直接说"写一个退款接口" → AI 凭想象写，大概率不符合你的业务
    
- B 给了完整的业务规则、数据库表结构、上下游接口文档 → AI 写出来的代码基本能直接用
    

差距在哪？不是 AI 的能力差距，是**喂给 AI 的上下文质量**的差距。

同样用 Claude Code，不同人产出质量差很多。差别在于上下文构建——我给 AI 的 Prompt 包含完整的业务规则、相关代码片段和边界条件，而不是一句话就让它写。AI 的上限是由你的输入质量决定的。

## 结果验证能力

代码跑起来了 ≠ 代码对了。
AI 生成的代码经常"看着对、跑得通、但语义是错的“。

验证能力不是"跑一下看有没有报错"，而是：

- **业务语义验证**：这段代码的行为是否符合业务意图。
    
- **边界验证**：极端情况下的行为是否符合预期。
    
- **回归验证**：这次改动有没有影响其他功能。

**AI 生成的代码我会重点验证业务语义，不是看能不能跑通，而是看行为是否符合业务意图。比如退款接口我会验证退款金额、退款对象、幂等性，这些是测试覆盖不到的，必须人工理解**。

## 技术决策能力

AI 能列出方案 A 和方案 B 的 pros/cons，但**拍板选哪个是你决定的**。

AI 的建议是通用的，你的决策是具体的。**通用建议和具体场景之间，永远需要人来做判断。**

举个实际例子：AI 会告诉你"高并发场景用缓存"，但不会告诉你**你们团队的缓存之前出过两次线上事故，这次要用就得多加一层降级**。这个判断只能你做。

AI 能帮我分析方案，但最终的选型决策是我做的。因为决策要考虑的不仅是技术因素，还有**团队现状、业务阶段、历史教训**——这些 AI 不知道，也不应该由 AI 决定。

## 成本控制能力

一次 Claude Code 的交互，可能消耗 5 万到 20 万 Token。一个项目跑下来，Token 费用可能比工程师工资还高。

成本控制能力包括：

- **知道什么时候用大模型，什么时候用小模型**
    
- **知道怎么组织上下文才能省 Token**
    
- **知道怎么写 Prompt 才能减少来回次数**
    
- **知道哪些任务让 AI 做更贵，自己做更便宜**

## 总结
一句话：**AI 的输出质量取决于你的输入质量——定义问题、构建上下文、验证结果、做决策、控成本，这五件事 AI 替代不了你**。

# AI 编程工具的 Token 成本怎么控制

## 模型路由—什么活用什么模型

不是所有代码都需要最强的模型来写。根据任务复杂度路由到不同模型。

大模型的输出价格是小模型的近 20 倍。如果一个 70% 的任务能用小模型解决，整体成本能降 60% 以上。Claude Code 内部已经在做这件事——简单补全走轻量模型，复杂推理走主力模型。

**我们团队做了模型路由策略——简单补全用小模型，复杂任务才用大模型。70% 的日常编码任务其实不需要最强模型，这样整体 Token 成本能降 60% 以上**。

## 上下文管理—别把整个代码库塞进去

上下文窗口是 Token 消耗的大头。很多人用 Claude Code 的时候，习惯把整个项目目录打开，让它自己找文件。结果每次交互，模型都要读取大量无关代码，Token 消耗直接翻几倍。

正确的做法：

- **只给相关的代码**：修改用户模块，就不要把支付模块的代码也塞进去
    
- **用摘要代替全文**：不需要把 500 行的配置文件全给 AI，给关键部分就行
    
- **按需加载**：先让 AI 看接口定义，需要实现细节时再给具体代码
    
- **定期清理上下文**：长时间对话会积累大量历史 Token，该开新会话就开新会话
    

**实际效果**：只给相关代码 vs 给整个项目，Token 消耗可能差 3-5 倍。

还有一个容易忽略的点：**AI 读到的无关代码越多，生成质量反而越差**。因为无关信息会干扰模型的注意力，让它关注到不该关注的地方。所以上下文管理不只是省钱，也是在提升质量。

**我会主动管理上下文，只给 AI 相关的代码片段而不是整个项目。修改用户模块就只给用户模块的代码和它依赖的接口定义。这样做 Token 消耗能降 3-5 倍，而且 AI 生成质量反而更好——因为无关信息少了，模型不容易被干扰**。

## Prompt 优化—一次说清楚，别来回改

**来回改是最浪费 Token 的。**

模糊的 Prompt → AI 生成不符合预期 → 再改 Prompt → 再生成 → 还是不对 → 再改……

一次说清楚，直接省掉 3-4 轮交互。

怎么写好 Prompt：

- **说清楚目标**：不是"写个接口"，是"写一个 REST 接口，接收退款请求，参数包括订单号和退款金额，需要幂等校验"
    
- **说清楚约束**：性能要求、安全要求、代码规范
    
- **说清楚上下文**：相关的数据库表结构、上下游接口
    
- **给示例**：一个具体的输入输出示例，比 100 字描述更有效

**成本对比**：一次精确 Prompt 可能 500 Token，四轮模糊 Prompt 可能 12000 Token——差 24 倍。这还没算时间的浪费。

## 缓存和复用

相似的问题，不要让 AI 从零生成。

- **Prompt 缓存**：相同的 Prompt 前缀，API 层面可以缓存，省掉重复计算的 Token。Anthropic 的 API 已经原生支持了 Prompt Caching，相同前缀的输入可以打到 90% 的缓存命中率
    
- **代码模板**：常见的 CRUD 接口、表单验证，维护一套模板，AI 只需要填差异部分
    
- **会话复用**：同一类任务的 Claude Code 会话，可以复用之前的上下文，不要每次从零开始
    

**面试怎么说**：**我们团队维护了一套代码模板，AI 只需要根据具体需求填充差异部分，而不是每次从零生成。配合 Prompt Caching，相似任务的 Token 消耗能降低 50% 以上**。

## 评估—哪些任务让 AI 做更贵

**不是所有任务都适合让 AI 做。**

有些任务你自己写 10 分钟搞定，让 AI 写要花 20 分钟来回改 Prompt + 审查代码，Token 费用还不少。这种情况下，直接自己写才是最优解。

适合让 AI 做的：

- 重复性高、模式固定的代码（CRUD、样板代码）
    
- 你知道要什么但手写太慢的代码
    
- 需要快速探索多种方案的场景
    
- 不熟悉的语言或框架的入门代码

不适合让 AI 做的：

- 改一行配置就能解决的小修改
    
- 你已经非常熟悉的代码区域，手写比解释更快
    
- 需要深度理解业务上下文的决策型代码
    
- 已经有精确模板、复制粘贴比生成更快的情况

举个日常开发的例子：线上报了一个 NullPointerException，你翻日志定位到是 `OrderService.java` 第 127 行，`user.getAddress()` 没做空判断。你加一行 `if (user.getAddress() != null)` 10 秒搞定。而使用 AI 整个流程可能会消耗不少 token 和时间，性价比极低。

## Token 成本控制总结

![[模型 Token 控制.png]]

# AI 生成的代码出了线上 bug，你怎么处理？

三步走：**先止血，再定因，最后补流程**。

止血：回滚或降级，先让线上恢复，不管是不是 AI 写的代码，处理方式一样。

定因：看监控确认影响范围，看日志追踪调用链路，定位到具体的问题代码。如果是 AI 生成的代码，还要想清楚**审查的时候为什么没拦住**——是安全没审到，还是边界条件没覆盖，还是业务语义理解有偏差。

补流程：补充测试用例、加强审查重点、甚至调整哪些场景允许 AI 生成。**一个 bug 不可怕，同类 bug 再出一次才可怕。**

# AI 写的代码上线出问题了，让 AI 修，结果 AI 也修不好，你怎么兜底？

兜底的关键是**你不能等 AI 来救你，你得自己能接手**：

- **先止血**：不管 AI 能不能修，先回滚到上一个稳定版本，线上用户等不了
    
- **自己排查**：看日志、看监控、看链路追踪，定位根因。这时候你之前审查 AI 代码积累的理解就派上用场了——**如果你审查的时候理解了逻辑，排查速度会快很多；如果你审查的时候是"看着没问题就过了"，那排查起来跟看别人的代码没区别**。
    
- **修复上线**：自己改代码，走正常的测试和发布流程
    
- **复盘**：为什么 AI 修不好？是上下文不够，还是问题超出它的能力范围？这次复盘的结论，决定下次类似问题还要不要交给 AI

说到底，AI 修不了的时候，你得能修。这是底线。

# AI 对于个人能力的退化

