"""Tests for Heroku/Hikka/FTG userbot compatibility layer."""

import asyncio
import json
import os
import sys
import tempfile
import pytest

from pathlib import Path

# Add python path
sys.path.insert(0, str(Path(__file__).parent.parent / "TMessagesProj" / "src" / "main" / "python"))

from heroku_compat import loader, utils
from heroku_compat.module_runner import _FakeMessage, run_command, run_filter, has_filter


def test_loader_decorators_and_config():
    @loader.tds
    class SampleMod(loader.Module):
        strings = {"name": "Sample", "greeting": "Hello, {name}!"}

        def __init__(self):
            super().__init__()
            # Test FTG triple-style config
            self.config = loader.ModuleConfig(
                "custom_name", "Ame", "Name to greet",
                "count", 5, "Number of times"
            )

        @loader.command("testcmd")
        @loader.ratelimit
        @loader.owner
        async def my_cmd(self, message):
            name = self.config["custom_name"]
            await utils.answer(message, self.strings("greeting").format(name=name))

    mod = SampleMod()
    assert mod.strings("name") == "Sample"
    assert mod.config["custom_name"] == "Ame"
    assert mod.config["count"] == 5
    assert mod.config.get("nonexistent", "def") == "def"
    assert getattr(mod.my_cmd, "__heroku_cmd__", False) is True
    assert getattr(mod.my_cmd, "__heroku_cmd_name__") == "testcmd"


def test_loader_hikka_config_values():
    cfg = loader.ModuleConfig(
        loader.ConfigValue("enabled", True, "Is active", loader.validators.Boolean()),
        loader.ConfigValue("choice", "a", "Choice", loader.validators.Choice(["a", "b"]))
    )
    assert cfg["enabled"] is True
    assert cfg["choice"] == "a"
    cfg["choice"] = "b"
    assert cfg["choice"] == "b"


def test_utils_arg_parsing():
    msg = _FakeMessage(".tr en Hello world", "en Hello world")
    assert utils.get_args_raw(msg) == "en Hello world"
    assert utils.get_args(msg) == ["en", "Hello", "world"]

    assert utils.get_args_raw("single_word") == ""
    assert utils.get_args(".cmd") == []
    assert utils.get_args_split_by(".tag a|b|c", "|") == ["a", "b", "c"]
    assert utils.escape_html("<script>") == "&lt;script&gt;"


def test_fake_message_replies():
    msg = _FakeMessage(".ping", "", reply_text="Original reply", reply_id=42, chat_id=-100123456)
    assert msg.is_reply is True
    assert msg.reply_to_msg_id == 42
    assert msg.is_channel is True

    async def _test():
        await msg.edit("Edited text")
        await msg.respond("New response")
        await msg.delete()
        reply = await msg.get_reply_message()
        assert reply is not None
        assert reply.raw_text == "Original reply"

    asyncio.run(_test())
    assert msg.replies == [["edit", "Edited text"], ["respond", "New response"], ["delete", ""]]


def test_real_module_execution():
    code = """
from .. import loader, utils

@loader.tds
class EchoMod(loader.Module):
    \"\"\"Echoes text back.\"\"\"
    strings = {"name": "Echo"}

    async def echocmd(self, message):
        args = utils.get_args_raw(message)
        if not args:
            await utils.answer(message, "Empty!")
            return
        await utils.answer(message, f"Echo: {args}")
"""
    with tempfile.NamedTemporaryFile("w", suffix=".py", delete=False, encoding="utf-8") as f:
        f.write(code)
        tmp_path = f.name

    try:
        res_raw = run_command(tmp_path, "echo", "test string", ".echo test string")
        res = json.loads(res_raw)
        assert res["error"] is None
        assert res["replies"] == [["edit", "Echo: test string"]]
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
