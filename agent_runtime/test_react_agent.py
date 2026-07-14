from __future__ import annotations

import json
import unittest
from collections.abc import Sequence

from agent_runtime.contracts import ChatMessage
from agent_runtime.react_agent import ReActAgent
from agent_runtime.safe_python_tool import RestrictedPythonTool


class ScriptedModel:
    def __init__(self, responses: list[dict[str, object]]) -> None:
        self.responses = responses
        self.calls: list[Sequence[ChatMessage]] = []

    def complete(
        self,
        messages: Sequence[ChatMessage],
        *,
        temperature: float,
    ) -> str:
        self.calls.append(messages)
        if not self.responses:
            raise AssertionError("No scripted model response remains")
        return json.dumps(self.responses.pop(0))


class ReActAgentTests(unittest.TestCase):
    def test_traceback_is_returned_and_code_is_corrected(self) -> None:
        model = ScriptedModel(
            [
                {
                    "phase": "action",
                    "plan": ["Run the calculation"],
                    "action": {
                        "tool": "python",
                        "code": "print(10 / 0)",
                    },
                    "final_answer": None,
                },
                {
                    "phase": "action",
                    "plan": ["Correct the divisor and rerun"],
                    "action": {
                        "tool": "python",
                        "code": "print(10 / 2)",
                    },
                    "final_answer": None,
                },
                {
                    "phase": "final",
                    "plan": ["Report the verified value"],
                    "action": None,
                    "final_answer": "The verified result is 5.0.",
                },
            ]
        )
        agent = ReActAgent(
            model=model,
            tools={"python": RestrictedPythonTool(timeout_seconds=2.0)},
            max_steps=5,
            max_corrections=2,
        )

        result = agent.run("Calculate ten divided by two.")

        self.assertEqual(result.answer, "The verified result is 5.0.")
        self.assertEqual(result.corrections, 1)
        self.assertEqual(len(result.tool_results), 2)
        self.assertFalse(result.tool_results[0].success)
        self.assertIn("ZeroDivisionError", result.tool_results[0].traceback)
        self.assertTrue(result.tool_results[1].success)
        self.assertEqual(result.tool_results[1].stdout.strip(), "5.0")

        second_call_context = model.calls[1][1].content
        self.assertIn("TRACEBACK_BEGIN", second_call_context)
        self.assertIn("ZeroDivisionError", second_call_context)
        self.assertIn("TRACEBACK_END", second_call_context)

    def test_policy_error_is_treated_as_a_correctable_observation(self) -> None:
        model = ScriptedModel(
            [
                {
                    "phase": "action",
                    "plan": ["Read a local file"],
                    "action": {
                        "tool": "python",
                        "code": "print(open('/etc/passwd').read())",
                    },
                    "final_answer": None,
                },
                {
                    "phase": "action",
                    "plan": ["Use approved computation instead"],
                    "action": {
                        "tool": "python",
                        "code": "print(sum(range(1, 6)))",
                    },
                    "final_answer": None,
                },
                {
                    "phase": "final",
                    "plan": ["Report the computed total"],
                    "action": None,
                    "final_answer": "The total is 15.",
                },
            ]
        )
        agent = ReActAgent(
            model=model,
            tools={"python": RestrictedPythonTool(timeout_seconds=2.0)},
        )

        result = agent.run("Add the integers from one through five.")

        self.assertEqual(result.answer, "The total is 15.")
        self.assertIn("not allowed", result.tool_results[0].traceback)
        self.assertEqual(result.tool_results[1].stdout.strip(), "15")


if __name__ == "__main__":
    unittest.main()
