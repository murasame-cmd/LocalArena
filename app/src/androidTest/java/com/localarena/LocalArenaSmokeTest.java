package com.localarena;

import android.app.Instrumentation;
import android.Manifest;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalArenaSmokeTest {
    @Test public void allGameScreensOpenAndRender() throws Exception {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        try { inst.getUiAutomation().grantRuntimePermission(inst.getTargetContext().getPackageName(), Manifest.permission.NEARBY_WIFI_DEVICES); } catch (Exception ignored) {}
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

    @Test public void interactionAndMalformedProtocolStress() throws Exception {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(inst.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MainActivity a = (MainActivity) inst.startActivitySync(intent);
        assertNotNull(a);

        String[] games = {"CHESS", "SEA", "DURAK"};
        for (int round=0; round<2; round++) {
            for (String game : games) {
                inst.runOnMainSync(() -> {
                    a.host = true;
                    a.me = 0;
                    a.connected = true;
                    a.startGame(game);
                    a.view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
                    a.view.layout(0,0,1080,1920);
                    if (round==0) {
                        Bitmap b = Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);
                        a.view.draw(new Canvas(b));
                        b.recycle();
                    }
                });
                inst.waitForIdleSync();
                assertTrue(!a.isFinishing() && !a.isDestroyed());

                if (game.equals("CHESS")) {
                    final float[] xy = {a.view.getWidth()/2f, 132*a.view.d + 0.5f*(Math.min(a.view.getWidth()-32*a.view.d, a.view.getHeight()-132*a.view.d-70*a.view.d)/8f)};
                    inst.runOnMainSync(() -> {
                        float side=Math.min(a.view.getWidth()-32*a.view.d,a.view.getHeight()-132*a.view.d-70*a.view.d);
                        float cell=side/8f;
                        float left=(a.view.getWidth()-side)/2f;
                        float y=132*a.view.d+4.5f*cell;
                        float x=left+4.5f*cell;
                        long t=System.currentTimeMillis();
                        MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);
                        a.view.onTouchEvent(d); d.recycle();
                        MotionEvent u=MotionEvent.obtain(t,t+5,MotionEvent.ACTION_UP,x,y,0);
                        a.view.onTouchEvent(u); u.recycle();
                    });
                } else if (game.equals("SEA")) {
                    inst.runOnMainSync(() -> {
                        a.sea.randomPlace(0, round+1L);
                        a.sea.randomPlace(1, round+2L);
                        a.sea.ready[0]=a.sea.ready[1]=true;
                        float size=Math.min(a.view.getWidth()-48*a.view.d,a.view.getHeight()/2.8f);
                        float left=(a.view.getWidth()-size)/2f;
                        float y=135*a.view.d+size+55*a.view.d+0.5f*size;
                        float x=left+0.5f*size;
                        long t=System.currentTimeMillis();
                        MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);
                        a.view.onTouchEvent(d); d.recycle();
                        MotionEvent u=MotionEvent.obtain(t,t+5,MotionEvent.ACTION_UP,x,y,0);
                        a.view.onTouchEvent(u); u.recycle();
                    });
                } else {
                    inst.runOnMainSync(() -> {
                        if (!a.durak.hand[0].isEmpty()) {
                            float w=Math.min(76*a.view.d,(a.view.getWidth()-48*a.view.d)/Math.max(1,a.durak.hand[0].size()));
                            float x=24*a.view.d+w/2f, y=190*a.view.d;
                            long t=System.currentTimeMillis();
                            MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);
                            a.view.onTouchEvent(d); d.recycle();
                            MotionEvent u=MotionEvent.obtain(t,t+5,MotionEvent.ACTION_UP,x,y,0);
                            a.view.onTouchEvent(u); u.recycle();
                        }
                    });
                }
                inst.waitForIdleSync();
                assertTrue("Interaction must not destroy activity: "+game,!a.isFinishing() && !a.isDestroyed());
                a.line("STATE|CHESS|bad");
                a.line("STATE|SEA|bad");
                a.line("STATE|DURAK|bad");
                a.line("ACTION|BAD|x");
                a.line("GAME|NOT_A_GAME");
                assertTrue(!a.isFinishing() && !a.isDestroyed());
            }
        }
        inst.runOnMainSync(a::finish);
    }
}
