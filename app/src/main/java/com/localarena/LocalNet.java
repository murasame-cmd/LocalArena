package com.localarena;

import android.os.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;

final class LocalNet {
 interface Listener{
  void connected(boolean host,String ip);
  void line(String line);
  void status(String s);
  void error(String e);
  void discovered(String ip);
 }
 static final int PORT=47821;
 static final int DISCOVERY_PORT=47822;
 static final int CONNECT_TIMEOUT_MS=180000;
 static final int HANDSHAKE_TIMEOUT_MS=180000;
 static final String PROTOCOL_VERSION="1";
 static final String DISCOVERY_REQUEST="LOCALARENA_DISCOVER|1";
 static final String DISCOVERY_RESPONSE="LOCALARENA_HOST|1";
 static final String TAG="LocalArenaNet";
 final Handler main; final Listener listener;
 volatile ServerSocket server; volatile Socket socket; volatile DatagramSocket discoverySocket;
 BufferedReader in; PrintWriter out;
 volatile boolean closing=false;

 LocalNet(Handler h,Listener l){main=h;listener=l;}

 void host(){
  close(); closing=false;
  startDiscoveryResponder();
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
    if(!closing)postError("Подключение: соединение отклонено — проверь IP хоста, одну Wi‑Fi сеть и фаервол");
   }catch(SocketTimeoutException e){
    logException("JOIN_CONNECT_TIMEOUT",e);
    if(!closing)postError("Подключение: таймаут 3 минуты — проверь, что оба телефона в одной Wi‑Fi сети и в ней разрешено общение между устройствами");
   }catch(Exception e){
    logException("JOIN_ERROR",e);
    if(!closing)postError("Подключение: "+safeMessage(e));
   }
  },"ArenaJoin").start();
 }

 void discover(){
  close(); closing=false;
  new Thread(()->{
   Set<String> found=new LinkedHashSet<>();
   try(DatagramSocket ds=new DatagramSocket()){
    ds.setBroadcast(true);
    ds.setSoTimeout(700);
    byte[] data=DISCOVERY_REQUEST.getBytes("UTF-8");
    List<InetAddress> targets=broadcastTargets();
    log("DISCOVERY_START targets="+targets);
    for(InetAddress target:targets){
     DatagramPacket p=new DatagramPacket(data,data.length,target,DISCOVERY_PORT);
     ds.send(p);
     log("DISCOVERY_TX target="+target.getHostAddress());
    }
    long deadline=System.currentTimeMillis()+3500;
    while(System.currentTimeMillis()<deadline&&!closing){
     try{
      byte[] buf=new byte[256];
      DatagramPacket p=new DatagramPacket(buf,buf.length);
      ds.receive(p);
      String msg=new String(p.getData(),p.getOffset(),p.getLength(),"UTF-8");
      log("DISCOVERY_RX from="+p.getAddress().getHostAddress()+" msg="+msg);
      if(msg.startsWith(DISCOVERY_RESPONSE+"|")){
       String ip=p.getAddress().getHostAddress();
       if(found.add(ip)){postDiscovered(ip);break;}
      }
     }catch(SocketTimeoutException ignored){}
    }
    if(found.isEmpty())postError("Хост не найден. Проверь, что оба телефона подключены к одной Wi‑Fi сети и роутер не изолирует устройства");
    else postStatus("Найден хост: "+found.iterator().next());
   }catch(Exception e){
    logException("DISCOVERY_ERROR",e);
    if(!closing)postError("Поиск хоста: "+safeMessage(e));
   }
  },"ArenaDiscovery").start();
 }

 void startDiscoveryResponder(){
  new Thread(()->{
   try{
    DatagramSocket ds=new DatagramSocket(null);
    ds.setReuseAddress(true);
    ds.bind(new InetSocketAddress(DISCOVERY_PORT));
    discoverySocket=ds;
    ds.setSoTimeout(1000);
    log("DISCOVERY_LISTENING port="+DISCOVERY_PORT);
    while(!closing){
     try{
      byte[] buf=new byte[256];
      DatagramPacket p=new DatagramPacket(buf,buf.length);
      ds.receive(p);
      String msg=new String(p.getData(),p.getOffset(),p.getLength(),"UTF-8");
      log("DISCOVERY_REQUEST from="+p.getAddress().getHostAddress()+" msg="+msg);
      if(DISCOVERY_REQUEST.equals(msg)){
       String response=DISCOVERY_RESPONSE+"|"+localIp()+"|"+PORT;
       byte[] out=response.getBytes("UTF-8");
       DatagramPacket reply=new DatagramPacket(out,out.length,p.getAddress(),p.getPort());
       ds.send(reply);
       log("DISCOVERY_REPLY to="+p.getAddress().getHostAddress()+" response="+response);
      }
     }catch(SocketTimeoutException ignored){}
    }
    ds.close();
   }catch(BindException e){
    logException("DISCOVERY_BIND_FAILED",e);
   }catch(Exception e){
    logException("DISCOVERY_RESPONDER_ERROR",e);
   }
  },"ArenaDiscoveryHost").start();
 }

 static List<InetAddress> broadcastTargets(){
  LinkedHashSet<InetAddress> result=new LinkedHashSet<>();
  try{result.add(InetAddress.getByName("255.255.255.255"));}catch(Exception ignored){}
  try{
   Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();
   while(e.hasMoreElements()){
    NetworkInterface n=e.nextElement();
    if(!n.isUp()||n.isLoopback())continue;
    for(InterfaceAddress ia:n.getInterfaceAddresses()){
     InetAddress b=ia.getBroadcast();
     if(b!=null)result.add(b);
    }
   }
  }catch(Exception ignored){}
  return new ArrayList<>(result);
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
  try{if(discoverySocket!=null)discoverySocket.close();}catch(Exception e){logException("CLOSE_DISCOVERY_ERROR",e);}
  socket=null;server=null;discoverySocket=null;in=null;out=null;
  log("CLOSE_DONE");
 }

 void postConnected(boolean h,String ip){main.post(()->listener.connected(h,ip));}
 void postStatus(String s){main.post(()->listener.status(s));}
 void postError(String s){main.post(()->listener.error(s));}
 void postDiscovered(String ip){main.post(()->listener.discovered(ip));}

 void log(String s){Log.d(TAG,s);}
 void logException(String stage,Exception e){Log.e(TAG,stage+" type="+e.getClass().getSimpleName()+" message="+safeMessage(e),e);}
 String safeMessage(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}

 static String localIp(){
  try{
   Enumeration<NetworkInterface>e=NetworkInterface.getNetworkInterfaces();
   String fallback=null;
   while(e.hasMoreElements()){
    NetworkInterface n=e.nextElement();
    if(!n.isUp()||n.isLoopback())continue;
    boolean wifi=n.getName()!=null&&(n.getName().startsWith("wlan")||n.getName().startsWith("wifi"));
    Enumeration<InetAddress>a=n.getInetAddresses();
    while(a.hasMoreElements()){
     InetAddress x=a.nextElement();String ip=x.getHostAddress();
     if(x instanceof Inet4Address&&!x.isLoopbackAddress()&&isPrivateIpv4(ip)){
      if(wifi)return ip;
      if(fallback==null)fallback=ip;
     }
    }
   }
   if(fallback!=null)return fallback;
  }catch(Exception ignored){}
  return "0.0.0.0";
 }

 static boolean isPrivateIpv4(String ip){
  return ip.startsWith("192.168.")||ip.startsWith("10.")||ip.startsWith("172.");
 }
}
