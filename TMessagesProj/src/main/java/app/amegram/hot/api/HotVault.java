package app.amegram.hot.api;

/** Контракт модуля сховища: заливка файлів у vault. Реєструється під "vault". */
public interface HotVault {

    interface Callback {
        void onDone(boolean ok, String message);
    }

    boolean isLinked(int account);

    void upload(int account, java.io.File file, String fileName, String mimeType, Callback callback);
}
