package app.Peer.Client.Net;

import app.Models.PeerHosts;
import app.Peer.Client.ClientCenter.ClientControlCenter;
import app.Peer.Client.Gui;
import app.Peer.Client.Net.blockingqueue.ClientNetGetMsg;
import app.Peer.Client.Net.blockingqueue.ClientNetPutMsg;
import app.Peer.Client.gui.GuiController;
import app.Peer.Client.gui.LoginWindow;
import app.Protocols.RaftProtocol.RegisterProtocol;
import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

public class ClientNet implements Runnable {
    private String tag = "Net";
    private static Logger logger = Logger.getLogger(ClientNet.class);
    private final BlockingQueue<String> fromCenter;
    private final BlockingQueue<String> toCenter;
    private final BlockingQueue<String> toNetPutMsg;
    private boolean flag = true;
    private ThreadFactory threadForSocket;
    private ExecutorService pool;
    private HashMap<String, Socket> peerNameSocketMap;

    public HashMap<String, Socket> getPeerNameSocketMap() {
        return peerNameSocketMap;
    }

    public void putPeerNameSocketMap(String clientName, Socket socket) {
        this.peerNameSocketMap.put(clientName, socket);
    }

    public int getLeaderID() {
        return leaderID;
    }

    public void setLeaderID(int leaderID) {
        this.leaderID = leaderID;
    }

    private int leaderID;
    private String ipAddr;
    private int portNum;
    private Socket leaderSocket;
    private String userName;

    private ArrayList<PeerHosts> peerHosts;

    public ArrayList<Socket> getConnectedPeerSockets() {
        return connectedPeerSockets;
    }

    public void setConnectedPeerSockets(ArrayList<Socket> connectedPeers) {
        this.connectedPeerSockets = connectedPeers;
    }

    private ArrayList<Socket> connectedPeerSockets;
    private ArrayList<PeerHosts> connectedPeerHosts;

    public Socket getLeaderSocket() {
        return leaderSocket;
    }

    public void setLeaderSocket(Socket leaderSocket) {
        this.leaderSocket = leaderSocket;
    }


    public ArrayList<PeerHosts> getPeerHosts() {
        return peerHosts;
    }

    public void setPeerHosts(ArrayList<PeerHosts> peerHosts) {
        this.peerHosts = peerHosts;
    }


    public ClientNet(BlockingQueue fromNet, BlockingQueue toNet, String ipAddr, int portNum, String userName) {
        this.toCenter = fromNet;
        this.fromCenter = toNet;
        toNetPutMsg = new LinkedBlockingQueue<>();
        this.ipAddr = ipAddr;
        this.portNum = portNum;
        peerHosts = new ArrayList<PeerHosts>();
        connectedPeerSockets = new ArrayList<Socket>();
        connectedPeerHosts = new ArrayList<PeerHosts>();
        this.peerNameSocketMap = new HashMap<>();
    }

    private ServerSocket server;

    private volatile static ClientNet net;

    private ClientNet() {
        fromCenter = new LinkedBlockingQueue<>();
        toCenter = new LinkedBlockingQueue<>();
        toNetPutMsg = new LinkedBlockingQueue<>();

        peerHosts = new ArrayList<PeerHosts>();
        connectedPeerSockets = new ArrayList<Socket>();
        connectedPeerHosts = new ArrayList<PeerHosts>();
        this.peerNameSocketMap = new HashMap<>();
    }


    public static ClientNet getInstance() {
        if (net == null) {
            synchronized (ClientNet.class) {
                if (net == null) {
                    net = new ClientNet();
                }
            }
        }
        return net;
    }

    public static ClientNet getInstance(BlockingQueue fromNet, BlockingQueue toNet, String ipAddr, int portNum, String userName) {
        if (net == null) {
            synchronized (ClientNet.class) {
                if (net == null) {
                    net = new ClientNet(fromNet, toNet, ipAddr, portNum, userName);
                }
            }
        }
        return net;
    }

    private void initialServer(Socket leaderSocket, BlockingQueue toNetPutMsg) {
        pool.execute(new ClientNetThread(leaderSocket, toNetPutMsg));
    }


    public void connectToNewPeers() {
        for (PeerHosts peer : peerHosts) {
            int count = 0;
            for(PeerHosts connected : connectedPeerHosts){
//                System.out.println("ClientNet peer: " + peer);
//                System.out.println("ClientNet connected: " + connected);
//                if(peer.getPeerHost().equals(connected.getPeerHost())
//                && peer.getPeerPort().equals(connected.getPeerPort())){
//                    break;
//                }
                if (peer.equals(connected)) {
                    break;
                }
                count++;
            }
            if(count == connectedPeerHosts.size()){
                String addr = peer.getPeerHost();
                int portNum = Integer.parseInt(peer.getPeerPort());
                String newPeerName = peer.getUserName();
                System.out.println("new peer detected, start connection to "+ newPeerName);
                startConnection(newPeerName, addr, portNum);
            }

        }
    }

    private void startConnection(String newPeerName, String Addr, int port) {
        try {
            Socket newPeer = new Socket(Addr, port);
            putPeerNameSocketMap(newPeerName, newPeer);

            System.out.println("connection succ! ");

            connectedPeerHosts.add(new PeerHosts(newPeerName, Addr, Integer.toString(port)));
            connectedPeerSockets.add(newPeer);

            // open new net for new peer
            initialServer(newPeer, toNetPutMsg);

            // login to peer server process  command -- peerLogin
            GuiController.get().loginPeerServer(newPeerName, GuiController.get().getLocalServerPort());

            // send register msg for map clientName and socket to peer Server
            String clientName = GuiController.get().getUsername();
            String hostAddress = GuiController.get().getLocalHostAddress();
            String hostPort = GuiController.get().getLocalServerPort();
            RegisterProtocol registerProtocol = new RegisterProtocol(clientName, hostAddress, hostPort);
            String registerMsg = JSON.toJSONString(registerProtocol);
            pool.execute(new ClientNetSendMsg(registerMsg, newPeer));
        } catch (IOException e) {
            System.out.println("peer connection exception");
        }
    }


    public void shutdown() {
        flag = false;
    }

    @Override
    public void run() {
        Socket socket = null;
        try {
            if (leaderSocket == null) {
                socket = new Socket(ipAddr, portNum);

                connectedPeerSockets.add(socket);

                String userName = GuiController.get().getUsername();
                connectedPeerHosts.add(new PeerHosts("leader", ipAddr, Integer.toString(portNum)));


                if (GuiController.get().isLeader()) {
                    leaderSocket = socket;
                }

            } else {
                socket = leaderSocket;
            }

            String localServerPort = GuiController.get().getLocalServerPort();
            GuiController.get().loginGame(localServerPort);
            threadForSocket = new ThreadFactoryBuilder()
                    .setNameFormat("Net-pool-%d").build();
            pool = new ThreadPoolExecutor(20, 100, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(1024), threadForSocket, new ThreadPoolExecutor.AbortPolicy());
            pool.execute(ClientNetGetMsg.getInstance(fromCenter, socket));
            pool.execute(new ClientNetPutMsg(toCenter, toNetPutMsg));
            initialServer(socket, toNetPutMsg);
        } catch (Exception e) {
            System.out.println("I am ClientNet, Help me! Please re-input!");
            net = null;
            LoginWindow.get().closeWindow();
            LoginWindow.get().reInitial();
        }
    }
}
