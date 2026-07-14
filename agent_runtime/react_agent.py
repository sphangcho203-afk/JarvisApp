from __future__ import annotations

import hashlib
import json
from collections import Counter
from collections.abc import Mapping, Sequence

from .contracts import (
    ActionRequest,
    AgentDecision,
    AgentRunResult,
    ChatMessage,
    ModelClient,
    Tool,
    ToolResult,
)


class AgentProtocolError(RuntimeError):
    """Raised when a model response does not match the agent protocol."""


class AgentExhaustedError(RuntimeError):
    """Raised when the loop reaches its bounded correction or step limit."""


_AGENT_SYSTEM_PROMPT = """
You are the planning cortex inside JARVIS.

Operate in a bounded ReAct loop with two phases:

1. THOUGHT PHASE
Create a compact, logical plan. Do not produce a long hidden monologue. Return
only the minimum plan needed to choose the next action.

2. ACTION PHASE
Either call one approved tool or provide the final answer. Never invent a tool
result. After a tool error, inspect the exact traceback in the scratchpad,
change the code or approach, and retry. Do not repeat identical failing code.

Return exactly one JSON object and no Markdown:

{
  "phase": "action" | "final",
  "plan": ["short step", "short step"],
  "action": {
    "tool": "python",
    "code": "complete executable Python code"
  } | null,
  "final_answer": "answer for the operator" | null
}

Rules:
- Use phase=action when computation or verification is needed.
- Use phase=final when the request is solved without a tool or after successful
  tool observations support the answer.
- Code must print the value needed for the next decision.
- Use only approved modules and ordinary computation.
- Do not access files, processes, environment variables, devices, networks, or
  credentials through the Python tool.
- If the latest observation contains TRACEBACK, correct the actual cause before
  proposing a final answer.
""".strip()


class ReActAgent:
    """
    Bounded reasoning-and-acting controller with exact traceback feedback.

    The scratchpad stores compact plans, actions, and observations. It is an
    execution journal, not an unrestricted chain-of-thought transcript.
    """

    def __init__(
        self,
        *,
        model: ModelClient,
        tools: Mapping[str, Tool],
        max_steps: int = 7,
        max_corrections: int = 3,
        planning_temperature: float = 0.25,
        scratchpad_limit_chars: int = 40_000,
    ) -> None:
        if max_steps < 1:
            raise ValueError("max_steps must be at least 1")
        if max_corrections < 0:
            raise ValueError("max_corrections cannot be negative")
        if not tools:
            raise ValueError("At least one approved tool is required")

        self.model = model
        self.tools = dict(tools)
        self.max_steps = max_steps
        self.max_corrections = max_corrections
        self.planning_temperature = planning_temperature
        self.scratchpad_limit_chars = scratchpad_limit_chars

    def run(self, request: str) -> AgentRunResult:
        clean_request = request.strip()
        if not clean_request:
            raise ValueError("request cannot be blank")

        scratchpad_entries: list[str] = []
        tool_results: list[ToolResult] = []
        action_counts: Counter[str] = Counter()
        corrections = 0
        latest_action_failed = False

        for step_number in range(1, self.max_steps + 1):
            messages = self._build_messages(clean_request, scratchpad_entries)
            raw_response = self.model.complete(
                messages,
                temperature=self.planning_temperature,
            )

            try:
                decision = self._parse_decision(raw_response)
            except AgentProtocolError as error:
                corrections += 1
                scratchpad_entries.append(
                    self._protocol_observation(step_number, str(error), raw_response)
                )
                if corrections > self.max_corrections:
                    raise AgentExhaustedError(
                        "The model repeatedly violated the ReAct JSON protocol."
                    ) from error
                continue

            scratchpad_entries.append(
                self._format_plan(step_number, decision.plan)
            )

            if decision.phase == "final":
                if latest_action_failed:
                    corrections += 1
                    scratchpad_entries.append(
                        "OBSERVATION // FINAL REJECTED\n"
                        "The latest tool action failed. Inspect the exact TRACEBACK, "
                        "rewrite the action, and obtain a successful observation before "
                        "returning phase=final."
                    )
                    if corrections > self.max_corrections:
                        raise AgentExhaustedError(
                            "The correction budget was exhausted after a tool failure."
                        )
                    continue

                answer = (decision.final_answer or "").strip()
                if not answer:
                    corrections += 1
                    scratchpad_entries.append(
                        "OBSERVATION // FINAL REJECTED\n"
                        "phase=final requires a non-empty final_answer."
                    )
                    if corrections > self.max_corrections:
                        raise AgentExhaustedError(
                            "The model produced empty final answers repeatedly."
                        )
                    continue

                return AgentRunResult(
                    answer=answer,
                    steps=step_number,
                    corrections=corrections,
                    scratchpad=self._render_scratchpad(scratchpad_entries),
                    tool_results=tuple(tool_results),
                )

            action = decision.action
            if action is None:
                corrections += 1
                scratchpad_entries.append(
                    "OBSERVATION // ACTION REJECTED\n"
                    "phase=action requires a tool and complete code payload."
                )
                if corrections > self.max_corrections:
                    raise AgentExhaustedError(
                        "The model omitted required action payloads repeatedly."
                    )
                continue

            tool = self.tools.get(action.tool)
            if tool is None:
                corrections += 1
                scratchpad_entries.append(
                    "OBSERVATION // UNKNOWN TOOL\n"
                    f"Requested tool: {action.tool}\n"
                    f"Approved tools: {', '.join(sorted(self.tools))}"
                )
                if corrections > self.max_corrections:
                    raise AgentExhaustedError(
                        "The model repeatedly selected an unknown tool."
                    )
                continue

            action_key = self._action_fingerprint(action)
            action_counts[action_key] += 1
            if action_counts[action_key] > 1:
                corrections += 1
                latest_action_failed = True
                scratchpad_entries.append(
                    "OBSERVATION // DUPLICATE ACTION BLOCKED\n"
                    "The same tool payload already failed or was already attempted. "
                    "Change the code or choose a different approach."
                )
                if corrections > self.max_corrections:
                    raise AgentExhaustedError(
                        "The model kept repeating an identical action."
                    )
                continue

            scratchpad_entries.append(self._format_action(step_number, action))
            result = tool.run(action.code)
            tool_results.append(result)
            scratchpad_entries.append(
                self._format_observation(step_number, action.tool, result)
            )

            if result.success:
                latest_action_failed = False
            else:
                latest_action_failed = True
                corrections += 1
                scratchpad_entries.append(
                    "CORRECTION DIRECTIVE\n"
                    "The previous action failed. The TRACEBACK above is the exact "
                    "execution error. Diagnose its direct cause, rewrite the code, and "
                    "retry with phase=action. Do not hide, summarize, or ignore it."
                )
                if corrections > self.max_corrections:
                    raise AgentExhaustedError(
                        "The local execution correction budget was exhausted."
                    )

        raise AgentExhaustedError(
            f"The ReAct loop reached its {self.max_steps}-step limit without a final answer."
        )

    def _build_messages(
        self,
        request: str,
        scratchpad_entries: Sequence[str],
    ) -> list[ChatMessage]:
        scratchpad = self._render_scratchpad(scratchpad_entries)
        user_content = (
            f"OPERATOR REQUEST\n{request}\n\n"
            f"EXECUTION SCRATCHPAD\n{scratchpad or '[empty]'}\n\n"
            "Return the next strict JSON decision."
        )
        return [
            ChatMessage(role="system", content=_AGENT_SYSTEM_PROMPT),
            ChatMessage(role="user", content=user_content),
        ]

    def _render_scratchpad(self, entries: Sequence[str]) -> str:
        rendered = "\n\n".join(entries)
        if len(rendered) <= self.scratchpad_limit_chars:
            return rendered

        # Keep the newest observations and exact recent traceback. Old planning
        # notes are less valuable than the current execution state.
        trimmed = rendered[-self.scratchpad_limit_chars :]
        first_boundary = trimmed.find("\n\n")
        if first_boundary >= 0:
            trimmed = trimmed[first_boundary + 2 :]
        return "[older scratchpad entries compacted]\n\n" + trimmed

    @staticmethod
    def _parse_decision(raw_response: str) -> AgentDecision:
        raw_json = ReActAgent._extract_json_object(raw_response)
        try:
            payload = json.loads(raw_json)
        except json.JSONDecodeError as error:
            raise AgentProtocolError(
                f"Invalid JSON at line {error.lineno}, column {error.colno}: {error.msg}"
            ) from error

        if not isinstance(payload, dict):
            raise AgentProtocolError("The model response must be a JSON object.")

        phase = payload.get("phase")
        if phase not in {"action", "final"}:
            raise AgentProtocolError("phase must be either 'action' or 'final'.")

        plan_value = payload.get("plan", [])
        if not isinstance(plan_value, list) or not all(
            isinstance(item, str) for item in plan_value
        ):
            raise AgentProtocolError("plan must be an array of strings.")
        plan = tuple(item.strip() for item in plan_value if item.strip())[:8]

        final_value = payload.get("final_answer")
        final_answer = final_value if isinstance(final_value, str) else None

        action_value = payload.get("action")
        action: ActionRequest | None = None
        if phase == "action":
            if not isinstance(action_value, dict):
                raise AgentProtocolError(
                    "phase=action requires an action object."
                )
            tool = action_value.get("tool")
            code = action_value.get("code")
            if not isinstance(tool, str) or not tool.strip():
                raise AgentProtocolError("action.tool must be a non-empty string.")
            if not isinstance(code, str) or not code.strip():
                raise AgentProtocolError("action.code must be complete executable code.")
            action = ActionRequest(tool=tool.strip(), code=code.strip())

        return AgentDecision(
            phase=phase,
            plan=plan,
            action=action,
            final_answer=final_answer,
        )

    @staticmethod
    def _extract_json_object(raw_response: str) -> str:
        text = raw_response.strip()
        if text.startswith("```"):
            lines = text.splitlines()
            if lines:
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            text = "\n".join(lines).strip()

        if text.startswith("{") and text.endswith("}"):
            return text

        start = text.find("{")
        if start < 0:
            raise AgentProtocolError("No JSON object was found in the model response.")

        depth = 0
        in_string = False
        escaped = False
        for index in range(start, len(text)):
            character = text[index]
            if in_string:
                if escaped:
                    escaped = False
                elif character == "\\":
                    escaped = True
                elif character == '"':
                    in_string = False
                continue

            if character == '"':
                in_string = True
            elif character == "{":
                depth += 1
            elif character == "}":
                depth -= 1
                if depth == 0:
                    return text[start : index + 1]

        raise AgentProtocolError("The JSON object was not closed.")

    @staticmethod
    def _action_fingerprint(action: ActionRequest) -> str:
        normalized = f"{action.tool}\n{action.code.strip()}"
        return hashlib.sha256(normalized.encode("utf-8")).hexdigest()

    @staticmethod
    def _format_plan(step_number: int, plan: Sequence[str]) -> str:
        if not plan:
            return f"THOUGHT SUMMARY // STEP {step_number}\n[no plan supplied]"
        body = "\n".join(f"- {item}" for item in plan)
        return f"THOUGHT SUMMARY // STEP {step_number}\n{body}"

    @staticmethod
    def _format_action(step_number: int, action: ActionRequest) -> str:
        return (
            f"ACTION // STEP {step_number}\n"
            f"TOOL: {action.tool}\n"
            "CODE:\n"
            f"{action.code}"
        )

    @staticmethod
    def _format_observation(
        step_number: int,
        tool_name: str,
        result: ToolResult,
    ) -> str:
        if result.success:
            output = result.stdout.strip() or "[completed with no stdout]"
            return (
                f"OBSERVATION // STEP {step_number} // SUCCESS\n"
                f"TOOL: {tool_name}\n"
                f"DURATION_MS: {result.duration_ms}\n"
                f"STDOUT:\n{output}"
            )

        return (
            f"OBSERVATION // STEP {step_number} // ERROR\n"
            f"TOOL: {tool_name}\n"
            f"DURATION_MS: {result.duration_ms}\n"
            "TRACEBACK_BEGIN\n"
            f"{result.traceback}\n"
            "TRACEBACK_END"
        )

    @staticmethod
    def _protocol_observation(
        step_number: int,
        error: str,
        raw_response: str,
    ) -> str:
        return (
            f"OBSERVATION // STEP {step_number} // PROTOCOL ERROR\n"
            f"ERROR: {error}\n"
            "RAW_RESPONSE_BEGIN\n"
            f"{raw_response[:8_000]}\n"
            "RAW_RESPONSE_END\n"
            "Return one valid JSON object on the next attempt."
        )
