"""Heroku & Hikka utils compatibility layer for Miogram."""

import html
import os
import random
import shlex
import string
from typing import Any, List, Optional


def get_platform_name() -> str:
    """Get the platform name running the userbot."""
    return "📱 Miogram (Android Native)"


def get_version_raw() -> str:
    """Get userbot version string."""
    return "2.1.0"


def get_base_dir() -> str:
    """Get root base directory."""
    return os.path.dirname(os.path.abspath(__file__))


def get_git_info() -> List[str]:
    """Get git info."""
    return ["miogram-native", "https://github.com/exteraless/amegram"]


def get_args_raw(message_or_text: Any) -> str:
    """Extract raw arguments after command name."""
    if hasattr(message_or_text, "message"):
        text = message_or_text.message
    elif hasattr(message_or_text, "raw_text"):
        text = message_or_text.raw_text
    elif hasattr(message_or_text, "text"):
        text = message_or_text.text
    elif isinstance(message_or_text, str):
        text = message_or_text
    else:
        text = str(message_or_text or "")

    if not text:
        return ""
    parts = text.split(maxsplit=1)
    return parts[1] if len(parts) > 1 else ""


def get_args(message_or_text: Any) -> List[str]:
    """Extract tokenized arguments as a list."""
    raw = get_args_raw(message_or_text)
    if not raw:
        return []
    try:
        split = shlex.split(raw)
        return [s for s in split if s]
    except Exception:
        return [s for s in raw.split() if s]


def get_args_split_by(message_or_text: Any, sep: str) -> List[str]:
    """Split raw args by custom separator."""
    raw = get_args_raw(message_or_text)
    if not raw:
        return []
    return [section.strip() for section in raw.split(sep) if section.strip()]


def get_chat_id(message: Any) -> int:
    """Extract chat ID from message object."""
    if hasattr(message, "chat_id"):
        return message.chat_id
    if hasattr(message, "to_id"):
        return getattr(message.to_id, "chat_id", getattr(message.to_id, "user_id", 0))
    return 0


def rand(length: int = 6) -> str:
    """Generate random string of characters."""
    return "".join(random.choices(string.ascii_letters + string.digits, k=length))


def escape_html(text: str) -> str:
    """HTML escape string."""
    return html.escape(str(text or ""))


def ascii_face() -> str:
    """Return random kaomoji ascii face."""
    faces = [
        "(´･ω･`)", "(っ˘ω˘ς )", "(✿◠‿◠)", "(•‿•)",
        "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "( ͡° ͜ʖ ͡°)"
    ]
    return random.choice(faces)


async def answer(message: Any, text: str, **kwargs):
    """Answers a message: edits if outgoing or responds if incoming."""
    if hasattr(message, "edit"):
        try:
            return await message.edit(text, **kwargs)
        except Exception:
            pass
    if hasattr(message, "respond"):
        return await message.respond(text, **kwargs)
    if hasattr(message, "reply"):
        return await message.reply(text, **kwargs)
    return None
