package com.localarena;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalArenaSmokeTest {
    @Test public void allGameScreensOpenAndRender() throws Exception {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(inst.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MainActivity a = (MainActivity) inst.startActivitySync(intent);
        assertNotNull(a);

        String[] games = {"CHESS", "SEA", "DURAK"};
        for (String game : games) {
            inst.runOnMainSync(() -> a.startGame(game));
            inst.waitForIdleSync();
            assertNotNull("View missing for " + game, a.view);
            assertEquals("Wrong screen for " + game, game, a.screen.name());
            assertTrue("Activity not running for " + game, !a.isFinishing() && !a.isDestroyed());
            final boolean[] rendered = {false};
            inst.runOnMainSync(() -> {
                a.view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
                a.view.layout(0, 0, 1080, 1920);
                rendered[0] = a.view.getWidth() == 1080 && a.view.getHeight() == 1920;
            });
            assertTrue("View did not layout for " + game, rendered[0]);
        }
        inst.runOnMainSync(a::finish);
    }
}
