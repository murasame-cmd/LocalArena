package com.localarena;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LocalNet {
 interface Listener {
  void connected(boolean host,String ip);
  void line(String line);
  void status(String s);
  void error(String e);
  void discovered(String ip);
  void hostAddress(String ip);
 }

 // Deliberately simple architecture:
 // 1) TCP is the source of truth for gameplay.
 // 2) UDP broadcast is only an optional room-discovery helper.
 // 3) Manual IP connection always remains available.
 static final int PORT=47821;
 static final int DISCOVERY_PORT=47822;
 static final int CONNECT_TIMEOUT_MS=10000;
 static final int HANDSHAKE_TIMEOUT_MS=10000;
 static final int DISCOVERY_WINDOW_MS=2500;
 static final String PROTOCOL_VERSION="2";
 static final String DISCOVERY_REQUEST="LOCALARENA_DISCOVER|2";
 static final String DISCOVERY_RESPONSE="LOCALARENA_HOST|2";
 static final String TAG="LocalArenaNet";

 final Handler main;
 final Listener listener;
 final Context appContext;

 volatile ServerSocket server;
 volatile Socket socket;
 volatile DatagramSocket discoverySocket;
 volatile boolean closing=true;
  volatile long generation=0;

 BufferedReader in;
 PrintWriter out;

 LocalNet(Context context,Handler h,Listener l){
  appContext=context.getApplicationContext();
  main=h;
  listener=l;
 }

 void host(){
  close();
  closing=false;
  final long runId=generation;
  new Thread(()->{
   try{
    logNetworkEnvironment("HOST_START");
    startDiscoveryResponder();

    ServerSocket ss=new ServerSocket();
    ss.setReuseAddress(true);
    ss.bind(new InetSocketAddress("0.0.0.0",PORT));
    server=ss;

    String hostIp=localIp();
    log("HOST_LISTENING ip="+hostIp+" port="+PORT);
    postHostAddress(runId,hostIp);
    postStatus(runId,"Сервер запущен. Ждём второго игрока…");

    Socket s=ss.accept();
    if(!active(runId)){safeClose(s);return;}

    log("HOST_ACCEPTED remote="+s.getRemoteSocketAddress());
    setup(s);
    handshake(true);

    postConnected(runId,true,hostIp);
    postStatus(runId,"🟢 Телефон подключён");
    readLoop(runId);
   }catch(BindException e){
    logException("HOST_BIND_FAILED",e);
    if(active(runId))postError(runId,"Хост: не удалось открыть порт "+PORT+". Попробуй закрыть старую комнату и создать её заново.");
   }catch(Exception e){
    logException("HOST_ERROR",e);
    if(active(runId))postError(runId,"Хост: "+safeMessage(e));
   }finally{
    if(generation==runId)close();
   }
  },"ArenaHost").start();
 }

 void join(String address){
  close();
  closing=false;
  final long runId=generation;
  final String target=address==null?"":address.trim();

  new Thread(()->{
   try{
    if(!isValidIpv4(target)){
     throw new IOException("Неверный IPv4-адрес");
    }

    logNetworkEnvironment("JOIN_START target="+target);
    postStatus(runId,"Подключение к "+target+"…");

    Socket s=new Socket();
    s.setTcpNoDelay(true);
    s.setKeepAlive(true);
    s.connect(new InetSocketAddress(target,PORT),CONNECT_TIMEOUT_MS);

    log("JOIN_CONNECTED remote="+s.getRemoteSocketAddress()+" local="+s.getLocalSocketAddress());
    setup(s);
    handshake(false);

    postConnected(runId,false,target);
    postStatus(runId,"🟢 Соединение установлено");
    readLoop(runId);
   }catch(ConnectException e){
    logException("JOIN_REFUSED",e);
    if(active(runId))postError(runId,"Подключение отклонено. Проверь IP хоста, одну Wi‑Fi сеть и отсутствие изоляции клиентов.");
   }catch(SocketTimeoutException e){
    logException("JOIN_TIMEOUT",e);
    if(active(runId))postError(runId,"Не удалось подключиться за 10 секунд. Проверь IP и сеть.");
   }catch(Exception e){
    logException("JOIN_ERROR",e);
    if(active(runId))postError(runId,"Подключение: "+safeMessage(e));
   }finally{
    if(generation==runId)close();
   }
  },"ArenaJoin").start();
 }

 void discover(){
  close();
  closing=false;
  final long runId=generation;

  new Thread(()->{
   Set<String> found=new LinkedHashSet<>();
   try{
    List<InterfaceEndpoint> endpoints=interfaceEndpoints();
    log("DISCOVERY_INTERFACES "+endpoints);

    if(endpoints.isEmpty()){
     postError(runId,"Поиск хоста: не найден активный IPv4-интерфейс.");
     return;
    }

    byte[] request=DISCOVERY_REQUEST.getBytes(StandardCharsets.UTF_8);

    for(InterfaceEndpoint ep:endpoints){
     if(!active(runId))break;

     try(DatagramSocket ds=new DatagramSocket(null)){
      ds.setReuseAddress(true);
      ds.setBroadcast(true);
      ds.bind(new InetSocketAddress(ep.address,0));
      ds.setSoTimeout(300);

      LinkedHashSet<InetAddress> targets=new LinkedHashSet<>();
      if(ep.broadcast!=null)targets.add(ep.broadcast);
      targets.add(InetAddress.getByName("255.255.255.255"));

      for(InetAddress target:targets){
       DatagramPacket p=new DatagramPacket(request,request.length,target,DISCOVERY_PORT);
       ds.send(p);
       log("DISCOVERY_TX iface="+ep.name+" target="+target.getHostAddress());
      }

      long deadline=System.currentTimeMillis()+DISCOVERY_WINDOW_MS;
      while(System.currentTimeMillis()<deadline&&active(runId)){
       try{
        byte[] buf=new byte[256];
        DatagramPacket p=new DatagramPacket(buf,buf.length);
        ds.receive(p);

        String msg=new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8);
        String sender=p.getAddress().getHostAddress();
        log("DISCOVERY_RX from="+sender+" msg="+msg);

        if(msg.startsWith(DISCOVERY_RESPONSE+"|") && found.add(sender)){
         postDiscovered(runId,sender);
         postStatus(runId,"Найден хост: "+sender);
         return;
        }
       }catch(SocketTimeoutException ignored){}
      }
     }catch(Exception e){
      logException("DISCOVERY_INTERFACE_ERROR "+ep.name,e);
     }
    }

    if(active(runId)){
     postError(runId,"Автопоиск не нашёл хост. Введи IP хоста вручную — это основной fallback.");
    }
   }catch(Exception e){
    logException("DISCOVERY_ERROR",e);
    if(active(runId))postError(runId,"Поиск хоста: "+safeMessage(e));
   }
  },"ArenaDiscovery").start();
 }

 void startDiscoveryResponder(){
  final long runId=generation;
  new Thread(()->{
   try{
    DatagramSocket ds=new DatagramSocket(null);
    ds.setReuseAddress(true);
    ds.setBroadcast(true);
    ds.bind(new InetSocketAddress(DISCOVERY_PORT));
    discoverySocket=ds;
    ds.setSoTimeout(500);

    log("DISCOVERY_LISTENING port="+DISCOVERY_PORT);

    while(active(runId)){
     try{
      byte[] buf=new byte[256];
      DatagramPacket p=new DatagramPacket(buf,buf.length);
      ds.receive(p);

      String msg=new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8);
      if(!DISCOVERY_REQUEST.equals(msg))continue;

      String response=DISCOVERY_RESPONSE+"|"+localIp()+"|"+PORT;
      byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
      DatagramPacket reply=new DatagramPacket(bytes,bytes.length,p.getAddress(),p.getPort());
      ds.send(reply);

      log("DISCOVERY_REPLY to="+p.getAddress().getHostAddress()+" response="+response);
     }catch(SocketTimeoutException ignored){}
     catch(SocketException e){
      if(active(runId))logException("DISCOVERY_SOCKET_ERROR",e);
      break;
     }
    }
    safeClose(ds);
   }catch(BindException e){
    // Discovery is optional. A discovery bind failure must never prevent TCP hosting.
    logException("DISCOVERY_BIND_FAILED_OPTIONAL",e);
   }catch(Exception e){
    logException("DISCOVERY_RESPONDER_ERROR",e);
   }
  },"ArenaDiscoveryHost").start();
 }

 void setup(Socket s)throws IOException{
  socket=s;
  s.setTcpNoDelay(true);
  s.setKeepAlive(true);
  s.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

  in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
  out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8)),true);

  log("SOCKET_SETUP remote="+s.getRemoteSocketAddress()+" local="+s.getLocalSocketAddress());
 }

 // A deliberately tiny, line-framed handshake inspired by LAN chess projects:
 // HOST -> HELLO|2
 // GUEST -> HELLO_ACK|2
 void handshake(boolean hostSide)throws IOException{
  log("HANDSHAKE_START side="+(hostSide?"HOST":"GUEST"));

  if(hostSide){
   send("HELLO|"+PROTOCOL_VERSION);
   String line=in.readLine();
   log("HANDSHAKE_RX side=HOST line="+line);
   if(!("HELLO_ACK|"+PROTOCOL_VERSION).equals(line)){
    throw new IOException("Неверное рукопожатие");
   }
  }else{
   String line=in.readLine();
   log("HANDSHAKE_RX side=GUEST line="+line);
   if(!("HELLO|"+PROTOCOL_VERSION).equals(line)){
    throw new IOException("Неверное рукопожатие");
   }
   send("HELLO_ACK|"+PROTOCOL_VERSION);
  }

  socket.setSoTimeout(0);
  log("HANDSHAKE_OK side="+(hostSide?"HOST":"GUEST"));
 }

 void readLoop(long runId)throws IOException{
  log("READ_LOOP_START");

  String line;
  while(active(runId) && (line=in.readLine())!=null){
   final String message=line;
   if(message.length()>8192){
    log("RX_REJECT_TOO_LONG len="+message.length());
    continue;
   }
   log("RX "+message);
   main.post(()->{if(active(runId))listener.line(message);});
  }

  if(active(runId)){
   postError(runId,"Соединение закрыто");
   log("READ_EOF");
  }
 }

 synchronized void send(String message){
  if(closing || out==null){
   log("TX_SKIPPED message="+message);
   return;
  }

  if(message==null || message.indexOf('\n')>=0 || message.indexOf('\r')>=0){
   log("TX_REJECT_BAD_FRAME");
   return;
  }

  out.println(message);
  out.flush();

  if(out.checkError()){
   log("TX_ERROR");
  }else{
   log("TX "+message);
  }
 }

 synchronized void close(){
  generation++;
  boolean wasOpen=!closing || socket!=null || server!=null || discoverySocket!=null;
  closing=true;

  if(wasOpen)log("CLOSE_START");

  safeClose(socket);
  safeClose(server);
  safeClose(discoverySocket);

  socket=null;
  server=null;
  discoverySocket=null;
  in=null;
  out=null;

  if(wasOpen)log("CLOSE_DONE");
 }

 static void safeClose(Closeable c){
  if(c==null)return;
  try{c.close();}catch(Exception ignored){}
 }

 static void safeClose(Socket s){
  if(s==null)return;
  try{s.close();}catch(Exception ignored){}
 }

 boolean active(long id){return !closing && generation==id;}
 void postConnected(long id,boolean h,String ip){main.post(()->{if(active(id))listener.connected(h,ip);});}
 void postStatus(long id,String s){main.post(()->{if(active(id))listener.status(s);});}
 void postError(long id,String s){main.post(()->{if(active(id))listener.error(s);});}
 void postDiscovered(long id,String ip){main.post(()->{if(active(id))listener.discovered(ip);});}
 void postHostAddress(long id,String ip){main.post(()->{if(active(id))listener.hostAddress(ip);});}
 void log(String s){Log.d(TAG,s);}
 void logException(String stage,Exception e){Log.e(TAG,stage+" type="+e.getClass().getSimpleName()+" message="+safeMessage(e),e);}
 String safeMessage(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}

 static boolean isValidIpv4(String ip){
  if(ip==null)return false;
  String[] p=ip.split("\\.",-1);
  if(p.length!=4)return false;
  for(String part:p){
   try{
    if(part.isEmpty()||part.length()>3)return false;
    int n=Integer.parseInt(part);
    if(n<0||n>255)return false;
   }catch(NumberFormatException e){return false;}
  }
  return true;
 }

 static final class InterfaceEndpoint{
  final String name;
  final InetAddress address;
  final InetAddress broadcast;

  InterfaceEndpoint(String n,InetAddress a,InetAddress b){
   name=n;
   address=a;
   broadcast=b;
  }

  @Override public String toString(){
   return name+"="+address.getHostAddress()+"/"+(broadcast==null?"-":broadcast.getHostAddress());
  }
 }

 static List<InterfaceEndpoint> interfaceEndpoints(){
  List<InterfaceEndpoint> result=new ArrayList<>();

  try{
   Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
   while(interfaces.hasMoreElements()){
    NetworkInterface ni=interfaces.nextElement();
    if(!ni.isUp()||ni.isLoopback()||ni.isVirtual())continue;

    for(InterfaceAddress ia:ni.getInterfaceAddresses()){
     InetAddress address=ia.getAddress();
     if(address instanceof Inet4Address&&!address.isLoopbackAddress()&&isPrivateIpv4(address.getHostAddress())){
      result.add(new InterfaceEndpoint(ni.getName(),address,ia.getBroadcast()));
     }
    }
   }
  }catch(Exception e){
   Log.e(TAG,"INTERFACE_ENUM_ERROR",e);
  }

  result.sort((a,b)->{
   boolean aw=isWifiName(a.name);
   boolean bw=isWifiName(b.name);
   if(aw==bw)return 0;
   return aw?-1:1;
  });

  return result;
 }

 static boolean isWifiName(String name){
  if(name==null)return false;
  String n=name.toLowerCase(Locale.US);
  return n.startsWith("wlan")||n.startsWith("wifi")||n.startsWith("swlan");
 }

 static boolean isPrivateIpv4(String ip){
  if(ip==null)return false;
  if(ip.startsWith("10."))return true;
  if(ip.startsWith("192.168."))return true;
  if(ip.startsWith("172.")){
   String[] p=ip.split("\\.");
   if(p.length<2)return false;
   try{
    int second=Integer.parseInt(p[1]);
    return second>=16&&second<=31;
   }catch(NumberFormatException ignored){}
  }
  return false;
 }

 static String localIp(){
  try{
   List<InterfaceEndpoint> endpoints=interfaceEndpoints();

   for(InterfaceEndpoint ep:endpoints){
    if(isWifiName(ep.name))return ep.address.getHostAddress();
   }

   if(!endpoints.isEmpty())return endpoints.get(0).address.getHostAddress();
  }catch(Exception ignored){}

  return "0.0.0.0";
 }

 void logNetworkEnvironment(String stage){
  try{
   ConnectivityManager cm=(ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
   Network active=cm==null?null:cm.getActiveNetwork();
   NetworkCapabilities caps=active==null||cm==null?null:cm.getNetworkCapabilities(active);
   LinkProperties lp=active==null||cm==null?null:cm.getLinkProperties(active);

   StringBuilder s=new StringBuilder(stage);
   s.append(" localIp=").append(localIp());

   if(caps!=null){
    s.append(" wifi=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    s.append(" validated=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
   }

   if(lp!=null){
    s.append(" iface=").append(lp.getInterfaceName());
    s.append(" addresses=").append(lp.getLinkAddresses());
    s.append(" routes=").append(lp.getRoutes());
   }

   log("NETWORK_ENV "+s);
  }catch(Exception e){
   logException("NETWORK_ENV_ERROR",e);
  }
 }
}
