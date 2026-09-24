package com.localarena;

import java.util.*;

final class ChessGame {
    static final char E='.'; char[] b=new char[64]; boolean white=true; boolean wK=true,wQ=true,bK=true,bQ=true; int ep=-1; char result=' ';
    ChessGame(){reset();}
    void reset(){ b=("rnbqkbnr"+"pppppppp"+"........"+"........"+"........"+"........"+"PPPPPPPP"+"RNBQKBNR").toCharArray(); white=true;wK=wQ=bK=bQ=true;ep=-1;result=' '; }
    static int sq(int f,int r){return r*8+f;} static int f(int s){return s&7;} static int r(int s){return s>>3;}
    static boolean isW(char p){return p>='A'&&p<='Z';} static boolean isB(char p){return p>='a'&&p<='z';} static boolean side(char p,boolean w){return w?isW(p):isB(p);}
    ChessGame copy(){ChessGame c=new ChessGame();c.b=b.clone();c.white=white;c.wK=wK;c.wQ=wQ;c.bK=bK;c.bQ=bQ;c.ep=ep;c.result=result;return c;}
    List<Integer> pseudo(int from,boolean attacks){ArrayList<Integer> o=new ArrayList<>();char p=b[from];if(p==E)return o;boolean w=isW(p);int x=f(from),y=r(from);switch(Character.toLowerCase(p)){
      case 'p': {int d=w?-1:1, nr=y+d;if(!attacks&&nr>=0&&nr<8){int t=sq(x,nr);if(b[t]==E){o.add(t);int sy=w?6:1;if(y==sy&&b[sq(x,y+2*d)]==E)o.add(sq(x,y+2*d));}}for(int dx:new int[]{-1,1}){int nx=x+dx; if(nx<0||nx>7||nr<0||nr>7)continue;int t=sq(nx,nr);if(attacks||t==ep||(b[t]!=E&&!side(b[t],w)))o.add(t);}break;}
      case 'n': for(int[]d:new int[][]{{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}})add(o,x+d[0],y+d[1],w,attacks);break;
      case 'b': slide(o,x,y,w,new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}},attacks);break;
      case 'r': slide(o,x,y,w,new int[][]{{1,0},{-1,0},{0,1},{0,-1}},attacks);break;
      case 'q': slide(o,x,y,w,new int[][]{{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}},attacks);break;
      case 'k': for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)add(o,x+dx,y+dy,w,attacks);if(!attacks&&!check(w)){if(w&&wK&&b[sq(7,7)]=='R'&&b[sq(5,7)]==E&&b[sq(6,7)]==E&&!attacked(sq(5,7),false)&&!attacked(sq(6,7),false))o.add(sq(6,7));if(w&&wQ&&b[sq(0,7)]=='R'&&b[sq(1,7)]==E&&b[sq(2,7)]==E&&b[sq(3,7)]==E&&!attacked(sq(3,7),false)&&!attacked(sq(2,7),false))o.add(sq(2,7));if(!w&&bK&&b[sq(7,0)]=='r'&&b[sq(5,0)]==E&&b[sq(6,0)]==E&&!attacked(sq(5,0),true)&&!attacked(sq(6,0),true))o.add(sq(6,0));if(!w&&bQ&&b[sq(0,0)]=='r'&&b[sq(1,0)]==E&&b[sq(2,0)]==E&&b[sq(3,0)]==E&&!attacked(sq(3,0),true)&&!attacked(sq(2,0),true))o.add(sq(2,0));}break; }
      return o; }
    void add(List<Integer>o,int x,int y,boolean own,boolean attacks){if(x<0||x>7||y<0||y>7)return;int t=sq(x,y);if(attacks||b[t]==E||!side(b[t],own))o.add(t);}
    void slide(List<Integer>o,int x,int y,boolean own,int[][]ds,boolean attacks){for(int[]d:ds){int nx=x+d[0],ny=y+d[1];while(nx>=0&&nx<8&&ny>=0&&ny<8){int t=sq(nx,ny);if(b[t]==E)o.add(t);else{if(attacks||!side(b[t],own))o.add(t);break;}nx+=d[0];ny+=d[1];}}}
    boolean attacked(int t,boolean byW){for(int i=0;i<64;i++)if(side(b[i],byW)){char p=Character.toLowerCase(b[i]);if(p=='p'){int dx=f(t)-f(i),dy=r(t)-r(i);if(Math.abs(dx)==1&&dy==(byW?-1:1))return true;}else if(p=='n'){if(Math.abs(f(t)-f(i))*Math.abs(r(t)-r(i))==2)return true;}else if(p=='k'){if(Math.max(Math.abs(f(t)-f(i)),Math.abs(r(t)-r(i)))==1)return true;}else if(p=='b'||p=='r'||p=='q'){int dx=f(t)-f(i),dy=r(t)-r(i);boolean ok=p=='b'?Math.abs(dx)==Math.abs(dy):p=='r'?dx==0||dy==0:dx==0||dy==0||Math.abs(dx)==Math.abs(dy);if(ok){int sx=Integer.signum(dx),sy=Integer.signum(dy),cx=f(i)+sx,cy=r(i)+sy;boolean clear=true;while(sq(cx,cy)!=t){if(b[sq(cx,cy)]!=E){clear=false;break;}cx+=sx;cy+=sy;}if(clear)return true;}}}return false;}
    boolean check(boolean w){int k=-1;char king=w?'K':'k';for(int i=0;i<64;i++)if(b[i]==king){k=i;break;}return k<0||attacked(k,!w);}
    List<Move> legal(boolean w){ArrayList<Move>o=new ArrayList<>();for(int i=0;i<64;i++)if(side(b[i],w))for(int t:pseudo(i,false)){char pr=Character.toLowerCase(b[i])=='p'&&(r(t)==0||r(t)==7)?(w?'Q':'q'):0;Move m=new Move(i,t,pr);ChessGame c=copy();c.apply(m);if(!c.check(w))o.add(m);}return o;}
    Move find(int from,int to,char promotion){for(Move m:legal(white))if(m.from==from&&m.to==to&&(m.promotion==0||promotion==0||Character.toLowerCase(m.promotion)==Character.toLowerCase(promotion)))return m;return null;}
    boolean move(Move m){Move x=find(m.from,m.to,m.promotion);if(x==null)return false;apply(x);List<Move>n=legal(white);result=n.isEmpty()?(check(white)?(white?'B':'W'):'D'):' ';return true;}
    void apply(Move m){char p=b[m.from],cap=b[m.to];boolean w=isW(p);int oldEp=ep;ep=-1;if(Character.toLowerCase(p)=='p'&&m.to==oldEp&&cap==E&&f(m.from)!=f(m.to))b[w?m.to+8:m.to-8]=E;if(p=='K'){wK=wQ=false;}if(p=='k'){bK=bQ=false;}if(p=='R'){if(m.from==sq(0,7))wQ=false;if(m.from==sq(7,7))wK=false;}if(p=='r'){if(m.from==sq(0,0))bQ=false;if(m.from==sq(7,0))bK=false;}if(cap=='R'){if(m.to==sq(0,7))wQ=false;if(m.to==sq(7,7))wK=false;}if(cap=='r'){if(m.to==sq(0,0))bQ=false;if(m.to==sq(7,0))bK=false;}b[m.from]=E;b[m.to]=m.promotion==0?p:m.promotion;if(Character.toLowerCase(p)=='p'&&Math.abs(r(m.to)-r(m.from))==2)ep=(m.from+m.to)/2;if(p=='K'&&m.from==sq(4,7)&&m.to==sq(6,7)){b[sq(7,7)]=E;b[sq(5,7)]='R';}if(p=='K'&&m.from==sq(4,7)&&m.to==sq(2,7)){b[sq(0,7)]=E;b[sq(3,7)]='R';}if(p=='k'&&m.from==sq(4,0)&&m.to==sq(6,0)){b[sq(7,0)]=E;b[sq(5,0)]='r';}if(p=='k'&&m.from==sq(4,0)&&m.to==sq(2,0)){b[sq(0,0)]=E;b[sq(3,0)]='r';}white=!white;}
    String encode(){return new String(b)+"|"+(white?'w':'b')+"|"+(wK?'1':'0')+(wQ?'1':'0')+(bK?'1':'0')+(bQ?'1':'0')+"|"+ep+"|"+result;}
    void decode(String s){String[]a=s.split("\\|",-1);b=a[0].toCharArray();white=a[1].equals("w");wK=a[2].charAt(0)=='1';wQ=a[2].charAt(1)=='1';bK=a[2].charAt(2)=='1';bQ=a[2].charAt(3)=='1';ep=Integer.parseInt(a[3]);result=a[4].charAt(0);}
    static final class Move{final int from,to;final char promotion;Move(int f,int t,char p){from=f;to=t;promotion=p;}}
}
