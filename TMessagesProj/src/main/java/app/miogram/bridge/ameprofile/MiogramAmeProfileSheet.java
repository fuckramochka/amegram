package app.miogram.bridge.ameprofile;

import android.content.Context;
import app.amegram.bridge.ameprofile.AmeProfileSheet;

/**
 * Backward compatibility bridge delegating to {@link AmeProfileSheet}.
 */
public class MiogramAmeProfileSheet extends AmeProfileSheet {

    public MiogramAmeProfileSheet(Context context) {
        super(context);
    }
}
