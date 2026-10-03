package app.amegram.core.events;

/**
 * Події ядра для плагінів і модулів. Тонка типізація навмисно:
 * контракт стабільний роками, payload — структури конкретних точок.
 * Жодних залежностей від org.telegram: імена полів — строки/примітиви.
 */
public final class CoreEvents {

    private CoreEvents() {
    }

    /** Вхідне повідомлення: dialogId, messageId, text. */
    public static final class MessageReceived {
        public final long dialogId;
        public final int messageId;
        public final String text;

        public MessageReceived(long dialogId, int messageId, String text) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.text = text != null ? text : "";
        }
    }

    /** Вихідний запит у мережу (preRequest): тип + скасування. */
    public static final class NetRequest {
        public final String kind;
        public final Object raw;
        public boolean cancelled;

        public NetRequest(String kind, Object raw) {
            this.kind = kind;
            this.raw = raw;
        }
    }

    /** Пункт меню, який додає плагін/модуль: id, заголовок, іконка. */
    public static final class MenuItem {
        public final String id;
        public final String title;

        public MenuItem(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    /** Вхідна нотифікація: dialogId, стислий текст. Призрак/дно її фільтрують. */
    public static final class Notification {
        public final long dialogId;
        public final String preview;
        public boolean suppressed;

        public Notification(long dialogId, String preview) {
            this.dialogId = dialogId;
            this.preview = preview != null ? preview : "";
        }
    }
}
