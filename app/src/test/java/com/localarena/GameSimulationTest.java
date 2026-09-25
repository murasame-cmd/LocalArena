package com.localarena;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class GameSimulationTest {
    @Test public void chessSelfPlay() {
        ChessGame g = new ChessGame();
        for (int ply = 0; ply < 400 && g.result == ' '; ply++) {
            List<ChessGame.Move> moves = g.legal(g.white);
            assertFalse("Chess reached a side with no legal move without result", moves.isEmpty());
            g.move(moves.get(0));
        }
        assertTrue("Chess simulation must terminate or hit move cap safely", g.result != ' ' || g.legal(g.white).size() > 0);
    }

    @Test public void seaBattleSelfPlay() {
        SeaBattleGame g = new SeaBattleGame();
        g.randomPlace(0, 1001L);
        g.randomPlace(1, 2002L);
        g.ready[0] = g.ready[1] = true;
        assertTrue(g.validFleet(g.cells[0]));
        assertTrue(g.validFleet(g.cells[1]));
        for (int pass = 0; pass < 200 && g.winner < 0; pass++) {
            int shooter = g.turn ? 0 : 1;
            boolean moved = false;
            for (int y = 0; y < 10 && !moved; y++) for (int x = 0; x < 10 && !moved; x++)
                moved = g.shoot(shooter, x, y);
            assertTrue("Sea Battle simulation got stuck", moved);
        }
        assertTrue("Sea Battle must finish within 200 turns", g.winner >= 0);
    }

    @Test public void durakSelfPlay() {
        DurakGame g = new DurakGame();
        for (int step = 0; step < 2000 && !g.finished; step++) {
            int a = g.attacker, d = g.defender;
            if (g.attack.isEmpty()) {
                assertFalse(g.hand[a].isEmpty());
                int card = g.hand[a].get(0);
                assertTrue(g.addAttack(a, card));
                continue;
            }

            boolean uncovered = false;
            for (int i = 0; i < g.defense.size(); i++) {
                if (g.defense.get(i) == DurakGame.NONE) {
                    uncovered = true;
                    int chosen = -1;
                    for (int card : g.hand[d]) {
                        if (g.canBeat(card, g.attack.get(i))) { chosen = card; break; }
                    }
                    if (chosen >= 0) assertTrue(g.defend(d, chosen, i));
                    else assertTrue(g.take(d));
                    break;
                }
            }

            if (!uncovered) {
                assertTrue(g.passAttack(a));
            }
        }
        assertTrue("Durak simulation exceeded safety limit", g.finished);
        assertTrue(g.loser == DurakGame.NONE || g.loser == 0 || g.loser == 1);
    }

    @Test public void seaBattlePlacementStress() {
        SeaBattleGame g = new SeaBattleGame();
        for (int p = 0; p < 2; p++) for (int i = 0; i < 100; i++) {
            g.randomPlace(p, 100000L + p * 1000L + i);
            assertTrue(g.validFleet(g.cells[p]));
        }
    }
}
