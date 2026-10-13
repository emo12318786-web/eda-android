import os
import tempfile
import unittest

from agent_core import EdaAgent, ToolResult, _safe_calculate


class AgentCoreTests(unittest.TestCase):
    def test_calculator(self):
        self.assertEqual(_safe_calculate("12 * (3 + 2)").data, 60)
        self.assertFalse(_safe_calculate("__import__('os')").ok)

    def test_local_note_round_trip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "memory.json")
            agent = EdaAgent(lambda _: '{"reply":"done","tool":null,"args":{},"done":true}',
                             memory_path=path)
            saved = agent._save_note(text="قرار تعمیرگاه")
            found = agent._search_notes(query="تعمیرگاه")
            self.assertTrue(saved.ok)
            self.assertEqual(len(found.data), 1)

    def test_unknown_tool_not_executed(self):
        agent = EdaAgent(lambda _: '{"reply":"","tool":"delete_everything","args":{},"done":true}')
        self.assertIn("ابزار مجازی ندارم", agent.run("همه چیز را حذف کن"))


if __name__ == "__main__":
    unittest.main()
