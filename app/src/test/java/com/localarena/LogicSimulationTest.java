package com.localarena;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LogicSimulationTest {
    static int sq(int f,int r){return r*8+f;}

    @Test public void chessFullSmoke(){
        ChessGame g=new ChessGame();
        assertTrue(g.move(new ChessGame.Move(sq(4,6),sq(4,4),(char)0))); // e4
        assertTrue(g.move(new ChessGame.Move(sq(0,1),sq(0,2),(char)0))); // ...a6
        assertTrue(g.move(new ChessGame.Move(sq(4,4),sq(4,3),(char)0))); // e5
        assertTrue(g.move(new ChessGame.Move(sq(3,1),sq(3,3),(char)0))); // ...d5
        assertTrue(g.move(new ChessGame.Move(sq(4,3),sq(3,2),(char)0))); // exd6 e.p.
        assertEquals('P',g.b[sq(3,2)]);
        assertEquals('.',g.b[sq(3,3)]);

        g.reset();
        assertTrue(g.move(new ChessGame.Move(sq(4,6),sq(4,4),(char)0)));
        assertTrue(g.move(new ChessGame.Move(sq(4,1),sq(4,3),(char)0)));
        assertTrue(g.move(new ChessGame.Move(sq(6,7),sq(5,5),(char)0)));
        assertTrue(g.move(new ChessGame.Move(sq(1,0),sq(2,2),(char)0)));
        assertTrue(g.move(new ChessGame.Move(sq(5,7),sq(4,6),(char)0)));
        assertTrue(g.move(new ChessGame.Move(sq(6,0),sq(5,2),(char)0)));
        assertTrue(g.move(new ChessGame.Move(sq(4,7),sq(6,7),(char)0))); // O-O
        assertEquals('K',g.b[sq(6,7)]);
        assertEquals('R',g.b[sq(5,7)]);

        g=new ChessGame();
        Arrays.fill(g.b, ChessGame.E);
        g.b[sq(4,7)]='K'; g.b[sq(4,0)]='k'; g.b[sq(4,6)]='R';
        g.white=true;
        assertFalse(g.move(new ChessGame.Move(sq(4,6),sq(4,0),(char)0))); // king cannot be captured
    }

    @Test public void seaBattleFullSmoke(){
        SeaBattleGame s=new SeaBattleGame();
        s.randomPlace(0,12345L);
        s.randomPlace(1,67890L);
        assertTrue(s.validFleet(s.cells[0]));
        assertTrue(s.validFleet(s.cells[1]));
        s.ready[0]=s.ready[1]=true;
        String hidden=s.encodeFor(0);
        String[] parts=hidden.split("\\|",-1);
        assertEquals(3,parts.length);
        assertFalse(parts[2].contains("1")); // opponent ships are hidden
        int guard=0;
        while(s.winner<0 && guard++<500){
            int p=s.turn?0:1;
            int[][] target=s.cells[1-p];
            boolean fired=false;
            for(int y=0;y<10&&!fired;y++)for(int x=0;x<10&&!fired;x++){
                if(target[y][x]==0 || target[y][x]==1){
                    assertTrue(s.shoot(p,x,y));
                    fired=true;
                }
            }
        }
        assertTrue("Sea Battle did not finish",s.winner>=0);
    }

    @Test public void durakFullSmoke(){
        DurakGame d=new DurakGame();
        assertEquals(6,d.hand[0].size());
        assertEquals(6,d.hand[1].size());
        assertEquals(24,d.deck.size());
        assertTrue(d.trump>=0&&d.trump<4);

        d.deck.clear();
        d.hand[0].clear(); d.hand[1].clear();
        d.hand[0].add(0); d.hand[0].add(1); d.hand[0].add(4);
        d.hand[1].add(8); d.hand[1].add(9);
        d.trump=2; d.attacker=0; d.defender=1; d.finished=false; d.loser=DurakGame.NONE;
        assertTrue(d.addAttack(0,0));
        assertTrue(d.addAttack(0,1)); // same rank as first attack
        assertEquals(2,d.attack.size());
        assertTrue(d.defend(1,8,0));
        assertTrue(d.defend(1,9,1));
        assertFalse(d.take(1)); // all attacks are already covered
        assertTrue(d.passAttack(0));
        assertEquals(1,d.attacker);
        assertEquals(0,d.defender);

        d.reset();
        d.deck.clear();
        d.hand[0].clear(); d.hand[1].clear();
        d.hand[0].add(0); d.hand[1].add(2);
        d.attacker=0; d.defender=1; d.finished=false; d.loser=DurakGame.NONE;
        assertTrue(d.addAttack(0,0));
        assertTrue(d.take(1));
        assertTrue(d.hand[1].contains(0));
        assertEquals(0,d.attacker); // attacker stays attacker after take
    }
}
