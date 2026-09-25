package com.localarena;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class HardStressTest {
    private static final Random R = new Random(0x5EEDBEEF);

    private void chessInvariant(ChessGame g) {
        int wk=0,bk=0;
        for(char p:g.b){ if(p=='K') wk++; else if(p=='k') bk++; }
        assertEquals("Chess must contain exactly one white king",1,wk);
        assertEquals("Chess must contain exactly one black king",1,bk);
        for(ChessGame.Move m:g.legal(g.white)) {
            assertTrue("Legal move may not capture the opposing king", g.b[m.to] != (g.white?'k':'K'));
            assertTrue("Legal move origin must contain own piece", ChessGame.side(g.b[m.from],g.white));
        }
    }

    @Test public void chessRandomStressAndRoundTrip() {
        for(int game=0; game<250; game++){
            ChessGame g=new ChessGame();
            assertEquals(20,g.legal(true).size());
            for(int ply=0; ply<1200 && g.result==' '; ply++){
                List<ChessGame.Move> moves=g.legal(g.white);
                assertFalse("No legal move without game result", moves.isEmpty());
                chessInvariant(g);
                ChessGame.Move m=moves.get(R.nextInt(moves.size()));
                assertTrue("Generated legal move must apply",g.move(m));
                String encoded=g.encode();
                ChessGame copy=new ChessGame();
                copy.decode(encoded);
                assertEquals("Chess state must survive encode/decode",encoded,copy.encode());
            }
            assertTrue("Chess stress run exceeded safety bound",g.result!=' ');
        }
    }

    @Test public void chessSpecialMoveSmoke() {
        ChessGame g=new ChessGame();
        // e2-e4, e7-e5, g1-f3, b8-c6, f1-b5, a7-a6, b5-a4, g8-f6
        int[][] seq={{ChessGame.sq(4,6),ChessGame.sq(4,4)},{ChessGame.sq(4,1),ChessGame.sq(4,3)},
                     {ChessGame.sq(6,7),ChessGame.sq(5,5)},{ChessGame.sq(1,0),ChessGame.sq(2,2)},
                     {ChessGame.sq(5,7),ChessGame.sq(1,3)},{ChessGame.sq(0,1),ChessGame.sq(0,2)},
                     {ChessGame.sq(1,3),ChessGame.sq(0,2)},{ChessGame.sq(6,0),ChessGame.sq(5,2)}};
        for(int[]m:seq) assertTrue(g.move(new ChessGame.Move(m[0],m[1],(char)0)));
        // Position must still have both kings and legal moves.
        assertEquals(1,count(g.b,'K')); assertEquals(1,count(g.b,'k'));
        assertFalse(g.legal(g.white).isEmpty());
    }

    private static int count(char[] b,char c){int n=0;for(char x:b)if(x==c)n++;return n;}

    @Test public void seaBattleFullGameStress() {
        for(int game=0;game<500;game++){
            SeaBattleGame g=new SeaBattleGame();
            g.randomPlace(0,0x100000L+game);
            g.randomPlace(1,0x200000L+game);
            g.ready[0]=g.ready[1]=true;
            boolean[][][] tried={new boolean[10][10],new boolean[10][10]};
            int shots=0;
            while(g.winner<0 && shots<200){
                int p=g.turn?0:1;
                // Randomly try until an unseen cell is selected.
                int x,y;
                do { x=R.nextInt(10); y=R.nextInt(10); } while(tried[p][y][x]);
                tried[p][y][x]=true;
                assertTrue(g.shoot(p,x,y));
                shots++;
                String enc=g.encodeFor(p);
                SeaBattleGame copy=new SeaBattleGame();
                copy.decode(enc);
                assertEquals("Sea state header/grid must round-trip",enc,copy.encodeFor(p));
                for(int yy=0;yy<10;yy++) for(int xx=0;xx<10;xx++) {
                    int v=g.cells[0][yy][xx];
                    assertTrue("Sea grid cell out of range",v>=0&&v<=3);
                    v=g.cells[1][yy][xx];
                    assertTrue("Sea grid cell out of range",v>=0&&v<=3);
                }
            }
            assertTrue("Sea Battle must finish",g.winner>=0);
        }
    }

    @Test public void durakHiddenHandSyncPreservesRuleCapacity() {
        DurakGame host=new DurakGame();
        String publicForGuest=host.encodeFor(1);
        DurakGame guest=new DurakGame();
        guest.decode(publicForGuest);
        assertEquals(host.hand[0].size(),guest.hand[0].size());
        assertEquals(host.hand[1].size(),guest.hand[1].size());
        assertTrue("Guest must be able to attack when it is attacker", guest.attacker==1 ? guest.addAttack(1,guest.hand[1].get(0)) : true);
    }

    @Test public void durakFullGameStressAndRoundTrip() {
        for(int game=0;game<500;game++){
            DurakGame g=new DurakGame();
            for(int step=0;step<5000 && !g.finished;step++){
                assertEquals("Attack/defense sizes must match",g.attack.size(),g.defense.size());
                assertTrue(g.trump>=0 && g.trump<4);
                assertTrue(g.attacker==0||g.attacker==1);
                assertTrue(g.defender==0||g.defender==1);
                assertNotEquals(g.attacker,g.defender);
                int total=0; boolean[] seen=new boolean[36];
                for(int p=0;p<2;p++) for(int card:g.hand[p]) { assertTrue(card>=0&&card<36); assertFalse("Duplicate card in hands",seen[card]); seen[card]=true; total++; }
                for(int card:g.attack) { assertTrue(card>=0&&card<36); assertFalse("Duplicate attack card",seen[card]); seen[card]=true; total++; }
                for(int card:g.defense) if(card>=0) { assertTrue(card>=0&&card<36); assertFalse("Duplicate defense card",seen[card]); seen[card]=true; total++; }
                for(int card:g.deck) { assertTrue(card>=0&&card<36); assertFalse("Duplicate deck card",seen[card]); seen[card]=true; total++; }
                assertEquals("Every Durak card must exist exactly once",36,total);

                if(g.attack.isEmpty()){
                    List<Integer> choices=new ArrayList<>(g.hand[g.attacker]);
                    assertFalse(choices.isEmpty());
                    assertTrue(g.addAttack(g.attacker,choices.get(R.nextInt(choices.size()))));
                } else {
                    int idx=-1;
                    for(int i=0;i<g.defense.size();i++) if(g.defense.get(i)==DurakGame.NONE){idx=i;break;}
                    if(idx>=0){
                        int d=g.defender, target=g.attack.get(idx), chosen=-1;
                        for(int c:g.hand[d]) if(g.canBeat(c,target)){chosen=c;break;}
                        if(chosen>=0) assertTrue(g.defend(d,chosen,idx));
                        else assertTrue(g.take(d));
                    } else {
                        assertTrue(g.passAttack(g.attacker));
                    }
                }
                String enc=g.encodeFor(0);
                DurakGame copy=new DurakGame();
                copy.decode(enc);
                assertEquals("Durak public/private state must round-trip for viewer 0",enc,copy.encodeFor(0));
            }
            assertTrue("Durak must finish within safety bound",g.finished);
            assertTrue(g.loser==DurakGame.NONE||g.loser==0||g.loser==1);
        }
    }

    @Test public void seaBattlePlacementMassStress() {
        SeaBattleGame g=new SeaBattleGame();
        for(int p=0;p<2;p++) for(int i=0;i<2000;i++){
            g.randomPlace(p,0xABC000L+p*100000L+i);
            assertTrue("Invalid fleet at player "+p+" iteration "+i,g.validFleet(g.cells[p]));
        }
    }
}
