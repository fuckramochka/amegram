"""
id: chat_exporter
name: Експорт видалених повідомлень (Telegram Web UI + Медіа + Реакції)
version: 2.1.0
author: Community
description: Експортує всі видалені чати у форматі Telegram Web з медіа (фото, стікери, аудіо), реакціями, маленькою іконкою кошика та автоматично відправляє у Збережені.
icon: msg_shareout
"""

from base_plugin import BasePlugin, MenuItemData, MenuItemType
from ui.settings import Header, Text, Divider
from ui.bulletin import BulletinHelper
from java import jclass

import sqlite3
import os
import shutil
import datetime
import html
import json
import base64

__id__ = "chat_exporter"
__name__ = "Експорт видалених повідомлень (Telegram Web UI)"
__version__ = "2.1.0"
__author__ = "Community"
__description__ = "Експорт чатів з медіа, реакціями, кошиком та відправкою у Збережені"
__icon__ = "msg_shareout"
__app_version__ = ">=12.5.0"
__sdk_version__ = ">=1.0.0"
__permissions__ = ["files", "ui"]


HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="uk">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Telegram — Збережені чати</title>
<style>
  :root {
    --bg-main: #0e1621;
    --sidebar-bg: #17212b;
    --sidebar-hover: #202b36;
    --sidebar-active: #2b5278;
    --text-primary: #f5f5f5;
    --text-secondary: #7e8b9b;
    --bubble-in: #182533;
    --bubble-out: #2b5278;
    --accent: #5288c1;
    --time-text: rgba(255, 255, 255, 0.45);
    --border-color: rgba(255, 255, 255, 0.08);
  }
  * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
  body {
    background-color: var(--bg-main);
    color: var(--text-primary);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    height: 100vh;
    overflow: hidden;
  }
  .app-container {
    display: flex;
    width: 100vw;
    height: 100vh;
  }
  /* --- Левая колонка: список чатов --- */
  .sidebar {
    width: 360px;
    background-color: var(--sidebar-bg);
    border-right: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    transition: transform 0.2s ease;
  }
  .sidebar-header {
    padding: 14px 16px;
    border-bottom: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .sidebar-title {
    font-size: 19px;
    font-weight: 700;
    color: var(--text-primary);
  }
  .search-box {
    width: 100%;
    background: #242f3d;
    border: none;
    border-radius: 20px;
    padding: 9px 16px;
    font-size: 14px;
    color: var(--text-primary);
    outline: none;
  }
  .search-box::placeholder { color: var(--text-secondary); }
  .chat-list {
    flex: 1;
    overflow-y: auto;
  }
  .chat-item {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    cursor: pointer;
    border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    transition: background 0.15s;
    user-select: none;
  }
  .chat-item:hover { background-color: var(--sidebar-hover); }
  .chat-item.active { background-color: var(--sidebar-active); }
  .avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: #5288c1;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    font-size: 18px;
    margin-right: 14px;
    flex-shrink: 0;
  }
  .chat-item-content {
    flex: 1;
    min-width: 0;
  }
  .chat-item-row {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 4px;
  }
  .chat-item-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .chat-item-time {
    font-size: 12px;
    color: var(--text-secondary);
    margin-left: 8px;
  }
  .chat-item-preview {
    font-size: 13.5px;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .badge {
    background: #5288c1;
    color: #fff;
    border-radius: 12px;
    padding: 2px 7px;
    font-size: 11px;
    font-weight: 700;
    margin-left: 6px;
  }

  /* --- Правая колонка: окно открытого чата --- */
  .chat-view {
    flex: 1;
    display: flex;
    flex-direction: column;
    background-color: var(--bg-main);
    background-image: radial-gradient(circle at 50% 50%, #151f2b 0%, #0c141d 100%);
    position: relative;
    min-width: 0;
  }
  .chat-view-header {
    height: 60px;
    background: var(--sidebar-bg);
    border-bottom: 1px solid var(--border-color);
    display: flex;
    align-items: center;
    padding: 0 16px;
    gap: 12px;
    z-index: 10;
  }
  .back-btn {
    display: none;
    background: none;
    border: none;
    color: var(--text-primary);
    font-size: 24px;
    cursor: pointer;
    padding: 4px 8px;
  }
  .chat-view-info {
    flex: 1;
  }
  .chat-view-name {
    font-size: 16px;
    font-weight: 600;
  }
  .chat-view-sub {
    font-size: 12px;
    color: var(--text-secondary);
  }
  .messages-area {
    flex: 1;
    overflow-y: auto;
    padding: 20px 24px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .date-separator {
    align-self: center;
    background: rgba(0, 0, 0, 0.35);
    color: #fff;
    font-size: 12px;
    padding: 4px 12px;
    border-radius: 12px;
    margin: 12px 0 6px 0;
    backdrop-filter: blur(4px);
  }
  .msg-row {
    display: flex;
    width: 100%;
  }
  .msg-row.in { justify-content: flex-start; }
  .msg-row.out { justify-content: flex-end; }
  .bubble {
    max-width: 68%;
    padding: 8px 12px;
    border-radius: 12px;
    position: relative;
    font-size: 14.5px;
    line-height: 1.4;
    word-break: break-word;
    box-shadow: 0 1px 2px rgba(0,0,0,0.25);
  }
  .msg-row.in .bubble {
    background-color: var(--bubble-in);
    border-bottom-left-radius: 4px;
  }
  .msg-row.out .bubble {
    background-color: var(--bubble-out);
    border-bottom-right-radius: 4px;
  }
  .msg-author {
    font-size: 12.5px;
    font-weight: 600;
    color: #64b5f6;
    margin-bottom: 3px;
  }
  .msg-quote {
    border-left: 3px solid #64b5f6;
    background: rgba(0, 0, 0, 0.2);
    padding: 4px 8px;
    border-radius: 2px 6px 6px 2px;
    font-size: 12px;
    color: var(--text-secondary);
    margin-bottom: 6px;
  }
  .msg-text {
    white-space: pre-wrap;
  }
  
  /* Медиа контент */
  .msg-img {
    max-width: 100%;
    max-height: 380px;
    border-radius: 8px;
    display: block;
    margin-top: 5px;
    object-fit: cover;
  }
  .msg-sticker {
    max-width: 140px;
    max-height: 140px;
    display: block;
    margin-top: 4px;
  }
  .msg-video {
    max-width: 100%;
    max-height: 320px;
    border-radius: 8px;
    display: block;
    margin-top: 5px;
  }
  .msg-audio {
    width: 100%;
    max-width: 280px;
    margin-top: 5px;
    display: block;
  }
  .msg-media-badge {
    display: inline-block;
    background: rgba(255,255,255,0.08);
    border-radius: 6px;
    padding: 4px 8px;
    font-size: 12px;
    margin-top: 5px;
    color: #e0e0e0;
  }

  /* Метаданные: время + маленькая корзинка */
  .msg-meta {
    float: right;
    display: flex;
    align-items: center;
    gap: 4px;
    margin-left: 10px;
    margin-top: 4px;
    user-select: none;
  }
  .trash-icon {
    width: 11px;
    height: 11px;
    fill: #e53935;
    opacity: 0.85;
    display: inline-block;
  }
  .msg-time {
    font-size: 11px;
    color: var(--time-text);
  }

  /* Реакции */
  .msg-reactions {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 6px;
  }
  .reaction-pill {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    background: rgba(255, 255, 255, 0.08);
    padding: 2px 7px;
    border-radius: 10px;
    font-size: 12px;
    user-select: none;
  }
  .reaction-pill.self {
    background: rgba(82, 136, 193, 0.3);
    border: 1px solid rgba(82, 136, 193, 0.5);
  }

  .placeholder-box {
    margin: auto;
    text-align: center;
    color: var(--text-secondary);
    font-size: 15px;
  }

  @media (max-width: 768px) {
    .sidebar { width: 100vw; }
    .chat-view { display: none; width: 100vw; }
    .app-container.chat-open .sidebar { display: none; }
    .app-container.chat-open .chat-view { display: flex; }
    .back-btn { display: block; }
    .bubble { max-width: 85%; }
  }
</style>
</head>
<body>
<div class="app-container" id="appContainer">
  <div class="sidebar">
    <div class="sidebar-header">
      <div class="sidebar-title">Збережені чати</div>
      <input type="text" class="search-box" id="searchInput" placeholder="Пошук по чатах..." oninput="filterChats()">
    </div>
    <div class="chat-list" id="chatList"></div>
  </div>

  <div class="chat-view">
    <div class="chat-view-header" id="chatHeader" style="display: none;">
      <button class="back-btn" onclick="closeChat()">←</button>
      <div class="chat-view-info">
        <div class="chat-view-name" id="activeChatName"></div>
        <div class="chat-view-sub" id="activeChatSub"></div>
      </div>
    </div>
    <div class="messages-area" id="messagesArea">
      <div class="placeholder-box" id="placeholder">Оберіть чат зі списку, щоб відкрити переписку</div>
    </div>
  </div>
</div>

<script>
const CHATS_DATA = __CHATS_DATA_JSON__;
let activeChatId = null;

const AVATAR_COLORS = [
  "#e53935", "#d81b60", "#8e24aa", "#5e35b1",
  "#3949ab", "#1e88e5", "#039be5", "#00acc1",
  "#00897b", "#43a047", "#7cb342", "#fb8c00"
];

function getAvatarColor(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash);
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

function getInitials(name) {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return name.slice(0, 2).toUpperCase();
}

function escapeHtml(text) {
  if (!text) return "";
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function renderChatList(filter = "") {
  const listEl = document.getElementById("chatList");
  listEl.innerHTML = "";

  const chatKeys = Object.keys(CHATS_DATA);
  if (chatKeys.length === 0) {
    listEl.innerHTML = '<div style="padding: 20px; text-align: center; color: var(--text-secondary);">Немає збережених чатів</div>';
    return;
  }

  chatKeys.forEach(id => {
    const chat = CHATS_DATA[id];
    if (filter && !chat.title.toLowerCase().includes(filter.toLowerCase())) return;

    const item = document.createElement("div");
    item.className = "chat-item" + (activeChatId === id ? " active" : "");
    item.onclick = () => openChat(id);

    const color = getAvatarColor(chat.title);
    const initials = getInitials(chat.title);

    item.innerHTML = `
      <div class="avatar" style="background-color: ${color}">${initials}</div>
      <div class="chat-item-content">
        <div class="chat-item-row">
          <div class="chat-item-title">${escapeHtml(chat.title)}</div>
          <div class="chat-item-time">${escapeHtml(chat.lastTime || "")}</div>
        </div>
        <div class="chat-item-row">
          <div class="chat-item-preview">${escapeHtml(chat.lastPreview || "Повідомлення")}</div>
          <span class="badge">${chat.messages.length}</span>
        </div>
      </div>
    `;
    listEl.appendChild(item);
  });
}

// Маленькая векторная иконка корзины (trash)
const TRASH_SVG = `<svg class="trash-icon" viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>`;

function openChat(id) {
  activeChatId = id;
  const chat = CHATS_DATA[id];
  if (!chat) return;

  document.getElementById("appContainer").classList.add("chat-open");
  document.getElementById("chatHeader").style.display = "flex";
  document.getElementById("activeChatName").textContent = chat.title;
  document.getElementById("activeChatSub").textContent = `${chat.messages.length} збережених повідомлень`;

  const area = document.getElementById("messagesArea");
  area.innerHTML = "";

  let lastDate = "";
  chat.messages.forEach(msg => {
    if (msg.date && msg.date !== lastDate) {
      lastDate = msg.date;
      const sep = document.createElement("div");
      sep.className = "date-separator";
      sep.textContent = msg.date;
      area.appendChild(sep);
    }

    const row = document.createElement("div");
    row.className = "msg-row " + (msg.isOut ? "out" : "in");

    let quoteHtml = "";
    if (msg.quote) {
      quoteHtml = `<div class="msg-quote">↳ ${escapeHtml(msg.quote)}</div>`;
    }

    // Отрисовка медиа (картинка, стикер, аудио, видео или бейдж)
    let mediaHtml = "";
    if (msg.mediaData) {
      const md = msg.mediaData;
      if (md.type === "image") {
        mediaHtml = `<img class="msg-img" src="${md.src}" alt="Фото">`;
      } else if (md.type === "sticker") {
        mediaHtml = `<img class="msg-sticker" src="${md.src}" alt="Стікер">`;
      } else if (md.type === "audio") {
        mediaHtml = `<audio controls class="msg-audio" src="${md.src}"></audio>`;
      } else if (md.type === "video") {
        mediaHtml = `<video controls class="msg-video" src="${md.src}"></video>`;
      } else if (md.type === "badge") {
        mediaHtml = `<div class="msg-media-badge">${escapeHtml(md.text)}</div>`;
      }
    } else if (msg.mediaName) {
      mediaHtml = `<div class="msg-media-badge">📎 ${escapeHtml(msg.mediaName)}</div>`;
    }

    let authorHtml = "";
    if (!msg.isOut && msg.author) {
      authorHtml = `<div class="msg-author">${escapeHtml(msg.author)}</div>`;
    }

    // Реакции
    let reactionsHtml = "";
    if (msg.reactions && msg.reactions.length > 0) {
      const pills = msg.reactions.map(r => 
        `<span class="reaction-pill ${r.self ? 'self' : ''}">${r.emoticon} ${r.count > 1 ? r.count : ''}</span>`
      ).join("");
      reactionsHtml = `<div class="msg-reactions">${pills}</div>`;
    }

    row.innerHTML = `
      <div class="bubble">
        ${authorHtml}
        ${quoteHtml}
        ${mediaHtml}
        <div class="msg-text">${escapeHtml(msg.text)}</div>
        <div class="msg-meta">
          ${TRASH_SVG}
          <span class="msg-time">${escapeHtml(msg.time)}</span>
        </div>
        ${reactionsHtml}
      </div>
    `;
    area.appendChild(row);
  });

  area.scrollTop = area.scrollHeight;
  renderChatList(document.getElementById("searchInput").value);
}

function closeChat() {
  document.getElementById("appContainer").classList.remove("chat-open");
  activeChatId = null;
  renderChatList(document.getElementById("searchInput").value);
}

function filterChats() {
  const q = document.getElementById("searchInput").value;
  renderChatList(q);
}

renderChatList();
const keys = Object.keys(CHATS_DATA);
if (keys.length === 1) {
  openChat(keys[0]);
}
</script>
</body>
</html>
"""


class ChatExporterPlugin(BasePlugin):

    def on_plugin_load(self):
        self.log("ChatExporterPlugin loaded.")
        try:
            self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.CHAT_ACTION_MENU,
                    text="Відправити чат у Збережені (HTML)",
                    on_click=self.on_chat_menu_click,
                    item_id="export_chat_saved",
                    icon="msg_shareout",
                    subtext="Надіслати HTML у Saved Messages"
                )
            )
        except Exception as e:
            self.log(f"Failed to add chat menu item: {e}")

        try:
            self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.DRAWER_MENU,
                    text="Експорт у Збережені (Telegram Web)",
                    on_click=self.on_drawer_menu_click,
                    item_id="export_all_drawer",
                    icon="msg_shareout"
                )
            )
        except Exception as e:
            self.log(f"Failed to add drawer menu item: {e}")

    def create_settings(self):
        return [
            Header(text="Порятунок даних та експорт"),
            Text(
                text="Відправити ВСЕ у Збережені (HTML + База)",
                subtext="Створює зручний веб-інтерфейс із медіа та реакціями і надсилає у Saved Messages",
                accent=True,
                on_click=self.export_all_click
            ),
            Text(
                text="Надіслати тільки базу (ayu-data.db) у Збережені",
                subtext="Файл бази для відновлення в новому Yumigram через меню Імпорту",
                on_click=self.export_database_click
            ),
            Divider(text="Куди також зберігається копія"),
            Text(
                text="Дублікат зберігається у пам'ять:",
                subtext="Папка «Download» (Завантаження) вашого телефону",
                on_click=None
            )
        ]

    def _get_downloads_dir(self):
        try:
            Environment = jclass("android.os.Environment")
            path = str(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath())
            if os.path.exists(path):
                return path
        except Exception:
            pass
        fallback = "/sdcard/Download"
        os.makedirs(fallback, exist_ok=True)
        return fallback

    def _get_db_path(self):
        ApplicationLoader = jclass("org.telegram.messenger.ApplicationLoader")
        ctx = ApplicationLoader.applicationContext
        return str(ctx.getDatabasePath("ayu-data").getAbsolutePath())

    def _checkpoint_db(self):
        try:
            AyuData = jclass("com.radolyn.ayugram.database.AyuData")
            AyuData.checkpointDatabase()
        except Exception as e:
            self.log(f"Checkpoint ignored: {e}")

    def _get_dialog_title(self, dialog_id, fallback_name=""):
        try:
            UserConfig = jclass("org.telegram.messenger.UserConfig")
            MessagesController = jclass("org.telegram.messenger.MessagesController")
            curr_acc = int(UserConfig.selectedAccount)
            mc = MessagesController.getInstance(curr_acc)
            if dialog_id > 0:
                user = mc.getUser(jclass("java.lang.Long").valueOf(int(dialog_id)))
                if user:
                    fn = str(user.first_name or "")
                    ln = str(user.last_name or "")
                    full = (fn + " " + ln).strip()
                    if full:
                        return full
                    if user.username:
                        return "@" + str(user.username)
            else:
                chat = mc.getChat(jclass("java.lang.Long").valueOf(int(-dialog_id)))
                if chat and chat.title:
                    return str(chat.title)
        except Exception:
            pass
        if fallback_name:
            return fallback_name
        return f"Діалог {dialog_id}"

    def _send_file_to_saved_messages(self, file_path, caption="", mime_type="application/octet-stream"):
        try:
            UserConfig = jclass("org.telegram.messenger.UserConfig")
            AccountInstance = jclass("org.telegram.messenger.AccountInstance")
            SendMessagesHelper = jclass("org.telegram.messenger.SendMessagesHelper")

            curr_acc = int(UserConfig.selectedAccount)
            ai = AccountInstance.getInstance(curr_acc)
            my_id = int(UserConfig.getInstance(curr_acc).getClientUserId())

            SendMessagesHelper.prepareSendingDocument(
                ai,
                str(file_path),
                str(file_path),
                None,
                str(caption),
                str(mime_type),
                my_id,
                None, None, None, None, None,
                True, 0, None, None, False
            )
            return True
        except Exception as e:
            self.log(f"Direct send failed: {e}")
            try:
                LaunchActivity = jclass("org.telegram.ui.LaunchActivity")
                ShareUtil = jclass("tw.nekomimi.nekogram.utils.ShareUtil")
                File = jclass("java.io.File")
                act = LaunchActivity.instance
                if act is not None:
                    ShareUtil.shareFile(act, File(file_path), str(caption))
                    return True
            except Exception as e2:
                self.log(f"Fallback share failed: {e2}")
            return False

    def export_database_click(self, view=None):
        try:
            self._checkpoint_db()
            db_path = self._get_db_path()
            if not os.path.exists(db_path):
                BulletinHelper.show_error("База даних ayu-data не знайдена!")
                return

            downloads_dir = self._get_downloads_dir()
            out_file = os.path.join(downloads_dir, "ayu-data.db")
            shutil.copyfile(db_path, out_file)

            time_str = datetime.datetime.now().strftime("%d.%m.%Y %H:%M")
            caption = f"💾 Резервна копія бази Yumigram (ayu-data.db)\n📅 Створено: {time_str}\n\nЗбережіть цей файл. У новому Yumigram оберіть «Імпорт бази даних» і виберіть його, щоб повернути всі чати!"
            sent = self._send_file_to_saved_messages(out_file, caption=caption, mime_type="application/octet-stream")

            if sent:
                BulletinHelper.show_success("Базу ayu-data.db надіслано в Збережені!")
            else:
                BulletinHelper.show_success("Базу збережено в папку Завантаження!")
        except Exception as e:
            BulletinHelper.show_error(f"Помилка експорту: {e}")

    def on_chat_menu_click(self, context: dict):
        try:
            dialog_id = int(context.get("dialog_id", 0)) if context else 0
            self._do_export_and_send(target_dialog_id=dialog_id)
        except Exception as e:
            BulletinHelper.show_error(f"Помилка: {e}")

    def on_drawer_menu_click(self, context: dict):
        self._do_export_and_send(target_dialog_id=None)

    def export_all_click(self, view=None):
        self._do_export_and_send(target_dialog_id=None)

    def _process_media_for_html(self, media_path, doc_type, mime_type):
        """Конвертує локальний медіафайл (фото/стікер/аудіо) у вбудований base64 або формує бейдж"""
        if media_path and os.path.exists(media_path):
            file_size = os.path.getsize(media_path)
            base_name = os.path.basename(media_path)
            
            # Якщо це фото або стікер (до 3.5 МБ) - вбудовуємо прямо в HTML
            if (doc_type in (1, 2) or media_path.lower().endswith((".jpg", ".jpeg", ".png", ".webp"))) and file_size <= 3500000:
                try:
                    with open(media_path, "rb") as f:
                        b64 = base64.b64encode(f.read()).decode("ascii")
                    ext = "webp" if media_path.lower().endswith(".webp") else "jpeg"
                    return {
                        "type": "sticker" if doc_type == 2 else "image",
                        "src": f"data:image/{ext};base64,{b64}",
                        "name": base_name
                    }
                except Exception:
                    pass

            # Якщо це аудіо/голосове (до 4 МБ)
            if (doc_type == 3 and (media_path.lower().endswith((".ogg", ".mp3", ".m4a")) or "audio" in (mime_type or ""))) and file_size <= 4000000:
                try:
                    with open(media_path, "rb") as f:
                        b64 = base64.b64encode(f.read()).decode("ascii")
                    return {
                        "type": "audio",
                        "src": f"data:audio/ogg;base64,{b64}",
                        "name": base_name
                    }
                except Exception:
                    pass

            # Якщо це відео/гіфка (до 6 МБ)
            if (media_path.lower().endswith(".mp4") or "video" in (mime_type or "")) and file_size <= 6000000:
                try:
                    with open(media_path, "rb") as f:
                        b64 = base64.b64encode(f.read()).decode("ascii")
                    return {
                        "type": "video",
                        "src": f"data:video/mp4;base64,{b64}",
                        "name": base_name
                    }
                except Exception:
                    pass

            return {"type": "badge", "text": f"📎 {base_name}"}

        # Якщо самого файлу на диску немає, вказуємо тип вкладення
        if doc_type == 1:
            return {"type": "badge", "text": "🖼 Фото"}
        elif doc_type == 2:
            return {"type": "badge", "text": "🎭 Стікер"}
        elif doc_type == 3:
            return {"type": "badge", "text": "🎙 Голосове / Аудіо"}
        elif doc_type == 4:
            return {"type": "badge", "text": "🔗 Посилання"}
        elif doc_type == 5:
            return {"type": "badge", "text": "📖 Історія"}
        
        return None

    def _do_export_and_send(self, target_dialog_id=None):
        try:
            self._checkpoint_db()
            db_path = self._get_db_path()
            if not os.path.exists(db_path):
                BulletinHelper.show_error("База даних ayu-data не знайдена!")
                return

            conn = sqlite3.connect(db_path)
            cursor = conn.cursor()

            cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
            tables = [r[0] for r in cursor.fetchall()]
            table_name = next((c for c in ["DeletedMessage", "deletedmessage"] if c in tables), None)

            if not table_name:
                BulletinHelper.show_error("Таблицю DeletedMessage не знайдено!")
                conn.close()
                return

            # Зчитуємо реакції, якщо таблиця існує в базі
            reactions_map = {}
            r_table = next((c for c in ["DeletedMessageReaction", "deletedmessagereaction"] if c in tables), None)
            if r_table:
                try:
                    cursor.execute(f"SELECT deletedMessageId, emoticon, count, selfSelected FROM {r_table}")
                    for r_row in cursor.fetchall():
                        m_fake_id = r_row[0]
                        if m_fake_id not in reactions_map:
                            reactions_map[m_fake_id] = []
                        reactions_map[m_fake_id].append({
                            "emoticon": r_row[1],
                            "count": int(r_row[2] or 1),
                            "self": bool(r_row[3])
                        })
                except Exception as e:
                    self.log(f"Reactions query ignored: {e}")

            if target_dialog_id:
                cursor.execute(f"SELECT * FROM {table_name} WHERE dialogId = ? ORDER BY date ASC", (target_dialog_id,))
            else:
                cursor.execute(f"SELECT * FROM {table_name} ORDER BY date ASC")

            cols = [d[0] for d in cursor.description]
            rows = cursor.fetchall()
            conn.close()

            if not rows:
                BulletinHelper.show_info("Немає збережених повідомлень для експорту.")
                return

            my_id = 0
            try:
                UserConfig = jclass("org.telegram.messenger.UserConfig")
                curr_acc = int(UserConfig.selectedAccount)
                my_user = UserConfig.getInstance(curr_acc).getCurrentUser()
                if my_user:
                    my_id = int(my_user.id)
            except Exception:
                pass

            chats_dict = {}

            for r in rows:
                row_dict = dict(zip(cols, r))
                d_id = int(row_dict.get("dialogId", 0))
                from_id = int(row_dict.get("fromId", 0))
                date_ts = int(row_dict.get("date", 0))
                msg_text = row_dict.get("text") or ""
                media_path = row_dict.get("mediaPath") or ""
                quote_text = row_dict.get("replyQuoteText") or ""
                fwd_name = row_dict.get("fwdName") or ""
                doc_type = int(row_dict.get("documentType", 0))
                mime_type = row_dict.get("mimeType") or ""
                fake_id = row_dict.get("fakeId", 0)

                d_key = str(d_id)
                if d_key not in chats_dict:
                    chats_dict[d_key] = {
                        "id": d_id,
                        "title": self._get_dialog_title(d_id, fwd_name),
                        "lastTime": "",
                        "lastPreview": "",
                        "messages": []
                    }

                dt = datetime.datetime.fromtimestamp(date_ts) if date_ts else datetime.datetime.now()
                time_str = dt.strftime("%H:%M")
                date_str = dt.strftime("%d.%m.%Y")

                is_out = (from_id == my_id and my_id != 0)
                author_name = "Ви" if is_out else (fwd_name if fwd_name else f"ID {from_id}")

                media_data = self._process_media_for_html(media_path, doc_type, mime_type)
                media_name = os.path.basename(media_path) if media_path else ""

                chats_dict[d_key]["lastTime"] = time_str
                preview_txt = msg_text or (media_data["text"] if media_data and "text" in media_data else "Повідомлення")
                chats_dict[d_key]["lastPreview"] = (preview_txt[:35] + "...") if len(preview_txt) > 35 else preview_txt

                msg_reactions = reactions_map.get(fake_id, [])

                chats_dict[d_key]["messages"].append({
                    "id": int(row_dict.get("messageId", 0)),
                    "fromId": from_id,
                    "author": author_name,
                    "isOut": is_out,
                    "text": msg_text,
                    "time": time_str,
                    "date": date_str,
                    "quote": quote_text,
                    "mediaData": media_data,
                    "mediaName": media_name,
                    "reactions": msg_reactions
                })

            chats_json = json.dumps(chats_dict, ensure_ascii=False)
            full_html = HTML_TEMPLATE.replace("__CHATS_DATA_JSON__", chats_json)

            downloads_dir = self._get_downloads_dir()
            now_str = datetime.datetime.now().strftime("%d.%m.%Y %H:%M")
            html_filename = f"telegram_chats_export.html"
            out_html_path = os.path.join(downloads_dir, html_filename)

            with open(out_html_path, "w", encoding="utf-8") as f:
                f.write(full_html)

            out_db_path = os.path.join(downloads_dir, "ayu-data.db")
            try:
                shutil.copyfile(db_path, out_db_path)
            except Exception:
                pass

            total_chats = len(chats_dict)
            html_caption = f"💬 Telegram Web експорт усіх видалених чатів\n📁 Чатів: {total_chats} · Повідомлень: {len(rows)}\n📅 Дата: {now_str}\n\n✨ Відкрийте файл у браузері: зліва чати, справа переписка з фото, стікерами та реакціями!"
            self._send_file_to_saved_messages(out_html_path, caption=html_caption, mime_type="text/html")

            if os.path.exists(out_db_path):
                db_caption = f"💾 База ayu-data.db для відновлення\n📅 Дата: {now_str}\n\nДля переносу в новий Yumigram (меню «Імпорт бази даних»)."
                self._send_file_to_saved_messages(out_db_path, caption=db_caption, mime_type="application/octet-stream")

            BulletinHelper.show_success("Надіслано у Збережені (Saved Messages)!")
        except Exception as e:
            self.log(f"Export error: {e}")
            BulletinHelper.show_error(f"Помилка: {e}")
