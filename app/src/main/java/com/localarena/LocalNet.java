package com.localarena;

import android.os.*;
import android.util.Log;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.security.NetworkSecurityPolicy;
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
  void hostAddress(String ip);
 }
 static final int PORT=47821;
 static final int DISCOVERY_PORT=47822;
 static final int CONNECT_TIMEOUT_MS=180000;
 static final int HANDSHAKE_TIMEOUT_MS=180000;
 static final String PROTOCOL_VERSION="1";
 static final String DISCOVERY_REQUEST="LOCALARENA_DISCOVER|1";
 static final String DISCOVERY_RESPONSE="LOCALARENA_HOST|1";
 static final String TAG="LocalArenaNet";
 final Handler main; final Listener listener; final Context appContext;
 volatile ServerSocket server; volatile Socket socket; volatile DatagramSocket discoverySocket;
 BufferedReader in; PrintWriter out;
 volatile boolean closing=false;

 LocalNet(Context context,Handler h,Listener l){appContext=context.getApplicationContext();main=h;listener=l;}

 void host(){
  close(); closing=false;
  new Thread(()->{
   bindToWifiNetwork("HOST_START");
   logNetworkEnvironment("HOST_START");
   startDiscoveryResponder();
   try{
    log("HOST_BIND_START port="+PORT);
    ServerSocket ss=new ServerSocket();
    ss.setReuseAddress(true);
    ss.bind(new InetSocketAddress("0.0.0.0",PORT));
    server=ss;
    String hostIp=localIp();
    log("HOST_LISTENING port="+PORT+" localIp="+hostIp+" bound="+ss.getLocalSocketAddress());
    postHostAddress(hostIp);
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
   bindToWifiNetwork("JOIN_START target="+target);
   logNetworkEnvironment("JOIN_START target="+target);
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
   bindToWifiNetwork("DISCOVERY_START");
   logNetworkEnvironment("DISCOVERY_START");
   Set<String> found=new LinkedHashSet<>();
   try{
    byte[] data=DISCOVERY_REQUEST.getBytes("UTF-8");
    List<InterfaceEndpoint> endpoints=interfaceEndpoints();
    log("DISCOVERY_INTERFACES "+endpoints);
    if(endpoints.isEmpty()){
     postError("Поиск хоста: не найден активный IPv4-интерфейс");
     return;
    }
    for(InterfaceEndpoint ep:endpoints){
     if(closing)break;
     try(DatagramSocket ds=new DatagramSocket(null)){
      ds.setReuseAddress(true);
      ds.setBroadcast(true);
      ds.bind(new InetSocketAddress(ep.address,0));
      ds.setSoTimeout(400);
      List<InetAddress> targets=new ArrayList<>();
      if(ep.broadcast!=null)targets.add(ep.broadcast);
      try{
       InetAddress global=InetAddress.getByName("255.255.255.255");
       if(!targets.contains(global))targets.add(global);
      }catch(Exception ignored){}
      log("DISCOVERY_SOCKET iface="+ep.name+" local="+ep.address.getHostAddress()+" broadcast="+(ep.broadcast==null?"-":ep.broadcast.getHostAddress())+" port="+ds.getLocalPort());
      for(InetAddress target:targets){
       DatagramPacket p=new DatagramPacket(data,data.length,target,DISCOVERY_PORT);
       ds.send(p);
       log("DISCOVERY_TX iface="+ep.name+" target="+target.getHostAddress());
      }
      long deadline=System.currentTimeMillis()+1400;
      while(System.currentTimeMillis()<deadline&&!closing){
       try{
        byte[] buf=new byte[256];
        DatagramPacket p=new DatagramPacket(buf,buf.length);
        ds.receive(p);
        String msg=new String(p.getData(),p.getOffset(),p.getLength(),"UTF-8");
        log("DISCOVERY_RX iface="+ep.name+" from="+p.getAddress().getHostAddress()+" msg="+msg);
        if(msg.startsWith(DISCOVERY_RESPONSE+"|")){
         String ip=p.getAddress().getHostAddress();
         if(found.add(ip)){
          postDiscovered(ip);
          break;
         }
        }
       }catch(SocketTimeoutException ignored){}
      }
     }catch(Exception e){
      logException("DISCOVERY_INTERFACE_ERROR iface="+ep.name,e);
     }
     if(!found.isEmpty())break;
    }
    if(found.isEmpty()&&!closing)postError("Хост не найден. Проверь, что оба телефона подключены к одной Wi‑Fi сети, имеют общий IPv4-сегмент и роутер не изолирует устройства");
    else if(!found.isEmpty())postStatus("Найден хост: "+found.iterator().next());
   }catch(Exception e){
    logException("DISCOVERY_ERROR",e);
    if(!closing)postError("Поиск хоста: "+safeMessage(e));
   }
  },"ArenaDiscovery").start();
 }

 void bindToWifiNetwork(String reason){
  try{
   ConnectivityManager cm=(ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
   Network active=cm.getActiveNetwork();
   Network wifi=null;
   if(active!=null){
    NetworkCapabilities caps=cm.getNetworkCapabilities(active);
    if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))wifi=active;
   }
   if(wifi==null){
    for(Network n:cm.getAllNetworks()){
     NetworkCapabilities caps=cm.getNetworkCapabilities(n);
     if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
      wifi=n;break;
     }
    }
   }
   if(wifi!=null){
    boolean ok=cm.bindProcessToNetwork(wifi);
    log("WIFI_BIND reason="+reason+" network="+wifi+" ok="+ok);
   }else log("WIFI_BIND reason="+reason+" no_wifi_network");
  }catch(Exception e){logException("WIFI_BIND_ERROR reason="+reason,e);}
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

 static final class InterfaceEndpoint{
  final String name; final InetAddress address; final InetAddress broadcast;
  InterfaceEndpoint(String n,InetAddress a,InetAddress b){name=n;address=a;broadcast=b;}
  @Override public String toString(){return name+"="+address.getHostAddress()+"/"+(broadcast==null?"-":broadcast.getHostAddress());}
 }

 static List<InterfaceEndpoint> interfaceEndpoints(){
  List<InterfaceEndpoint> result=new ArrayList<>();
  try{
   Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();
   while(e.hasMoreElements()){
    NetworkInterface n=e.nextElement();
    if(!n.isUp()||n.isLoopback()||n.isVirtual())continue;
    for(InterfaceAddress ia:n.getInterfaceAddresses()){
     InetAddress a=ia.getAddress();
     if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&isPrivateIpv4(a.getHostAddress())){
      result.add(new InterfaceEndpoint(n.getName(),a,ia.getBroadcast()));
     }
    }
   }
  }catch(Exception ignored){}
  result.sort((a,b)->{
   boolean aw=isWifiName(a.name),bw=isWifiName(b.name);
   return aw==bw?0:(aw?-1:1);
  });
  return result;
 }

 static boolean isWifiName(String name){
  if(name==null)return false;
  String n=name.toLowerCase(Locale.US);
  return n.startsWith("wlan")||n.startsWith("wifi")||n.startsWith("swlan");
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
   List<InterfaceEndpoint> eps=interfaceEndpoints();
   for(InterfaceEndpoint ep:eps)if(isWifiName(ep.name))return ep.address.getHostAddress();
   if(!eps.isEmpty())return eps.get(0).address.getHostAddress();
  }catch(Exception ignored){}
  return "0.0.0.0";
 }

 static boolean isPrivateIpv4(String ip){
  if(ip==null)return false;
  if(ip.startsWith("10."))return true;
  if(ip.startsWith("192.168."))return true;
  if(ip.startsWith("172.")){
   String[] p=ip.split("\\.");
   if(p.length<2)return false;
   try{int second=Integer.parseInt(p[1]);return second>=16&&second<=31;}catch(NumberFormatException ignored){}
  }
  return false;
 }

 void logNetworkEnvironment(String stage){
  try{
   ConnectivityManager cm=(ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
   Network active=cm==null?null:cm.getActiveNetwork();
   NetworkCapabilities caps=active==null||cm==null?null:cm.getNetworkCapabilities(active);
   LinkProperties lp=active==null||cm==null?null:cm.getLinkProperties(active);
   StringBuilder s=new StringBuilder(stage);
   s.append(" localIp=").append(localIp());
   s.append(" cleartextPolicy=").append(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted());
   if(caps!=null){
    s.append(" wifi=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    s.append(" ethernet=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
   }
   if(lp!=null){
    s.append(" iface=").append(lp.getInterfaceName());
    s.append(" addresses=").append(lp.getLinkAddresses());
    s.append(" routes=").append(lp.getRoutes());
   }
   log("NETWORK_ENV "+s);
  }catch(Exception e){logException("NETWORK_ENV_ERROR",e);}
 }
}
