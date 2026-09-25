package com.localarena;

import android.os.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.Enumeration;

final class LocalNet {
 interface Listener{void connected(boolean host,String ip);void line(String line);void status(String s);void error(String e);}
 static final int PORT=47821;
 static final int CONNECT_TIMEOUT_MS=180000;
 static final int HANDSHAKE_TIMEOUT_MS=180000;
 static final String PROTOCOL_VERSION="1";
 static final String TAG="LocalArenaNet";
 final Handler main; final Listener listener;
 volatile ServerSocket server; volatile Socket socket; BufferedReader in; PrintWriter out;
 volatile boolean closing=false;

 LocalNet(Handler h,Listener l){main=h;listener=l;}

 void host(){
  close(); closing=false;
  new Thread(()->{
   try{
    log("HOST_BIND_START port="+PORT);
    ServerSocket ss=new ServerSocket();
    ss.setReuseAddress(true);
    ss.bind(new InetSocketAddress("0.0.0.0",PORT));
    server=ss;
    log("HOST_LISTENING port="+PORT+" localIp="+localIp()+" bound="+ss.getLocalSocketAddress());
    postStatus("Ждём второго игрока…");
    ss.setSoTimeout(CONNECT_TIMEOUT_MS);
    log("HOST_ACCEPT_WAIT timeoutMs="+CONNECT_TIMEOUT_MS);
    Socket s=ss.accept();
    log("HOST_ACCEPTED remote="+s.getRemoteSocketAddress());
    setup(s);
    handshake(true);
    postConnected(true,localIp());
    read();
   }catch(SocketTimeoutException e){
    log("HOST_ACCEPT_TIMEOUT");
    if(!closing)postError("Хост: ожидание подключения истекло (3 минуты)");
   }catch(BindException e){
    log("HOST_BIND_FAILED message="+e.getMessage());
    if(!closing)postError("Хост: не удалось открыть порт "+PORT+" — возможно, порт занят или заблокирован");
   }catch(Exception e){
    logException("HOST_ERROR",e);
    if(!closing)postError("Хост: "+safeMessage(e));
   }
  },"ArenaHost").start();
 }

 void join(String ip){
  close(); closing=false;
  final String target=ip==null?"":ip.trim();
  new Thread(()->{
   try{
    postStatus("Подключение к "+target+"…");
    log("JOIN_CONNECT_START target="+target+" port="+PORT+" timeoutMs="+CONNECT_TIMEOUT_MS);
    Socket s=new Socket();
    s.setReuseAddress(true);
    s.connect(new InetSocketAddress(target,PORT),CONNECT_TIMEOUT_MS);
    log("JOIN_CONNECTED remote="+s.getRemoteSocketAddress()+" local="+s.getLocalSocketAddress());
    setup(s);
    handshake(false);
    postConnected(false,target);
    read();
   }catch(ConnectException e){
    logException("JOIN_CONNECT_REFUSED",e);
    if(!closing)postError("Подключение: соединение отклонено — проверь IP хоста и фаервол");
   }catch(SocketTimeoutException e){
    logException("JOIN_CONNECT_TIMEOUT",e);
    if(!closing)postError("Подключение: таймаут 3 минуты — проверь Wi‑Fi, IP и фаервол");
   }catch(Exception e){
    logException("JOIN_ERROR",e);
    if(!closing)postError("Подключение: "+safeMessage(e));
   }
  },"ArenaJoin").start();
 }

 void setup(Socket s)throws IOException{
  socket=s;
  s.setKeepAlive(true);
  s.setTcpNoDelay(true);
  s.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
  in=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8"));
  out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),"UTF-8")),true);
  log("SOCKET_SETUP remote="+s.getRemoteSocketAddress()+" local="+s.getLocalSocketAddress());
 }

 void handshake(boolean hostSide)throws IOException{
  log("HANDSHAKE_START side="+(hostSide?"HOST":"GUEST"));
  if(hostSide){
   send("HELLO|"+PROTOCOL_VERSION);
   String line=in.readLine();
   log("HANDSHAKE_RX side=HOST line="+line);
   if(!("HELLO_ACK|"+PROTOCOL_VERSION).equals(line))throw new IOException("Неверное рукопожатие");
  }else{
   String line=in.readLine();
   log("HANDSHAKE_RX side=GUEST line="+line);
   if(!("HELLO|"+PROTOCOL_VERSION).equals(line))throw new IOException("Неверное рукопожатие");
   send("HELLO_ACK|"+PROTOCOL_VERSION);
  }
  socket.setSoTimeout(0);
  log("HANDSHAKE_OK side="+(hostSide?"HOST":"GUEST")+" protocol="+PROTOCOL_VERSION);
 }

 void read()throws IOException{
  log("READ_LOOP_START");
  String x;
  while(!closing && (x=in.readLine())!=null){
   final String line=x;
   log("RX "+line);
   main.post(()->listener.line(line));
  }
  if(!closing){
   log("READ_EOF");
   postError("Соединение закрыто");
  }else log("READ_STOPPED_BY_CLOSE");
 }

 synchronized void send(String s){
  if(out!=null&&!closing){
   log("TX "+s);
   out.println(s);out.flush();
   if(out.checkError())log("TX_ERROR checkError=true");
  }else log("TX_SKIPPED no_socket");
 }

 synchronized void close(){
  closing=true;
  log("CLOSE_START");
  try{if(socket!=null)socket.close();}catch(Exception e){logException("CLOSE_SOCKET_ERROR",e);}
  try{if(server!=null)server.close();}catch(Exception e){logException("CLOSE_SERVER_ERROR",e);}
  socket=null;server=null;in=null;out=null;
  log("CLOSE_DONE");
 }

 void postConnected(boolean h,String ip){main.post(()->listener.connected(h,ip));}
 void postStatus(String s){main.post(()->listener.status(s));}
 void postError(String s){main.post(()->listener.error(s));}

 void log(String s){Log.d(TAG,s);}
 void logException(String stage,Exception e){Log.e(TAG,stage+" type="+e.getClass().getSimpleName()+" message="+safeMessage(e),e);}
 String safeMessage(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}

 static String localIp(){
  try{
   Enumeration<NetworkInterface>e=NetworkInterface.getNetworkInterfaces();
   while(e.hasMoreElements()){
    NetworkInterface n=e.nextElement();
    if(!n.isUp()||n.isLoopback())continue;
    Enumeration<InetAddress>a=n.getInetAddresses();
    while(a.hasMoreElements()){
     InetAddress x=a.nextElement();String ip=x.getHostAddress();
     if(x instanceof Inet4Address&&!x.isLoopbackAddress()&&(ip.startsWith("192.168.")||ip.startsWith("10.")||ip.startsWith("172.")))return ip;
    }
   }
  }catch(Exception ignored){}
  return "192.168.43.1";
 }
}
