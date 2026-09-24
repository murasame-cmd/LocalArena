package com.localarena;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local profile state for the MVP.
 * The free edition is complete and contains no ads.
 * Purchases/support are intentionally not connected yet.
 */
public final class MonetizationState {
    private static final String PREFS = "localarena_profile";
    public String themeId = "classic_dark";
    public String selectedSkinId = "classic";

    public void load(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        themeId=p.getString("theme_id","classic_dark");
        selectedSkinId=p.getString("skin_id","classic");
    }
    public void save(Context c){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("theme_id",themeId)
            .putString("skin_id",selectedSkinId)
            .apply();
    }
    public void reset(Context c){
        themeId="classic_dark";
        selectedSkinId="classic";
        save(c);
    }
}
