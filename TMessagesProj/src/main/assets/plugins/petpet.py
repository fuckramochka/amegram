from __future__ import annotations

import io
import os
import re
import struct
import time
import zlib
from base64 import b64decode
from typing import Any, List

import requests
from PIL import Image

from java import jarray, jint, jlong
from java.io import FileOutputStream
from java.lang import Long
from java.util.concurrent import CountDownLatch, TimeUnit

from android.graphics import Bitmap, BitmapFactory, Canvas
from android.media import MediaMetadataRetriever
from base_plugin import (
    BasePlugin,
    HookResult,
    HookStrategy,
    MenuItemData,
    MenuItemType,
)
from client_utils import (
    GLOBAL_QUEUE,
    get_client,
    get_file_loader,
    get_last_fragment,
    get_messages_controller,
    run_on_queue,
    send_request,
)
from file_utils import ensure_dir_exists, get_cache_dir
from org.telegram.messenger import (
    AndroidUtilities,
    ChatObject,
    DialogObject,
    FileLoader,
    ImageLocation,
    LocaleController,
    MessageObject,
)
from org.telegram.tgnet import TLRPC
from org.telegram.ui import ChatActivity
from org.telegram.ui.Components import AvatarDrawable, RLottieNative
from ui.bulletin import BulletinHelper
from ui.settings import Header, Selector, Switch, Text

__id__ = "petpet"
__name__ = "petpet"
__description__ = "Creates a petpet gif from avatars, photos, stickers, or image URLs"
__author__ = "@limeplug"
__version__ = "1.0.6"
__icon__ = "masquernya/14"
__app_version__ = ">=12.5.1"
__sdk_version__ = ">=1.4.4.3"

COMMANDS = (".petpet", ".петпет")
FRAMES = 10
DELAY_VALUES = (20, 30, 40, 50, 70, 100)
RESOLUTION_VALUES = (64, 128, 256, 512)
DOWNLOAD_TIMEOUT_SEC = 20
RESOLVE_TIMEOUT_SEC = 12
MAX_URL_BYTES = 8 * 1024 * 1024
RUSSIAN_FALLBACK_LANGUAGES = frozenset("ru be kk ky uz tg tt ba".split())
USERNAME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]{3,31}$")
URL_RE = re.compile(r"^https?://", re.I)

HAND_FRAMES_B64 = """\
eNrt3GVUlWG3L/zVdCMtLLqR7lggIA1SkgKCSEuXgItFd4d0d3fqoktgoTQCi04VA0QUPO5xxrvP
O95n7Ofs/e7zYZ89uD7en+55319+Y17/OQEA/LcP1FUlpWzcAe6Ac8C/HA4qXB1BVisV8WAdKZ0H
xm6GqoG2xt5OzihznTh70wJPowgXm1SXJ7Fe9mm+zrF+3llBHmUvXNPDrHPDfUtjnFujHUuyXJuT
XUpiglojAlrLA8ZKA/LiwyoTUb0J4eWpEbWpkQ25qLLM+IaM2IacxNZXCQ0Fqe0FKY2l0T3lkX3N
kfWl2e0lGb1lGW/q0joqcnoqctpqCntqCl43lL7rjRuvzh5uKBluqRzuqBnraZjqb5h53YIZ6lwa
68ZMDDL/hEBBf4tg/kOgo2JocF9RT0VYQBAM/PuI71+K+5cyAbAbBNJdjY8uabzMc8JFuhBT5a1R
b9a4XOf78yTwDVYgaWnqvOPwu+Bb2I9AGQudgp1XC/6Oveu/LvxlbGV8vH1lpe1sY318pKOfBZgq
BAfZPA9+aSsdHxspGhkd+zRSqlxYuDA35aGC3QuXbMvnoTLSYbEeneElJVJhXlJVnqKSMlm1amkh
WVmPn6e9nUPmJrZHRc0nJi4lSuV6DYlivb3GVVrSMdNzs3Lyn+Vftra3r27sSkr/vJr32vaUdChK
FQqDCdGJgCYFcQB3ZfyyttXzLvfv5pluKn0pCmMtGkAO4cfmKXraTaBwNd/ppc3YiDpQ8Q7m/hx6
Uvl8V8OnzGOn7Ga2EBE2YxK9XzwXLZI2o7xqa4AoFizRyJBmHSWkka/HvxtbcziX9SldICILr0Qh
1aCyINoDNBpDzrna4k5W6hZHw2+N7adn5Y/v47PbXR7kY5QZejJiL22o8Wyy/+FOZrZLSXjMZJrI
s9G6bggeBae8buIU6ATLRzrG8jh/uYKlkr1+0efXxfDeZKc68gzd2/l5Ex0QxTWAUolrMNHv7Mg2
2Si8iznfkqQdguLPFKqHYz4xh+OQ0yrp4aYU90V9PXyfVUkaGvyTNHWtoa+lT77P+TxPvt7sugOP
fDDgTBFGqQdXDIV2+GFRn5iz4S9IlJcf+xekoJWUvq8srdIjbwQXUx78bndff8rm8IAxgFKNZiuW
TAO9HScgj0HHE3EJ7oSuxUSCIo0cqkxkUZJPMeqLpva0eEzhZwMBtF1qO2l6qtzlIHy2ZGTCToZJ
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
vd/PIxzSFw1JjOftnTJHnrqBy9/kz7v+9J9F6f7epv9zOa77+mvW7O99mT8/MUmvv1fqXh+b/7la
nn190Tl7/enFn19YkjeXY7o3X3P//D7UfXO1NHtz0fvn+izpze8D3T9X5gq4ABkAAL/7v5tn8UC3
eL3F6y1eb/F6i9dbvN7i9Ravt3j9b4pXgu3/hdeLf8Dr/5Tr/z+21mb4tuUG/jvl+pet/5ZZe9sz
/7J1qDZ2tCP+X+U601b6F69vO2r/p18xfWX/StiNhe6tt2V/Ibs/OwT4v+38P83k/4VvnBskABUW
HhEZFR0TGxefkJiUnJKalp6RmZX9Kic3L7+gsKi4pLSsvKKyqrqmtq6+obGpuaW1rb2js6tcQbY7
5f4b9MDg0LD8fYUB+UkpeSkZqf4ohfGh94vv5RfHllaWFVbWl6dlZvekMN0D79GrCojNyY2lz2eT
Fz9+zMxKSRxICd97/FZaVW9d3PkzEtO775YzGHh5qsjm11CogpRwGz8biF29F45PIVzW2WQzHo5A
r+yEfqYnyaS8tgxnvg+Lp+Y3DGA7/GQ3G7XINvptRW+FmGk3scXiOOzBu+A7IqvhHhTP8EPLCBtk
8Ci3JDz4mWpEa5I+TFVo26pOc2XwxFPdcJtBtBQloD1aKyiyrZjU2okCRVbY6A9Rx/fllPxOdabv
XePGPBrfBTPbPqhwJYmCBEiE1x7MfmYZpchvc7Gp0qftLb/qErPIA3Lb5s9/M1FDKyieRNJ82SPe
jLnGS1pd3jekHjev8z8dbP+w7M4NvDnV++G24WAlHRyoE0lESze0NH9I1U1wh2emUPLhdnjM+YlR
V6YLC9Q1bb7eAOfFZd37jzpf3mebjz70ycM/yyiyxmMVlQThwiWXC/AOF56B8FjKrJXYWI4OmK2j
ip9m+q6y26cRPK9HPiF4zowDRhGK49rgiPha7yTclXQYjGCzvgzFlVHa3kq2skkzjLk5vMHGp4Im
MSiCs/ofGy/O5HCVcKi9kQNQcgvAANgKxa2YJS69DI+kse8qZr0feJguDCz49vZJ7QTyiddhfac1
HumFKWsWefhPBJiIrwkAiiTnsc+UKDgqxUNI61vmhgQbmDlx8mQdBLI1SSglEV4E2GaWeeIBiwko
A7lAMAYEGogLz3ECk8arcySQhQgO4rBJlhCWKw+0DmkZMkfDX+CbqbM2iHse1dVj5Juc6poZ9dCA
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
"""

PLUGIN_STRINGS = {
    "ru": {
        "menu": "Petpet",
        "menu_sub": "Сделать гифку, где рука гладит это фото",
        "profile_sub": "Сделать гифку из аватарки",
        "chat_sub": "Погладить аватарку этого чата",
        "header": "Petpet",
        "enable_command": "Команда .petpet",
        "enable_command_sub": "Отправьте .petpet или .петпет. Можно ответить на фото, указать @user или URL",
        "enable_message_menu": "Пункт в меню сообщения",
        "enable_message_menu_sub": "Долгое нажатие на сообщение",
        "enable_profile_menu": "Пункт в профиле",
        "enable_profile_menu_sub": "Меню действий профиля пользователя или канала",
        "enable_chat_menu": "Пункт в меню чата",
        "enable_chat_menu_sub": "Погладить аватарку текущего чата",
        "prefer_media": "Брать фото из сообщения",
        "prefer_media_sub": "Если в сообщении есть картинка или стикер — гладить её, иначе аватарку",
        "reply": "Отвечать на исходное сообщение",
        "reply_sub": "Отправлять гифку ответом",
        "delay": "Задержка кадра",
        "resolution": "Разрешение гифки",
        "how": "Как пользоваться",
        "how_sub": "Ответьте на сообщение и отправьте .petpet, выберите пункт в меню или укажите .petpet @user / URL",
        "making": "Делаю petpet…",
        "success": "Petpet готов",
        "error_generic": "Не удалось сделать petpet",
        "error_no_image": "Нет картинки: ответьте на фото, укажите @user или URL",
        "error_no_chat": "Откройте чат и попробуйте снова",
        "error_secret": "Petpet недоступен в секретных чатах",
        "error_cant_send": "Сюда нельзя отправлять сообщения",
        "error_busy": "Уже делается другая гифка, подождите",
        "error_user": "Не удалось найти пользователя",
        "error_download": "Не удалось скачать изображение",
        "error_url": "Не удалось загрузить картинку по ссылке",
        "usage": "Использование: .petpet [@user|url] — или ответом на сообщение",
    },
    "en": {
        "menu": "Petpet",
        "menu_sub": "Make a gif of a hand petting this photo",
        "profile_sub": "Make a gif from this avatar",
        "chat_sub": "Pet this chat's avatar",
        "header": "Petpet",
        "enable_command": "Command .petpet",
        "enable_command_sub": "Send .petpet. Reply to a photo, or pass @user / a URL",
        "enable_message_menu": "Message menu item",
        "enable_message_menu_sub": "Long-press a message",
        "enable_profile_menu": "Profile menu item",
        "enable_profile_menu_sub": "User or channel profile actions",
        "enable_chat_menu": "Chat menu item",
        "enable_chat_menu_sub": "Pet the current chat avatar",
        "prefer_media": "Prefer photo in the message",
        "prefer_media_sub": "Pet the image or sticker; otherwise use the sender avatar",
        "reply": "Reply to the source message",
        "reply_sub": "Send the gif as a reply",
        "delay": "Frame delay",
        "resolution": "Gif resolution",
        "how": "How to use",
        "how_sub": "Reply to a message and send .petpet, use the menu, or run .petpet @user / URL",
        "making": "Making petpet…",
        "success": "Petpet is ready",
        "error_generic": "Could not make petpet",
        "error_no_image": "No image: reply to a photo, pass @user, or a URL",
        "error_no_chat": "Open a chat and try again",
        "error_secret": "Petpet is not available in secret chats",
        "error_cant_send": "You can't send messages in this chat",
        "error_busy": "Another petpet is already running",
        "error_user": "Could not find that user",
        "error_download": "Could not download the image",
        "error_url": "Could not load the image URL",
        "usage": "Usage: .petpet [@user|url] — or reply to a message",
    },
}

_HAND_FRAMES = None
_RESAMPLE = None
_QUANTIZE = None
_DITHER = None
_LAST_CLEANUP = 0


def locale_code():
    try:
        controller = LocaleController.getInstance()
        locale_info = controller.getCurrentLocaleInfo()
        language_code = str(getattr(locale_info, "shortName", "") or "")
        if not language_code:
            language_code = str(controller.getCurrentLocale().getLanguage() or "")
    except Exception:
        language_code = ""
    primary = str(language_code or "").strip().lower().replace("_", "-").split("-", 1)[0]
    if primary in RUSSIAN_FALLBACK_LANGUAGES:
        return "ru"
    return "en"


def tr(key):
    return PLUGIN_STRINGS[locale_code()][key]


def as_int(value, default=0):
    if value is None:
        return default
    try:
        return int(value.longValue())
    except Exception:
        pass
    try:
        return int(value)
    except Exception:
        return default


def as_bool(value, default=False):
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in ("1", "true", "yes", "on")
    try:
        return bool(value)
    except Exception:
        return default


def as_path(value):
    if value is None:
        return None
    try:
        if hasattr(value, "getAbsolutePath"):
            return str(value.getAbsolutePath())
    except Exception:
        pass
    text = str(value)
    return text if text and text != "None" else None


def resample_filter():
    global _RESAMPLE
    if _RESAMPLE is not None:
        return _RESAMPLE
    try:
        _RESAMPLE = Image.Resampling.LANCZOS
    except Exception:
        _RESAMPLE = getattr(Image, "LANCZOS", Image.BICUBIC)
    return _RESAMPLE


def quantize_method():
    global _QUANTIZE
    if _QUANTIZE is not None:
        return _QUANTIZE
    method = getattr(Image, "FASTOCTREE", None)
    if method is None:
        quantize = getattr(Image, "Quantize", None)
        method = getattr(quantize, "FASTOCTREE", 2) if quantize is not None else 2
    _QUANTIZE = method
    return _QUANTIZE


def dither_none():
    global _DITHER
    if _DITHER is not None:
        return _DITHER
    dither = getattr(Image, "Dither", None)
    if dither is not None:
        _DITHER = getattr(dither, "NONE", 0)
    else:
        _DITHER = getattr(Image, "NONE", 0)
    return _DITHER


def work_dir():
    path = os.path.join(as_path(get_cache_dir()) or "/tmp", "petpet")
    ensure_dir_exists(path)
    return path


def cleanup_old_files():
    global _LAST_CLEANUP
    now = time.time()
    if now - _LAST_CLEANUP < 3600:
        return
    _LAST_CLEANUP = now
    directory = work_dir()
    try:
        names = os.listdir(directory)
    except Exception:
        return
    for name in names:
        path = os.path.join(directory, name)
        try:
            if now - os.path.getmtime(path) > 3600:
                os.remove(path)
        except Exception:
            pass


def load_hand_frames():
    global _HAND_FRAMES
    if _HAND_FRAMES is not None:
        return _HAND_FRAMES
    raw = zlib.decompress(b64decode(HAND_FRAMES_B64.encode("ascii")))
    frames = []
    offset = 0
    while offset + 4 <= len(raw):
        size = struct.unpack_from(">I", raw, offset)[0]
        offset += 4
        chunk = raw[offset : offset + size]
        offset += size
        image = Image.open(io.BytesIO(chunk))
        image.load()
        frames.append(image.convert("RGBA"))
    if len(frames) != FRAMES:
        raise RuntimeError("expected %d hand frames, got %d" % (FRAMES, len(frames)))
    _HAND_FRAMES = frames
    return frames


def squish_box(index, resolution):
    bounce = index if index < FRAMES / 2.0 else FRAMES - index
    width = 0.8 + bounce * 0.02
    height = 0.8 - bounce * 0.05
    offset_x = (1 - width) * 0.5 + 0.1
    offset_y = 1 - height - 0.08
    box_w = max(1, int(width * resolution))
    box_h = max(1, int(height * resolution))
    pos_x = int(offset_x * resolution)
    pos_y = int(offset_y * resolution)
    return pos_x, pos_y, box_w, box_h


def compose_frame(index, avatar, hands, resolution):
    canvas = Image.new("RGBA", (resolution, resolution), (0, 0, 0, 0))
    pos_x, pos_y, box_w, box_h = squish_box(index, resolution)
    squeezed = avatar.resize((box_w, box_h), resample_filter())
    canvas.paste(squeezed, (pos_x, pos_y), squeezed)
    canvas.paste(hands[index], (0, 0), hands[index])
    return canvas


def shared_palette(avatar, hands, resolution):
    palette_size = min(120, resolution)
    image = Image.new("RGBA", (palette_size, palette_size * 2), (0, 0, 0, 0))
    avatar_w = max(1, int(0.8 * palette_size))
    avatar_h = max(1, int(0.8 * palette_size))
    fitted = avatar.resize((avatar_w, avatar_h), resample_filter())
    image.paste(fitted, (0, palette_size), fitted)
    hand = hands[0].resize((palette_size, palette_size), resample_filter())
    image.paste(hand, (0, 0), hand)
    return image.convert("RGB").quantize(
        colors=255, method=quantize_method(), dither=dither_none()
    )


_ALPHA_MASK_LUT = bytes([255] * 8 + [0] * 248)


def apply_transparency(paletted, rgba):
    mask = rgba.getchannel("A").point(_ALPHA_MASK_LUT)
    paletted.paste(Image.new("P", paletted.size, 255), mask=mask)
    paletted.info["transparency"] = 255
    return paletted


def render_petpet(avatar_image, resolution, delay):
    cleanup_old_files()
    source_hands = load_hand_frames()
    resample = resample_filter()
    avatar = avatar_image.convert("RGBA")
    limit = max(resolution * 2, 160)
    if max(avatar.size) > limit:
        avatar.thumbnail((limit, limit), resample)
    palette = shared_palette(avatar, source_hands, resolution)
    hands = [frame.resize((resolution, resolution), resample) for frame in source_hands]
    frames = []
    dither = dither_none()
    for index in range(FRAMES):
        rgba = compose_frame(index, avatar, hands, resolution)
        paletted = rgba.convert("RGB").quantize(palette=palette, dither=dither)
        frames.append(apply_transparency(paletted, rgba))
    output = os.path.join(work_dir(), "petpet_%d.gif" % int(time.time() * 1000))
    frames[0].save(
        output,
        save_all=True,
        append_images=frames[1:],
        duration=max(20, int(delay)),
        loop=0,
        disposal=2,
        transparency=255,
        optimize=False,
    )
    return output


def open_image(source):
    image = Image.open(source)
    image.load()
    return image.convert("RGBA")


def open_loaded(path):
    if not path:
        return None
    try:
        return open_image(path)
    except Exception:
        return image_from_bitmap(path)


def image_from_bitmap(path):
    text = as_path(path)
    if not text:
        return None
    try:
        bitmap = BitmapFactory.decodeFile(text)
    except Exception:
        return None
    if bitmap is None:
        return None
    try:
        return open_image(save_bitmap(bitmap, "decoded"))
    except Exception:
        return None
    finally:
        try:
            bitmap.recycle()
        except Exception:
            pass


def file_ready(path):
    text = as_path(path)
    if not text:
        return False
    try:
        return os.path.isfile(text) and os.path.getsize(text) > 32
    except Exception:
        return False


def wait_for_file(path, timeout=DOWNLOAD_TIMEOUT_SEC, refresh=None):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if refresh is not None:
            try:
                path = refresh() or path
            except Exception:
                pass
        if file_ready(path):
            return as_path(path)
        time.sleep(0.15)
    return as_path(path) if file_ready(path) else None


def load_attach(account, attach, parent, location=None, ext="jpg"):
    if attach is None and location is None:
        return None
    loader = get_file_loader(account)

    def current_path():
        if attach is None:
            return None
        try:
            if isinstance(attach, TLRPC.Document):
                return loader.getPathToAttach(attach)
            return loader.getPathToAttach(attach, True)
        except Exception:
            return None

    path = current_path()
    if file_ready(path):
        return as_path(path)
    try:
        if location is not None:
            loader.loadFile(location, parent, ext, FileLoader.PRIORITY_HIGH, jint(1))
        elif attach is not None:
            loader.loadFile(attach, parent, FileLoader.PRIORITY_HIGH, jint(0))
    except Exception:
        try:
            if location is not None:
                loader.loadFile(location, parent, ext, FileLoader.PRIORITY_HIGH, 1)
        except Exception:
            return as_path(path) if file_ready(path) else None
    return wait_for_file(path, refresh=current_path)


def save_bitmap(bitmap, prefix):
    if bitmap is None:
        return None
    path = os.path.join(work_dir(), "%s_%d.png" % (prefix, int(time.time() * 1000)))
    stream = FileOutputStream(path)
    try:
        ok = bitmap.compress(Bitmap.CompressFormat.PNG, jint(100), stream)
    finally:
        try:
            stream.close()
        except Exception:
            pass
    if not ok or not file_ready(path):
        return None
    return path


def render_avatar_placeholder(account, peer, size):
    drawable = AvatarDrawable()
    try:
        drawable.setInfo(jint(account), peer)
    except Exception:
        drawable.setInfo(peer)
    bitmap = Bitmap.createBitmap(jint(size), jint(size), Bitmap.Config.ARGB_8888)
    canvas = Canvas(bitmap)
    drawable.setBounds(jint(0), jint(0), jint(size), jint(size))
    drawable.draw(canvas)
    return save_bitmap(bitmap, "avatar")


def image_from_peer(account, peer, resolution):
    location = ImageLocation.getForUserOrChat(jint(account), peer, ImageLocation.TYPE_BIG)
    attach = None
    if location is not None:
        attach = getattr(location, "photoSize", None) or getattr(location, "location", None)
    image = open_loaded(load_attach(account, attach, peer, location=location, ext="jpg"))
    if image is not None:
        return image
    placeholder = render_avatar_placeholder(account, peer, max(resolution, 160))
    return open_loaded(placeholder)


def closest_size(thumbs):
    if thumbs is None:
        return None
    try:
        return FileLoader.getClosestPhotoSizeWithSize(thumbs, AndroidUtilities.getPhotoSize())
    except Exception:
        try:
            return FileLoader.getClosestPhotoSizeWithSize(thumbs, jint(1280))
        except Exception:
            return None


def image_from_photo_thumbs(account, message, thumbs, parent):
    size = closest_size(thumbs)
    if size is None:
        return None
    location = None
    try:
        location = ImageLocation.getForObject(size, parent)
    except Exception:
        location = None
    if location is None and parent is not None:
        try:
            if isinstance(parent, TLRPC.Photo):
                location = ImageLocation.getForPhoto(size, parent)
            elif isinstance(parent, TLRPC.Document):
                location = ImageLocation.getForDocument(size, parent)
        except Exception:
            location = None
    path = load_attach(account, size, message or parent, location=location, ext="jpg")
    return open_loaded(path)


def sticker_kind(document):
    if document is None:
        return None
    mime = str(getattr(document, "mime_type", "") or "").lower()
    try:
        if MessageObject.isAnimatedStickerDocument(document, True):
            return "lottie"
        if MessageObject.isVideoStickerDocument(document):
            return "video"
        if MessageObject.isStickerDocument(document):
            return "video" if mime == "video/webm" else "image"
    except Exception:
        pass
    if mime in ("application/x-tgsticker", "application/x-tgsdice"):
        return "lottie"
    return None


def image_from_lottie(path, size=512):
    text = as_path(path)
    if not text:
        return None
    meta = jarray(jint)([0, 0, 0])
    try:
        ptr = RLottieNative.create(text, None, jint(size), jint(size), meta, False, None, False, 0)
    except Exception:
        return None
    if not ptr:
        return None
    bitmap = None
    try:
        count = int(meta[0]) if int(meta[0]) > 0 else 1
        frame = count // 3
        bitmap = Bitmap.createBitmap(jint(size), jint(size), Bitmap.Config.ARGB_8888)
        if RLottieNative.getFrame(ptr, jint(frame), bitmap, True) == -5:
            return None
        return open_image(save_bitmap(bitmap, "lottie"))
    except Exception:
        return None
    finally:
        if bitmap is not None:
            try:
                bitmap.recycle()
            except Exception:
                pass
        try:
            RLottieNative.destroy(ptr)
        except Exception:
            pass


def image_from_video_frame(path):
    text = as_path(path)
    if not text:
        return None
    retriever = MediaMetadataRetriever()
    bitmap = None
    try:
        retriever.setDataSource(text)
        bitmap = retriever.getFrameAtTime(jlong(0), MediaMetadataRetriever.OPTION_CLOSEST)
        if bitmap is None:
            bitmap = retriever.getFrameAtTime(jlong(0))
    except Exception:
        return None
    finally:
        try:
            retriever.release()
        except Exception:
            pass
    if bitmap is None:
        return None
    try:
        return open_image(save_bitmap(bitmap, "sticker"))
    except Exception:
        return None
    finally:
        try:
            bitmap.recycle()
        except Exception:
            pass


def image_from_document(account, message, document):
    if document is None:
        return None
    mime = str(getattr(document, "mime_type", "") or "").lower()
    file_name = str(getattr(document, "file_name", "") or "").lower()
    kind = sticker_kind(document)
    is_image = mime in ("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif") or file_name.endswith(
        (".jpg", ".jpeg", ".png", ".webp", ".gif")
    )
    if is_image or kind is not None:
        ext = "jpg"
        if kind == "lottie":
            ext = "tgs"
        elif kind == "video":
            ext = "webm"
        elif "png" in mime:
            ext = "png"
        elif "webp" in mime or kind == "image":
            ext = "webp"
        elif "gif" in mime:
            ext = "gif"
        path = load_attach(account, document, message, ext=ext)
        if kind == "lottie":
            image = image_from_lottie(path)
        elif kind == "video":
            image = image_from_video_frame(path)
        else:
            image = open_loaded(path)
        if image is not None:
            return image
    thumbs = getattr(document, "thumbs", None)
    return image_from_photo_thumbs(account, message, thumbs, document)


def image_from_message(account, message):
    if message is None or not isinstance(message, MessageObject):
        return None
    document = None
    try:
        document = message.getDocument()
    except Exception:
        document = None
    if sticker_kind(document) is not None:
        image = image_from_document(account, message, document)
        if image is not None:
            return image
    image = image_from_photo_thumbs(
        account, message, getattr(message, "photoThumbs", None), getattr(message, "photoThumbsObject", None)
    )
    if image is not None:
        return image
    return image_from_document(account, message, document)


def sender_of(account, message):
    if message is None:
        return None
    try:
        peer = message.getFromPeerObject()
        if peer is not None:
            return peer
    except Exception:
        pass
    try:
        return peer_from_dialog(account, as_int(message.getSenderId(), 0))
    except Exception:
        return None


def peer_from_dialog(account, dialog_id):
    controller = get_messages_controller(account)
    if dialog_id > 0:
        return controller.getUser(Long.valueOf(jlong(dialog_id)))
    if dialog_id < 0:
        return controller.getChat(Long.valueOf(jlong(-dialog_id)))
    return None


def download_url(url):
    response = requests.get(
        url,
        timeout=DOWNLOAD_TIMEOUT_SEC,
        headers={"User-Agent": "exteragram-petpet/1.0"},
        stream=True,
    )
    response.raise_for_status()
    content_type = str(response.headers.get("Content-Type") or "").lower()
    if content_type and not content_type.startswith("image/") and "octet-stream" not in content_type:
        raise ValueError("not an image")
    data = io.BytesIO()
    total = 0
    for chunk in response.iter_content(64 * 1024):
        if not chunk:
            continue
        total += len(chunk)
        if total > MAX_URL_BYTES:
            raise ValueError("image too large")
        data.write(chunk)
    data.seek(0)
    return open_image(data)


TME_PREFIXES = (
    "https://t.me/",
    "http://t.me/",
    "https://telegram.me/",
    "http://telegram.me/",
    "tg://resolve?domain=",
)
TME_SKIP = frozenset(
    "c addstickers proxy socks joinchat s share boost login iv".split()
)


def username_from_text(text):
    value = (text or "").strip()
    if not value:
        return None
    lower = value.lower()
    for prefix in TME_PREFIXES:
        if lower.startswith(prefix):
            rest = value[len(prefix) :].split("/")[0].split("?")[0].lstrip("@")
            if rest and rest.lower() not in TME_SKIP and not rest.isdigit():
                return rest
            return None
    if value.startswith("@"):
        value = value[1:]
    if USERNAME_RE.fullmatch(value):
        return value
    return None


def lookup_cached_peer(account, username):
    try:
        return get_messages_controller(account).getUserOrChat(username)
    except Exception:
        return None


def resolve_username(account, username):
    cached = lookup_cached_peer(account, username)
    if cached is not None:
        return cached
    req = TLRPC.TL_contacts_resolveUsername()
    req.username = username
    latch = CountDownLatch(1)
    box = {}

    def on_done(response, error):
        box["response"] = response
        box["error"] = error
        latch.countDown()

    send_request(req, on_done, account=account)
    finished = getattr(latch, "await")(RESOLVE_TIMEOUT_SEC, TimeUnit.SECONDS)
    if not finished:
        return None
    if box.get("error") is not None or box.get("response") is None:
        return None
    response = box["response"]
    controller = get_messages_controller(account)
    try:
        controller.putUsers(response.users, False)
        controller.putChats(response.chats, False)
    except Exception:
        pass
    return lookup_cached_peer(account, username)


def parse_command(text):
    raw = (text or "").strip()
    if not raw:
        return None
    parts = raw.split(None, 1)
    command = parts[0].lower()
    if command not in COMMANDS:
        return None
    argument = parts[1].strip() if len(parts) > 1 else ""
    return argument


def can_send_to(account, dialog_id):
    if not dialog_id:
        return tr("error_no_chat")
    try:
        if DialogObject.isEncryptedDialog(jlong(dialog_id)):
            return tr("error_secret")
    except Exception:
        return tr("error_generic")
    if dialog_id < 0:
        try:
            chat = get_messages_controller(account).getChat(Long.valueOf(jlong(-dialog_id)))
            if chat is not None and not ChatObject.canSendMessages(chat):
                return tr("error_cant_send")
        except Exception:
            pass
    return None


def notify(kind, message):
    if kind == "error":
        BulletinHelper.show_error(message)
    elif kind == "success":
        BulletinHelper.show_success(message)
    else:
        BulletinHelper.show_info(message)


def send_gif(account, dialog_id, path, reply):
    client = get_client(account)
    peer = as_int(dialog_id, 0)
    if reply is not None:
        client.send_document(peer, path, caption="", replyToMsg=reply)
        return
    client.send_document(peer, path, "")


class PetpetPlugin(BasePlugin):
    def on_plugin_load(self):
        self._busy = False
        self.add_on_send_message_hook()
        self.add_menu_item(
            MenuItemData(
                menu_type=MenuItemType.MESSAGE_CONTEXT_MENU,
                text=tr("menu"),
                on_click=self.on_message_petpet,
                item_id="petpet_message",
                icon="msg_gif",
                subtext=tr("menu_sub"),
                condition="message != null",
            )
        )
        self.add_menu_item(
            MenuItemData(
                menu_type=MenuItemType.PROFILE_ACTION_MENU,
                text=tr("menu"),
                on_click=self.on_profile_petpet,
                item_id="petpet_profile",
                icon="msg_gif",
                subtext=tr("profile_sub"),
                condition="user != null || chat != null",
            )
        )
        self.add_menu_item(
            MenuItemData(
                menu_type=MenuItemType.CHAT_ACTION_MENU,
                text=tr("menu"),
                on_click=self.on_chat_petpet,
                item_id="petpet_chat",
                icon="msg_gif",
                subtext=tr("chat_sub"),
            )
        )
        self.log("petpet loaded")

    def create_settings(self) -> List[Any]:
        delay_items = ["%d ms" % value for value in DELAY_VALUES]
        resolution_items = ["%d px" % value for value in RESOLUTION_VALUES]
        return [
            Header(text=tr("header")),
            Switch(
                key="enable_command",
                text=tr("enable_command"),
                default=True,
                subtext=tr("enable_command_sub"),
            ),
            Switch(
                key="enable_message_menu",
                text=tr("enable_message_menu"),
                default=True,
                subtext=tr("enable_message_menu_sub"),
            ),
            Switch(
                key="enable_profile_menu",
                text=tr("enable_profile_menu"),
                default=True,
                subtext=tr("enable_profile_menu_sub"),
            ),
            Switch(
                key="enable_chat_menu",
                text=tr("enable_chat_menu"),
                default=True,
                subtext=tr("enable_chat_menu_sub"),
            ),
            Switch(
                key="prefer_media",
                text=tr("prefer_media"),
                default=True,
                subtext=tr("prefer_media_sub"),
            ),
            Switch(
                key="reply",
                text=tr("reply"),
                default=True,
                subtext=tr("reply_sub"),
            ),
            Selector(
                key="delay",
                text=tr("delay"),
                default=0,
                items=delay_items,
                icon="msg_customize",
            ),
            Selector(
                key="resolution",
                text=tr("resolution"),
                default=1,
                items=resolution_items,
                icon="msg_photos",
            ),
            Text(text=tr("how"), subtext=tr("how_sub"), icon="msg_info"),
        ]

    def _flag(self, key, default=True):
        return as_bool(self.get_setting(key, default), default)

    def _choice(self, key, values, default_index):
        index = as_int(self.get_setting(key, default_index), default_index)
        if index < 0 or index >= len(values):
            return values[default_index]
        return values[index]

    def delay_ms(self):
        return self._choice("delay", DELAY_VALUES, 0)

    def resolution(self):
        return self._choice("resolution", RESOLUTION_VALUES, 1)

    def _reply_to(self, message):
        if message is not None and self._flag("reply"):
            return message
        return None

    def _peer_from_context(self, context):
        return context.get("user") or context.get("chat")

    def _image_from_peer_or_raise(self, account, peer):
        if peer is None:
            raise RuntimeError(tr("error_user"))
        image = image_from_peer(account, peer, self.resolution())
        if image is None:
            raise RuntimeError(tr("error_download"))
        return image

    def on_send_message_hook(self, account: int, params: Any) -> HookResult:
        if not self._flag("enable_command"):
            return HookResult()
        if not isinstance(getattr(params, "message", None), str):
            return HookResult()
        argument = parse_command(params.message)
        if argument is None:
            return HookResult()
        if argument.lower() in ("help", "?", "помощь"):
            notify("info", tr("usage"))
            return HookResult(strategy=HookStrategy.CANCEL)
        reply = getattr(params, "replyToMsg", None)
        self._start(
            account,
            as_int(getattr(params, "peer", 0), 0),
            get_last_fragment(),
            message=reply,
            argument=argument,
            reply=self._reply_to(reply),
        )
        return HookResult(strategy=HookStrategy.CANCEL)

    def on_message_petpet(self, context: dict):
        if not self._flag("enable_message_menu"):
            return
        message = context.get("message")
        self._start(
            as_int(context.get("account"), 0),
            as_int(context.get("dialog_id"), 0),
            context.get("fragment"),
            message=message,
            reply=self._reply_to(message),
        )

    def on_profile_petpet(self, context: dict):
        if not self._flag("enable_profile_menu"):
            return
        peer = self._peer_from_context(context)
        dialog_id = as_int(context.get("dialog_id"), 0)
        if not dialog_id and peer is not None:
            peer_id = as_int(getattr(peer, "id", 0), 0)
            if isinstance(peer, TLRPC.Chat):
                dialog_id = -peer_id
            else:
                dialog_id = peer_id
        self._start(
            as_int(context.get("account"), 0),
            dialog_id,
            context.get("fragment"),
            peer=peer,
        )

    def on_chat_petpet(self, context: dict):
        if not self._flag("enable_chat_menu"):
            return
        self._start(
            as_int(context.get("account"), 0),
            as_int(context.get("dialog_id"), 0),
            context.get("fragment"),
            peer=self._peer_from_context(context),
        )

    def _start(self, account, dialog_id, fragment, message=None, peer=None, argument="", reply=None):
        account = as_int(account, 0)
        dialog_id = as_int(dialog_id, 0)
        if not dialog_id:
            if not isinstance(fragment, ChatActivity):
                fragment = get_last_fragment()
            if isinstance(fragment, ChatActivity):
                try:
                    dialog_id = as_int(fragment.getDialogId(), 0)
                except Exception:
                    dialog_id = 0
        error = can_send_to(account, dialog_id)
        if error:
            notify("error", error)
            return
        if self._busy:
            notify("error", tr("error_busy"))
            return
        self._busy = True
        notify("info", tr("making"))
        job = {
            "account": account,
            "dialog_id": dialog_id,
            "message": message,
            "peer": peer,
            "argument": argument or "",
            "reply": reply,
        }
        run_on_queue(lambda: self._run(job), GLOBAL_QUEUE)

    def _run(self, job):
        try:
            image = self._resolve_image(job)
            if image is None:
                notify("error", tr("error_no_image"))
                return
            path = render_petpet(image, self.resolution(), self.delay_ms())
            send_gif(job["account"], job["dialog_id"], path, job.get("reply"))
            notify("success", tr("success"))
        except Exception as error:
            self.log("petpet failed: %s" % error)
            message = str(error) if isinstance(error, RuntimeError) else tr("error_generic")
            notify("error", message or tr("error_generic"))
        finally:
            self._busy = False

    def _resolve_image(self, job):
        account = job["account"]
        argument = (job.get("argument") or "").strip()
        message = job.get("message")
        peer = job.get("peer")

        if argument:
            token = argument.split()[0]
            username = username_from_text(token)
            if username:
                return self._image_from_peer_or_raise(
                    account, resolve_username(account, username)
                )
            if URL_RE.match(token):
                try:
                    return download_url(token)
                except Exception as error:
                    self.log("petpet url failed: %s" % error)
                    raise RuntimeError(tr("error_url"))
            if token.isdigit():
                return self._image_from_peer_or_raise(
                    account, peer_from_dialog(account, int(token))
                )
            raise RuntimeError(tr("error_no_image"))

        if self._flag("prefer_media") and message is not None:
            image = image_from_message(account, message)
            if image is not None:
                return image

        if peer is None and message is not None:
            peer = sender_of(account, message)
        if peer is None:
            peer = peer_from_dialog(account, job["dialog_id"])
        if peer is None:
            return None
        return image_from_peer(account, peer, self.resolution())
