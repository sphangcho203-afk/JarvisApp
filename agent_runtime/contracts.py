from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal, Mapping, Protocol, Sequence


Role = Literal["system", "user", "assistant"]
Phase = Literal["action", "final"]


@dataclass(frozen=True)
class ChatMessage:
    role: Role
    content: str


class ModelClient(Protocol):
    """Minimal adapter implemented by the existing multi-provider router."""

    def complete(
        self,
        messages: Sequence[ChatMessage],
        *,
        temperature: float,
    ) -> str:
        """Return one model response as text."""


@dataclass(frozen=True)
class ActionRequest:
    tool: str
    code: str


@dataclass(frozen=True)
class AgentDecision:
    phase: Phase
    plan: tuple[str, ...] = ()
    action: ActionRequest | None = None
    final_answer: str | None = None


@dataclass(frozen=True)
class ToolResult:
    success: bool
    stdout: str
    stderr: str
    traceback: str
    duration_ms: int
    metadata: Mapping[str, str] = field(default_factory=dict)


class Tool(Protocol):
    name: str

    def run(self, payload: str) -> ToolResult:
        """Execute one approved tool payload and return a structured result."""


@dataclass(frozen=True)
class AgentRunResult:
    answer: str
    steps: int
    corrections: int
    scratchpad: str
    tool_results: tuple[ToolResult, ...]
