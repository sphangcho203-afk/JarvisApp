# JARVIS ReAct Runtime

This package adds a bounded reasoning-and-acting loop for the Jarvis project.
It is designed to run as a Python sidecar in Termux or another local Python
process while the Android app remains responsible for microphone, UI, memory,
and verified device actions.

## Execution flow

```text
Operator request
    ↓
Thought phase: compact plan JSON
    ↓
Action phase: approved local tool call
    ↓
Observation: stdout or exact traceback
    ↓
Self-correction prompt
    ↓
Rewritten action or final answer
```

The scratchpad contains compact plan summaries, tool payloads, and observations.
It is an execution journal rather than an unrestricted internal monologue.

## Minimal initialization

```python
from collections.abc import Sequence

from agent_runtime import ChatMessage, ReActAgent, RestrictedPythonTool


class ExistingJarvisRouterAdapter:
    def __init__(self, router) -> None:
        self.router = router

    def complete(
        self,
        messages: Sequence[ChatMessage],
        *,
        temperature: float,
    ) -> str:
        return self.router.complete(
            messages=[
                {"role": message.role, "content": message.content}
                for message in messages
            ],
            temperature=temperature,
            response_format="json_object",
        )


model = ExistingJarvisRouterAdapter(existing_multi_provider_router)
agent = ReActAgent(
    model=model,
    tools={"python": RestrictedPythonTool(timeout_seconds=4.0)},
    max_steps=7,
    max_corrections=3,
)

result = agent.run(
    "Calculate the compound value of 12500 at 7.5 percent for 6 years "
    "and explain the result."
)
print(result.answer)
```

The adapter must return the provider response as a string. The current Gemini
and Groq rotation layer can remain responsible for selecting keys, models,
rate-limit cooldowns, and provider failover.

## Decision protocol

Each model turn must return one JSON object:

```json
{
  "phase": "action",
  "plan": [
    "Calculate the value with Python",
    "Use the observation in the final explanation"
  ],
  "action": {
    "tool": "python",
    "code": "principal = 12500\nrate = 0.075\nyears = 6\nprint(principal * (1 + rate) ** years)"
  },
  "final_answer": null
}
```

After a successful observation, the model may return:

```json
{
  "phase": "final",
  "plan": ["Present the verified result clearly"],
  "action": null,
  "final_answer": "After six years, the amount is approximately ..."
}
```

## Self-correction

When Python fails, `RestrictedPythonTool` returns the traceback string in
`ToolResult.traceback`. `ReActAgent` places that string between
`TRACEBACK_BEGIN` and `TRACEBACK_END` in the next model context. A final answer
is rejected while the latest tool action is still failing.

The loop also blocks identical repeated actions, limits total steps, limits
corrections, caps output size, and enforces a wall-clock timeout.

## Python tool boundary

The Python tool intentionally blocks filesystem access, shell commands,
processes, networks, environment access, private and dunder attributes, dynamic
code evaluation, and unapproved imports. It runs in Python isolated mode with a
scrubbed environment and POSIX resource limits when available.

This is an application-level restriction layer. Code from an untrusted party
still belongs inside a stronger operating-system sandbox, container, or VM.

## Android integration boundary

Do not let generated Python code directly control Android. Device operations
should continue through the existing Kotlin action router, where permissions,
authentication, confirmation, and success verification are available.

A later bridge can expose individually named actions such as:

```text
get_battery_status
start_timer
open_app
search_web
```

Each action should use a typed JSON payload and return a structured observation.
Avoid exposing a generic shell command or unrestricted file tool.
