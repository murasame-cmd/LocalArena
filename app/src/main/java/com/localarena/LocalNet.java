package com.localarena;

import android.os.*;
import java.io.*;
import java.net.*;

final class LocalNet {
 interface Listener{void connected(boolean host,String ip);void line(String line);void status(String s);void error(String e);}
 static final int PORT=47821; final Handler main; final Listener listener; ServerSocket server; Socket socket; BufferedReader in; PrintWriter out;
 LocalNet(Handler h,Listener l){main=h;listener=l;}
 void host(){close();new Thread(()->{try{server=new ServerSocket(PORT);postStatus("Ждём второго игрока…");Socket s=server.accept();setup(s);postConnected(true,localIp());read();}catch(Exception e){postError("Хост: "+e.getMessage());}},"ArenaHost").start();}
 void join(String ip){close();new Thread(()->{try{postStatus("Подключение к "+ip+"…");Socket s=new Socket();s.connect(new InetSocketAddress(ip,PORT),5000);setup(s);postConnected(false,ip);send("HELLO");read();}catch(Exception e){postError("Подключение: "+e.getMessage());}},"ArenaJoin").start();}
 void setup(Socket s)throws IOException{socket=s;in=new BufferedReader(new InputStreamReader(s.getInputStream()));out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())),true);}
 void read()throws IOException{String x;while((x=in.readLine())!=null){final String line=x;main.post(()->listener.line(line));}postError("Соединение закрыто");}
 synchronized void send(String s){if(out!=null){out.println(s);out.flush();}}
 synchronized void close(){try{if(socket!=null)socket.close();}catch(Exception ignored){}try{if(server!=null)server.close();}catch(Exception ignored){}socket=null;server=null;in=null;out=null;}
 void postConnected(boolean h,String ip){main.post(()->listener.connected(h,ip));}void postStatus(String s){main.post(()->listener.status(s));}void postError(String s){main.post(()->listener.error(s));}
 static String localIp(){try{java.util.Enumeration<NetworkInterface>e=NetworkInterface.getNetworkInterfaces();while(e.hasMoreElements()){NetworkInterface n=e.nextElement();java.util.Enumeration<InetAddress>a=n.getInetAddresses();while(a.hasMoreElements()){InetAddress x=a.nextElement();String ip=x.getHostAddress();if(x instanceof Inet4Address&&!x.isLoopbackAddress()&&(ip.startsWith("192.168.")||ip.startsWith("10.")||ip.startsWith("172.")))return ip;}}}catch(Exception ignored){}return "192.168.43.1";}
}
