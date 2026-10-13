# -*- coding: utf-8 -*-
"""Eda Agent Core: lightweight, local-first tool planning for the existing Eda project.

This module intentionally has no mandatory LangChain/LangGraph dependency. Pass Eda's
existing AI function as llm=ai_sor. Tools are explicit allow-listed Python callables.
High-impact actions should be implemented as confirmation-gated tools by the host app.
"""
from __future__ import annotations

import ast
import json
import operator
import os
import re
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Callable, Dict, Optional


@dataclass
class ToolResult:
    ok: bool
    message: str
    data: Any = None


@dataclass
class Tool:
    name: str
    description: str
    parameters: Dict[str, str]
    function: Callable[..., ToolResult]


def _safe_calculate(expression: str) -> ToolResult:
    """Evaluate basic arithmetic only; never use eval()."""
    allowed_ops = {
        ast.Add: operator.add, ast.Sub: operator.sub, ast.Mult: operator.mul,
        ast.Div: operator.truediv, ast.FloorDiv: operator.floordiv,
        ast.Mod: operator.mod, ast.Pow: operator.pow, ast.USub: operator.neg,
        ast.UAdd: operator.pos,
    }
    try:
        tree = ast.parse(expression, mode="eval")
        def visit(node):
            if isinstance(node, ast.Expression):
                return visit(node.body)
            if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
                return node.value
            if isinstance(node, ast.BinOp) and type(node.op) in allowed_ops:
                left, right = visit(node.left), visit(node.right)
                if isinstance(node.op, ast.Pow) and abs(right) > 8:
                    raise ValueError("توان بیش از حد بزرگ است.")
                return allowed_ops[type(node.op)](left, right)
            if isinstance(node, ast.UnaryOp) and type(node.op) in allowed_ops:
                return allowed_ops[type(node.op)](visit(node.operand))
            raise ValueError("فقط محاسبات عددی ساده مجاز است.")
        value = visit(tree)
        if isinstance(value, float) and not (float("-inf") < value < float("inf")):
            raise ValueError("نتیجه عدد معتبر نیست.")
        return ToolResult(True, str(value), value)
    except Exception as exc:
        return ToolResult(False, f"محاسبه انجام نشد: {exc}")


class EdaAgent:
    """A bounded plan -> tool -> verify loop.

    llm(prompt) must return text. It may be Eda's existing ai_sor function.
    State is deliberately small; long-term facts are stored in a local JSON file.
    """

    def __init__(self, llm: Callable[[str], str], memory_path: Optional[str] = None,
                 max_steps: int = 3):
        self.llm = llm
        self.memory_path = memory_path or os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "agent_memory.json"
        )
        self.max_steps = max(1, min(int(max_steps), 3))
        self.tools: Dict[str, Tool] = {}
        self._register_builtin_tools()

    def register_tool(self, tool: Tool) -> None:
        if not re.fullmatch(r"[a-z][a-z0-9_]{1,40}", tool.name):
            raise ValueError("نام ابزار نامعتبر است.")
        self.tools[tool.name] = tool

    def _register_builtin_tools(self) -> None:
        self.register_tool(Tool(
            "get_time", "زمان فعلی دستگاه را برمی‌گرداند.", {},
            lambda **_: ToolResult(True, datetime.now().strftime("%H:%M:%S"))
        ))
        self.register_tool(Tool(
            "get_date", "تاریخ میلادی دستگاه را برمی‌گرداند.", {},
            lambda **_: ToolResult(True, datetime.now().strftime("%Y-%m-%d"))
        ))
        self.register_tool(Tool(
            "calculate", "محاسبه ریاضی امن؛ پارامتر expression.", {"expression": "string"},
            lambda expression="", **_: _safe_calculate(str(expression)[:200])
        ))
        self.register_tool(Tool(
            "save_note", "یادداشت محلی را ذخیره می‌کند؛ پارامتر text.", {"text": "string"},
            self._save_note
        ))
        self.register_tool(Tool(
            "search_notes", "در یادداشت‌های محلی جست‌وجو می‌کند؛ پارامتر query.", {"query": "string"},
            self._search_notes
        ))

    def _load_memory(self):
        try:
            with open(self.memory_path, "r", encoding="utf-8") as file:
                data = json.load(file)
            return data if isinstance(data, list) else []
        except (OSError, ValueError):
            return []

    def _save_note(self, text="", **_) -> ToolResult:
        note = str(text).strip()[:2000]
        if not note:
            return ToolResult(False, "متن یادداشت خالی است.")
        notes = self._load_memory()
        notes.append({"created_at": datetime.now().isoformat(timespec="seconds"), "text": note})
        notes = notes[-500:]
        try:
            os.makedirs(os.path.dirname(os.path.abspath(self.memory_path)), exist_ok=True)
            temp = self.memory_path + ".tmp"
            with open(temp, "w", encoding="utf-8") as file:
                json.dump(notes, file, ensure_ascii=False, indent=2)
            os.replace(temp, self.memory_path)
            return ToolResult(True, "یادداشت روی دستگاه ذخیره شد.", {"count": len(notes)})
        except OSError as exc:
            return ToolResult(False, f"ذخیره یادداشت ناموفق بود: {exc}")

    def _search_notes(self, query="", **_) -> ToolResult:
        q = str(query).strip().casefold()
        if not q:
            return ToolResult(False, "عبارت جست‌وجو خالی است.")
        matches = [n for n in self._load_memory() if q in str(n.get("text", "")).casefold()]
        return ToolResult(True, f"{len(matches)} یادداشت پیدا شد.", matches[-10:])

    @staticmethod
    def _extract_json(text: str) -> Optional[dict]:
        if not isinstance(text, str):
            return None
        cleaned = text.strip()
        cleaned = re.sub(r"^\s*```(?:json)?\s*|\s*```\s*$", "", cleaned, flags=re.I)
        try:
            value = json.loads(cleaned)
            return value if isinstance(value, dict) else None
        except ValueError:
            match = re.search(r"\{.*\}", cleaned, flags=re.S)
            if not match:
                return None
            try:
                value = json.loads(match.group(0))
                return value if isinstance(value, dict) else None
            except ValueError:
                return None

    def _plan(self, user_text: str, history: list) -> dict:
        tool_list = [
            {"name": t.name, "description": t.description, "parameters": t.parameters}
            for t in self.tools.values()
        ]
        prompt = (
            "You are Eda, a cautious Persian-speaking Android assistant. "
            "Choose at most one allow-listed tool per step. Never claim an action succeeded "
            "unless its tool result says ok=true. Do not invent sensor/API data. "
            "For requests requiring unavailable tools, explain the limitation. "
            "Return ONLY valid JSON with keys reply (string), tool (string or null), "
            "args (object), done (boolean). Use tool=null if no tool is needed.\n"
            f"Available tools: {json.dumps(tool_list, ensure_ascii=False)}\n"
            f"Recent steps: {json.dumps(history[-4:], ensure_ascii=False)}\n"
            f"User request: {user_text[:2000]}"
        )
        return self._extract_json(self.llm(prompt)) or {
            "reply": str(self.llm(user_text))[:4000], "tool": None, "args": {}, "done": True
        }

    def run(self, user_text: str) -> str:
        text = str(user_text).strip()
        if not text:
            return "بگو چه کاری برات انجام بدم."
        history = []
        for _ in range(self.max_steps):
            plan = self._plan(text, history)
            tool_name = plan.get("tool")
            if not tool_name:
                return str(plan.get("reply") or "درخواستت رو بررسی کردم.")[:4000]
            tool = self.tools.get(str(tool_name))
            if tool is None:
                history.append({"tool": str(tool_name), "ok": False, "result": "ابزار مجاز نیست."})
                return "برای این کار ابزار مجازی ندارم؛ چیزی اجرا نکردم."
            args = plan.get("args")
            if not isinstance(args, dict):
                args = {}
            try:
                result = tool.function(**{k: v for k, v in args.items() if k in tool.parameters})
                if not isinstance(result, ToolResult):
                    result = ToolResult(True, str(result), result)
            except Exception as exc:
                result = ToolResult(False, f"خطای ابزار: {exc}")
            history.append({
                "tool": tool.name, "ok": result.ok, "result": result.message,
                "data": result.data
            })
            if not result.ok:
                return f"نتونستم کار رو کامل کنم: {result.message}"
            # Ask the model to verify/report the actual tool result. A next tool call is
            # allowed only within max_steps; no arbitrary Python/code execution is possible.
            if len(history) >= self.max_steps:
                return f"ابزار اجرا شد: {result.message}"
        return "کار به سقف مراحل مجاز رسید؛ برای جلوگیری از حلقه، متوقف شدم."


def build_eda_agent(ai_sor: Callable[[str], str], memory_path: Optional[str] = None) -> EdaAgent:
    """Convenience adapter: EdaAgent(ai_sor) for the existing Eda Python project."""
    return EdaAgent(llm=ai_sor, memory_path=memory_path)
