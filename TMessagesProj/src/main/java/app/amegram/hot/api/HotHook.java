package app.amegram.hot.api;

import java.lang.reflect.Member;

/**
 * Універсальний механізм динамічного хукінгу будь-якого методу на рівні ART (Pine / Xposed).
 * Дозволяє модулям перехоплювати та модифікувати будь-які методи Telegram без оновлення APK.
 */
public final class HotHook {

    private HotHook() {
    }

    public interface Callback {
        default void before(Call call) throws Throwable {
        }

        default void after(Call call) throws Throwable {
        }
    }

    public interface Call {
        Member getMethod();

        Object getThis();

        Object[] getArgs();

        Object getResult();

        void setResult(Object result);

        Throwable getThrowable();

        void setThrowable(Throwable throwable);
    }

    public interface Unhook {
        void unhook();
    }
}
