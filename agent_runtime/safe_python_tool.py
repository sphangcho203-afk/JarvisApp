from __future__ import annotations

import ast
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Final

from .contracts import ToolResult


class PythonPolicyError(ValueError):
    """Raised before execution when a snippet violates the local tool policy."""


class _PolicyValidator(ast.NodeVisitor):
    _ALLOWED_IMPORTS: Final[frozenset[str]] = frozenset(
        {
            "collections",
            "datetime",
            "decimal",
            "fractions",
            "functools",
            "itertools",
            "json",
            "math",
            "random",
            "re",
            "statistics",
        }
    )

    _BLOCKED_CALLS: Final[frozenset[str]] = frozenset(
        {
            "breakpoint",
            "compile",
            "delattr",
            "dir",
            "eval",
            "exec",
            "getattr",
            "globals",
            "help",
            "input",
            "locals",
            "memoryview",
            "open",
            "setattr",
            "type",
            "vars",
            "__import__",
        }
    )

    _BLOCKED_NODES: Final[tuple[type[ast.AST], ...]] = (
        ast.AsyncFor,
        ast.AsyncFunctionDef,
        ast.AsyncWith,
        ast.Await,
        ast.ClassDef,
        ast.Delete,
        ast.Global,
        ast.Lambda,
        ast.Nonlocal,
        ast.Yield,
        ast.YieldFrom,
    )

    def generic_visit(self, node: ast.AST) -> None:
        if isinstance(node, self._BLOCKED_NODES):
            raise PythonPolicyError(
                f"Python node {node.__class__.__name__} is not allowed."
            )
        super().generic_visit(node)

    def visit_Import(self, node: ast.Import) -> None:  # noqa: N802
        for alias in node.names:
            self._validate_import(alias.name)
        self.generic_visit(node)

    def visit_ImportFrom(self, node: ast.ImportFrom) -> None:  # noqa: N802
        if node.level != 0 or not node.module:
            raise PythonPolicyError("Relative imports are not allowed.")
        self._validate_import(node.module)
        self.generic_visit(node)

    def visit_Name(self, node: ast.Name) -> None:  # noqa: N802
        if node.id.startswith("_"):
            raise PythonPolicyError("Private and dunder names are not allowed.")
        self.generic_visit(node)

    def visit_Attribute(self, node: ast.Attribute) -> None:  # noqa: N802
        if node.attr.startswith("_"):
            raise PythonPolicyError("Private and dunder attributes are not allowed.")
        self.generic_visit(node)

    def visit_Call(self, node: ast.Call) -> None:  # noqa: N802
        if isinstance(node.func, ast.Name) and node.func.id in self._BLOCKED_CALLS:
            raise PythonPolicyError(f"Call to {node.func.id}() is not allowed.")
        self.generic_visit(node)

    def _validate_import(self, module_name: str) -> None:
        root = module_name.split(".", maxsplit=1)[0]
        if root not in self._ALLOWED_IMPORTS:
            raise PythonPolicyError(
                f"Import '{module_name}' is not allowed. Allowed modules: "
                + ", ".join(sorted(self._ALLOWED_IMPORTS))
            )


_RUNNER_SOURCE: Final[str] = r'''
import json
import traceback
from pathlib import Path

ALLOWED_IMPORTS = {
    "collections", "datetime", "decimal", "fractions", "functools",
    "itertools", "json", "math", "random", "re", "statistics"
}


def safe_import(name, globals=None, locals=None, fromlist=(), level=0):
    if level != 0:
        raise ImportError("Relative imports are disabled")
    root = name.split(".", 1)[0]
    if root not in ALLOWED_IMPORTS:
        raise ImportError(f"Import '{name}' is disabled")
    return __import__(name, globals, locals, fromlist, level)


SAFE_BUILTINS = {
    "ArithmeticError": ArithmeticError,
    "AssertionError": AssertionError,
    "Exception": Exception,
    "IndexError": IndexError,
    "KeyError": KeyError,
    "RuntimeError": RuntimeError,
    "TypeError": TypeError,
    "ValueError": ValueError,
    "ZeroDivisionError": ZeroDivisionError,
    "abs": abs,
    "all": all,
    "any": any,
    "bool": bool,
    "dict": dict,
    "divmod": divmod,
    "enumerate": enumerate,
    "filter": filter,
    "float": float,
    "format": format,
    "frozenset": frozenset,
    "hash": hash,
    "hex": hex,
    "int": int,
    "isinstance": isinstance,
    "issubclass": issubclass,
    "iter": iter,
    "len": len,
    "list": list,
    "map": map,
    "max": max,
    "min": min,
    "next": next,
    "oct": oct,
    "ord": ord,
    "pow": pow,
    "print": print,
    "range": range,
    "repr": repr,
    "reversed": reversed,
    "round": round,
    "set": set,
    "slice": slice,
    "sorted": sorted,
    "str": str,
    "sum": sum,
    "tuple": tuple,
    "zip": zip,
    "__import__": safe_import,
}

payload = json.loads(Path("payload.json").read_text(encoding="utf-8"))
code = payload["code"]
namespace = {"__builtins__": SAFE_BUILTINS, "__name__": "__jarvis_action__"}

try:
    exec(compile(code, "<jarvis_action>", "exec"), namespace, namespace)
except BaseException:
    traceback.print_exc()
    raise SystemExit(1)
'''


class RestrictedPythonTool:
    """
    Executes approved Python snippets in an isolated subprocess.

    This is a strong application-level restriction layer, not a substitute for
    a container, VM, or operating-system sandbox when running hostile code.
    """

    name = "python"

    def __init__(
        self,
        *,
        timeout_seconds: float = 4.0,
        max_code_chars: int = 12_000,
        max_output_chars: int = 24_000,
        memory_limit_mb: int = 256,
    ) -> None:
        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")
        self.timeout_seconds = timeout_seconds
        self.max_code_chars = max_code_chars
        self.max_output_chars = max_output_chars
        self.memory_limit_mb = memory_limit_mb

    def run(self, payload: str) -> ToolResult:
        started = time.monotonic()
        code = payload.strip()
        if not code:
            return self._policy_failure("Python action contained no code.", started)
        if len(code) > self.max_code_chars:
            return self._policy_failure(
                f"Python action exceeded {self.max_code_chars} characters.",
                started,
            )

        try:
            tree = ast.parse(code, filename="<jarvis_action>", mode="exec")
            _PolicyValidator().visit(tree)
        except (SyntaxError, PythonPolicyError) as error:
            return self._policy_failure(
                f"{error.__class__.__name__}: {error}",
                started,
            )

        with tempfile.TemporaryDirectory(prefix="jarvis-python-") as temp_name:
            temp_dir = Path(temp_name)
            (temp_dir / "payload.json").write_text(
                json.dumps({"code": code}, ensure_ascii=False),
                encoding="utf-8",
            )
            runner_path = temp_dir / "runner.py"
            runner_path.write_text(_RUNNER_SOURCE, encoding="utf-8")

            environment = {
                "HOME": str(temp_dir),
                "LANG": "C.UTF-8",
                "LC_ALL": "C.UTF-8",
                "PATH": "",
                "PYTHONDONTWRITEBYTECODE": "1",
                "PYTHONHASHSEED": "0",
                "PYTHONNOUSERSITE": "1",
                "TMPDIR": str(temp_dir),
            }

            try:
                completed = subprocess.run(
                    [sys.executable, "-I", "-S", str(runner_path)],
                    cwd=temp_dir,
                    env=environment,
                    capture_output=True,
                    text=True,
                    timeout=self.timeout_seconds,
                    check=False,
                    start_new_session=True,
                    preexec_fn=self._limit_resources if os.name == "posix" else None,
                )
            except subprocess.TimeoutExpired as error:
                elapsed = self._elapsed_ms(started)
                stdout = self._trim(error.stdout or "")
                stderr = self._trim(error.stderr or "")
                traceback_text = (
                    f"TimeoutError: Python action exceeded "
                    f"{self.timeout_seconds:.2f} seconds."
                )
                return ToolResult(
                    success=False,
                    stdout=stdout,
                    stderr=stderr,
                    traceback=traceback_text,
                    duration_ms=elapsed,
                    metadata={"failure": "timeout"},
                )

        stdout = self._trim(completed.stdout)
        stderr = self._trim(completed.stderr)
        success = completed.returncode == 0
        traceback_text = "" if success else (stderr or "Python action failed.")
        return ToolResult(
            success=success,
            stdout=stdout,
            stderr=stderr,
            traceback=traceback_text,
            duration_ms=self._elapsed_ms(started),
            metadata={
                "return_code": str(completed.returncode),
                "executor": "isolated-subprocess",
            },
        )

    def _policy_failure(self, message: str, started: float) -> ToolResult:
        return ToolResult(
            success=False,
            stdout="",
            stderr=message,
            traceback=message,
            duration_ms=self._elapsed_ms(started),
            metadata={"failure": "policy"},
        )

    def _trim(self, value: str) -> str:
        if len(value) <= self.max_output_chars:
            return value
        removed = len(value) - self.max_output_chars
        return value[: self.max_output_chars] + f"\n...[truncated {removed} chars]"

    @staticmethod
    def _elapsed_ms(started: float) -> int:
        return int((time.monotonic() - started) * 1_000)

    def _limit_resources(self) -> None:
        try:
            import resource

            memory_bytes = self.memory_limit_mb * 1024 * 1024
            resource.setrlimit(resource.RLIMIT_CPU, (3, 3))
            resource.setrlimit(resource.RLIMIT_AS, (memory_bytes, memory_bytes))
            resource.setrlimit(resource.RLIMIT_FSIZE, (1_048_576, 1_048_576))
            resource.setrlimit(resource.RLIMIT_NOFILE, (16, 16))
            resource.setrlimit(resource.RLIMIT_NPROC, (0, 0))
        except (ImportError, OSError, ValueError):
            # Timeout, isolated mode, scrubbed environment, and AST policy remain
            # active on platforms that do not expose every POSIX resource limit.
            return
