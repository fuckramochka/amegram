package app.miogram.bridge.ameprofile;

import android.content.Context;

/**
 * Backward compatibility bridge delegating to {@link app.amegram.bridge.ameprofile.AmeCustomCardCell}.
 */
public class AmeCustomCardCell extends app.amegram.bridge.ameprofile.AmeCustomCardCell {

    public AmeCustomCardCell(Context context) {
        super(context);
    }
}
