package app.miogram.bridge.wall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Моделі даних стіни. Прості POJO, серіалізуються Gson у JSON
 * (див. {@link WallStorage}) — готові до майбутнього синку з VPS.
 */
public final class WallModels {

    private WallModels() {
    }

    // ---------- типи елементів ----------

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_PHOTO = "photo";
    public static final String TYPE_GIF = "gif";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_STICKER = "sticker";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_FORWARD = "forward";
    public static final String TYPE_POLL = "poll";
    public static final String TYPE_DRAWING = "drawing";
    public static final String TYPE_BUTTON = "button";
    public static final String TYPE_HTML = "html";

    // ---------- стилі карток ----------

    public static final String STYLE_GLASS = "glass";
    public static final String STYLE_NEON = "neon";
    public static final String STYLE_PAPER = "paper";
    public static final String STYLE_NIGHT = "night";

    // ---------- режими редагування ----------

    public static final String EDIT_OWNER = "owner";
    public static final String EDIT_EVERYONE = "everyone";
    public static final String EDIT_CONTACTS = "contacts";
    public static final String EDIT_FOLDER = "folder";
    public static final String EDIT_SELECTED = "selected";

    public static Wall newWall(long ownerId, String ownerName, String ownerUsername) {
        Wall wall = new Wall();
        wall.ownerId = ownerId;
        wall.ownerName = ownerName == null ? "" : ownerName;
        wall.ownerUsername = ownerUsername == null ? "" : ownerUsername;
        long now = System.currentTimeMillis();
        wall.createdAt = now;
        wall.updatedAt = now;
        return wall;
    }

    public static WallElement newElement(String type, long authorId, String authorName, String authorUsername) {
        WallElement e = new WallElement();
        e.id = java.util.UUID.randomUUID().toString();
        e.type = type;
        e.authorId = authorId;
        e.authorName = authorName == null ? "" : authorName;
        e.authorUsername = authorUsername == null ? "" : authorUsername;
        long now = System.currentTimeMillis();
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public static WallComment newComment(long authorId, String authorName, String text) {
        WallComment c = new WallComment();
        c.id = java.util.UUID.randomUUID().toString();
        c.authorId = authorId;
        c.authorName = authorName == null ? "" : authorName;
        c.text = text == null ? "" : text;
        c.createdAt = System.currentTimeMillis();
        return c;
    }

    /** Стіна одного користувача. */
    public static class Wall {
        public int schema = 1;
        public long ownerId;
        public String ownerName = "";
        public String ownerUsername = "";
        public String accent = "#FF4D8D";
        public long createdAt;
        public long updatedAt;

        /** Хто може додавати елементи: owner / everyone / contacts / folder / selected. */
        public String editMode = EDIT_EVERYONE;
        /** Індекс папки чатів для режиму folder (0 = перша). */
        public int folderIndex = 0;
        /** Обрані люди для режиму selected. */
        public List<Long> editorIds = new ArrayList<>();

        public boolean allowLikes = true;
        public boolean allowComments = true;
        public boolean allowDrawings = true;
        public boolean showOnBanner = true;

        /** Майбутній синк: local | pending | synced. */
        public String syncState = "local";
        public String remoteId = "";

        public List<WallElement> elements = new ArrayList<>();
    }

    /** Елемент стіни. */
    public static class WallElement {
        public String id = "";
        public String type = TYPE_TEXT;
        public long authorId;
        public String authorName = "";
        public String authorUsername = "";
        public long createdAt;
        public long updatedAt;

        /** Основний текст: думка, підпис, запитання опитування, лейбл кнопки. */
        public String text = "";
        /** Додатковий текст: автор пересланого, значення кнопки тощо. */
        public String subtitle = "";
        /** Посилання/юзернейм для пересланого. */
        public String sourceUrl = "";

        public String mediaPath = "";
        public String thumbPath = "";
        public String mime = "";
        public String fileName = "";
        public long fileSize;
        public int mediaWidth;
        public int mediaHeight;

        /** Емодзі-стікер (1–3 символи). */
        public String emoji = "";
        /** HTML-віджет. */
        public String html = "";
        public String css = "";
        public String js = "";

        public WallPoll poll;
        public WallAction action;

        /** glass / neon / paper / night. */
        public String style = STYLE_GLASS;
        /** half або full. */
        public String size = "half";
        public boolean pinned;

        public Map<String, Boolean> likes = new LinkedHashMap<>();
        public List<WallComment> comments = new ArrayList<>();
        public long views;

        public int likeCount() {
            int n = 0;
            if (likes != null) {
                for (Boolean v : likes.values()) {
                    if (v != null && v) n++;
                }
            }
            return n;
        }

        public boolean likedBy(long userId) {
            return likes != null && Boolean.TRUE.equals(likes.get(String.valueOf(userId)));
        }

        public int commentCount() {
            return comments == null ? 0 : comments.size();
        }

        public int voteCount() {
            return poll == null || poll.votes == null ? 0 : poll.votes.size();
        }

        public Integer myVote(long userId) {
            if (poll == null || poll.votes == null) return null;
            return poll.votes.get(String.valueOf(userId));
        }

        public int averageVote() {
            if (poll == null || poll.votes == null || poll.votes.isEmpty()) return -1;
            int sum = 0;
            for (Integer v : poll.votes.values()) {
                if (v != null) sum += v;
            }
            return Math.round(sum / (float) poll.votes.size());
        }
    }

    /** Опитування-слайдер: тягнеш від min до max, бачиш граф відповідей. */
    public static class WallPoll {
        public int min = 0;
        public int max = 100;
        public String minLabel = "0";
        public String maxLabel = "100";
        public String color1 = "#FF4D8D";
        public String color2 = "#8A5CFF";
        /** userId -> значення (min..max). */
        public Map<String, Integer> votes = new LinkedHashMap<>();
    }

    /** Дія кнопки-елемента. */
    public static class WallAction {
        /** url / copy / chat. */
        public String kind = "url";
        public String value = "";
    }

    /** Відповідь/коментар до елемента. */
    public static class WallComment {
        public String id = "";
        public long authorId;
        public String authorName = "";
        public String text = "";
        public long createdAt;
        public Map<String, Boolean> likes = new LinkedHashMap<>();

        public int likeCount() {
            int n = 0;
            if (likes != null) {
                for (Boolean v : likes.values()) {
                    if (v != null && v) n++;
                }
            }
            return n;
        }
    }
}
