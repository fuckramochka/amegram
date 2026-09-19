package app.miogram.bridge.badge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.miogram.bridge.MiogramLocale;

/**
 * 10 Canonical Miogram Badges with Needy Streamer Overload / Ame & KAngel aesthetic:
 * 01 - ORIGINAL (Classic winged heart with antenna visor)
 * 02 - ANGEL (Celestial angel with glowing halo & pastel wings)
 * 03 - DARK (Midnight obsidian with velvet lavender glow & cyber ribbons)
 * 04 - GLITCH (Cyber glitch with chromatic RGB displacement & scanlines)
 * 05 - PINK (Neon pink streamer heart with chevron ribs)
 * 06 - CYAN (Cyberspace matrix with laser cyan wings)
 * 07 - DEVIL (Mischievous devil with horns & bat wings)
 * 08 - RAINBOW (Prismatic rainbow with 5-tier spectrum feathers)
 * 09 - OUTLINE (1-bit retro wireframe contour)
 * 10 - PREMIUM (Supreme royal crown & golden halo)
 */
public enum MiogramBadgeType {

    ORIGINAL("original", "01 — ORIGINAL", "Класичний стиль", "Классический стиль", "Classic style"),
    ANGEL("angel", "02 — ANGEL", "Небесний ангел", "Небесный ангел", "Celestial angel"),
    DARK("dark", "03 — DARK", "Нічний обсидіан", "Ночной обсидиан", "Midnight obsidian"),
    GLITCH("glitch", "04 — GLITCH", "Кібер-глітч", "Кибер-глитч", "Cyber glitch"),
    PINK("pink", "05 — PINK", "Неоново-рожевий", "Неоново-розовый", "Neon pink"),
    CYAN("cyan", "06 — CYAN", "Кібер-блакитний", "Кибер-лазурный", "Cyber cyan"),
    DEVIL("devil", "07 — DEVIL", "Зухвалий чортик", "Дерзкий чертёнок", "Devil style"),
    RAINBOW("rainbow", "08 — RAINBOW", "Призматичний спектр", "Призматический спектр", "Prismatic rainbow"),
    OUTLINE("outline", "09 — OUTLINE", "Кібер-вайрфрейм", "Кибер-вайрфрейм", "Cyber wireframe"),
    PREMIUM("premium", "10 — PREMIUM", "Королівська корона", "Королевская корона", "Royal crown");

    private final String id;
    private final String code;
    private final String titleUk;
    private final String titleRu;
    private final String titleEn;

    MiogramBadgeType(String id, String code, String titleUk, String titleRu, String titleEn) {
        this.id = id;
        this.code = code;
        this.titleUk = titleUk;
        this.titleRu = titleRu;
        this.titleEn = titleEn;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return MiogramLocale.get(titleUk, titleRu, titleEn);
    }

    @NonNull
    public static MiogramBadgeType fromId(@Nullable String id) {
        if (id != null) {
            String lower = id.trim().toLowerCase();
            // Aliases
            if ("kangel".equals(lower)) return ANGEL;
            if ("ame".equals(lower)) return DARK;
            if ("overdose".equals(lower)) return GLITCH;

            for (MiogramBadgeType type : values()) {
                if (type.id.equals(lower) || type.name().toLowerCase().equals(lower)) {
                    return type;
                }
            }
        }
        return ORIGINAL;
    }
}
