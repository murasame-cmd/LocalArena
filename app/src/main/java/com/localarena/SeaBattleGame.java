package com.localarena;

import java.util.*;

final class SeaBattleGame {
    int[][][] cells={new int[10][10],new int[10][10]}; boolean[] ready={false,false}; boolean turn=true; int winner=-1;
    SeaBattleGame(){reset();}
    void reset(){cells=new int[][][]{new int[10][10],new int[10][10]};ready[0]=ready[1]=false;turn=true;winner=-1;}
    void randomPlace(int p,long seed){Random r=new Random(seed);cells[p]=new int[10][10];int[] lengths={4,3,3,2,2,2,1,1,1,1};for(int len:lengths){boolean ok=false;for(int tries=0;tries<500&&!ok;tries++){int x=r.nextInt(10),y=r.nextInt(10);boolean h=r.nextBoolean();if(h&&x+len>10)continue;if(!h&&y+len>10)continue;boolean good=true;for(int i=0;i<len;i++){int xx=x+(h?i:0),yy=y+(h?0:i);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){int nx=xx+dx,ny=yy+dy;if(nx>=0&&nx<10&&ny>=0&&ny<10&&cells[p][ny][nx]==1)good=false;}}if(good){for(int i=0;i<len;i++)cells[p][y+(h?0:i)][x+(h?i:0)]=1;ok=true;}}}}
    boolean shoot(int shooter,int x,int y){if(winner>=0||!ready[0]||!ready[1]||shooter!=(turn?0:1)||x<0||x>9||y<0||y>9)return false;int[][]target=cells[1-shooter];if(target[y][x]==2||target[y][x]==3)return false;if(target[y][x]==1){target[y][x]=3;if(allSunk(target))winner=shooter;}else{target[y][x]=2;turn=!turn;}return true;}
    boolean allSunk(int[][]g){for(int[]row:g)for(int c:row)if(c==1)return false;return true;}
    String encode(){return encodeFor(0);}
    String encodeFor(int viewer){StringBuilder s=new StringBuilder();s.append(turn?'0':'1').append(',').append(winner).append(',').append(ready[0]?'1':'0').append(ready[1]?'1':'0');for(int p=0;p<2;p++){s.append('|');for(int y=0;y<10;y++)for(int x=0;x<10;x++){int v=cells[p][y][x];if(p!=viewer&&v==1)v=0;s.append(v);}}return s.toString();}
    void decode(String s){String[]a=s.split("\\|",-1);String[]h=a[0].split(",");turn=h[0].equals("0");winner=Integer.parseInt(h[1]);ready[0]=h[2].equals("1");ready[1]=h[3].equals("1");for(int p=0;p<2;p++){String q=a[p+1];int k=0;for(int y=0;y<10;y++)for(int x=0;x<10;x++)cells[p][y][x]=q.charAt(k++)-'0';}}
}
