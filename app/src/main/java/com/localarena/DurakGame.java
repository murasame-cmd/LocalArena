package com.localarena;

import java.util.*;

final class DurakGame {
    static final int NONE=-1; List<Integer>[] hand=new List[]{new ArrayList<>(),new ArrayList<>()}; List<Integer> deck=new ArrayList<>(); List<Integer> attack=new ArrayList<>(), defense=new ArrayList<>(); int trump=-1,attacker=0,defender=1; boolean started=false,finished=false; int loser=-1;
    DurakGame(){reset();}
    static int suit(int c){return c%4;} static int rank(int c){return c/4;} static String suitName(int s){return s>=0&&s<4?new String[]{"♠","♥","♦","♣"}[s]:"—";} static String card(int c){if(c<0)return "";String[]r={"6","7","8","9","10","J","Q","K","A"};return r[rank(c)]+new String[]{"♠","♥","♦","♣"}[suit(c)];}
    void reset(){hand[0].clear();hand[1].clear();deck.clear();attack.clear();defense.clear();for(int i=0;i<36;i++)deck.add(i);Collections.shuffle(deck,new Random());trump=suit(deck.get(0));attacker=0;defender=1;started=true;finished=false;loser=-1;for(int n=0;n<6;n++){hand[0].add(deck.remove(deck.size()-1));hand[1].add(deck.remove(deck.size()-1));}}
    boolean canBeat(int d,int a){if(suit(d)==suit(a))return rank(d)>rank(a);return suit(d)==trump&&suit(a)!=trump;}
    boolean addAttack(int p,int c){if(finished||p!=attacker||!hand[p].contains(c)||c<0)return false;if(attack.size()>=Math.min(6,hand[defender].size()))return false;if(!attack.isEmpty()){boolean ok=false;for(int a:attack)if(rank(a)==rank(c))ok=true;for(int d:defense)if(d>=0&&rank(d)==rank(c))ok=true;if(!ok)return false;}hand[p].remove((Integer)c);attack.add(c);defense.add(NONE);return true;}
    boolean defend(int p,int c,int idx){if(finished||p!=defender||idx<0||idx>=attack.size()||defense.get(idx)!=NONE||!hand[p].contains(c))return false;if(!canBeat(c,attack.get(idx)))return false;hand[p].remove((Integer)c);defense.set(idx,c);return true;}
    boolean passAttack(int p){if(finished||p!=attacker||attack.isEmpty())return false;for(int d:defense)if(d==NONE)return false;endRound();return true;}
    boolean take(int p){if(finished||p!=defender||attack.isEmpty())return false;boolean uncovered=false;for(int d:defense)if(d==NONE){uncovered=true;break;}if(!uncovered)return false;for(int c:attack)hand[p].add(c);attack.clear();defense.clear();refill(attacker);refill(defender);checkFinish();return true;}
    void endRound(){attack.clear();defense.clear();refill(attacker);refill(defender);int old=attacker;attacker=defender;defender=old;checkFinish();}
    void refill(int p){while(hand[p].size()<6&&!deck.isEmpty())hand[p].add(deck.remove(deck.size()-1));}
    void checkFinish(){if(!deck.isEmpty())return;boolean e0=hand[0].isEmpty(),e1=hand[1].isEmpty();if(e0&&e1){finished=true;loser=NONE;}else if(e0||e1){finished=true;loser=e0?1:0;}}
    String encode(){return encodeFor(0);}
    String encodeFor(int viewer){StringBuilder s=new StringBuilder();s.append(trump).append(',').append(attacker).append(',').append(defender).append(',').append(finished?'1':'0').append(',').append(loser).append('|');for(int p=0;p<2;p++){if(p==viewer){for(int c:hand[p])s.append(c).append('.');}else{s.append("#").append(hand[p].size());}s.append('|');}for(int c:attack)s.append(c).append('.');s.append('|');for(int c:defense)s.append(c).append('.');s.append('|').append("#").append(deck.size());return s.toString();}
    void decode(String s){String[]a=s.split("\\|",-1);String[]h=a[0].split(",");trump=Integer.parseInt(h[0]);attacker=Integer.parseInt(h[1]);defender=Integer.parseInt(h[2]);finished=h[3].equals("1");loser=Integer.parseInt(h[4]);for(int p=0;p<2;p++){hand[p].clear();if(!a[p+1].isEmpty()&&!a[p+1].startsWith("#"))for(String z:a[p+1].split("\\."))if(!z.isEmpty())hand[p].add(Integer.parseInt(z));}attack.clear();defense.clear();deck.clear();if(!a[3].isEmpty())for(String z:a[3].split("\\."))if(!z.isEmpty())attack.add(Integer.parseInt(z));if(!a[4].isEmpty())for(String z:a[4].split("\\."))if(!z.isEmpty())defense.add(Integer.parseInt(z));if(a.length>5&&a[5].startsWith("#")){int n=Integer.parseInt(a[5].substring(1));for(int i=0;i<n;i++)deck.add(-1);}}
}
