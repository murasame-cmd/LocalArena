package com.localarena;

import android.content.Context;
import android.content.SharedPreferences;

/** Monetization-ready local state. Purchases/ads are intentionally not enabled in the MVP. */
public final class MonetizationState {
    private static final String PREFS = "localarena_profile";
    public String themeId = "classic_dark";
    public String selectedSkinId = "classic";
    public boolean adsRemoved = false;

    public void load(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        themeId=p.getString("theme_id","classic_dark");
        selectedSkinId=p.getString("skin_id","classic");
        adsRemoved=p.getBoolean("ads_removed",false);
    }
    public void save(Context c){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("theme_id",themeId)
            .putString("skin_id",selectedSkinId)
            .putBoolean("ads_removed",adsRemoved)
            .apply();
    }
    public void reset(Context c){
        themeId="classic_dark"; selectedSkinId="classic"; adsRemoved=false; save(c);
    }
}
