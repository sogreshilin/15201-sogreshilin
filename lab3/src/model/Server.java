package model;

import model.message.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Alexander on 29/05/2017.
 */
public class Server {
    private static AtomicInteger sessionIDGenerator = new AtomicInteger(0);
    private static final int BUFFER_CAPACITY = 10;
    private static final int QUEUE_CAPACITY = 100000;
    private static final short OS_PORT = 5000;
    private static final short XML_PORT = 5001;

    private ServerSocket ooServerSocket;
    private ServerSocket xmlServerSocket;
    private List<String> users;
    private BlockingQueue<DisplayMessage> messageBuffer;
    private BlockingQueue<ServerMessage> serverMessages;
    private List<ClientHandler> clientHandlers;

    private Thread objectStreamAcceptor;
    private Thread xmlAcceptor;
    private Thread sender;
    private final Object lock = new Object();

    private static final Logger log = LogManager.getLogger(Server.class);

    public Server() {
        try {
            ooServerSocket = new ServerSocket(OS_PORT);
            xmlServerSocket = new ServerSocket(XML_PORT);
        } catch (IOException e) {
            log.error("server socket creating error");
            System.exit(1);
        }
        messageBuffer = new ArrayBlockingQueue<>(BUFFER_CAPACITY);
        serverMessages = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        clientHandlers = new ArrayList<>();
        users = new ArrayList<>();
//        messageHandler = new ServerMessageHandler(this);
        log.info("server sockets created on ports : {}, {}", OS_PORT, XML_PORT);
    }

    public void process(LoginRequest message, ClientHandler handler) {
        String name = message.getName();
        if (this.getUsers().contains(name)) {
            LoginError response = new LoginError();
            response.setError("User name is already in use");
            handler.addOutgoingMessage(response);
            log.info("user name is already in use");
            return;
        }
        handler.setName(name);
        this.getUsers().add(name);
        LoginSuccess response = new LoginSuccess();
        response.setSessionID(handler.getSessionID());
        handler.addOutgoingMessage(response);
        log.info("add new user={} sessionID={}", name, handler.getSessionID());
        UserLoginMessage msg = new UserLoginMessage();
        msg.setName(name);
        this.getServerMessages().add(msg);
    }

    public void process(TextMessage message, ClientHandler handler) {
        if (message.getSessionID() != handler.getSessionID()) {
            TextError msg = new TextError();
            msg.setError("session IDs are not equal");
            handler.addOutgoingMessage(msg);
            log.info("in text message session IDs are not equal");
            return;
        }
        handler.addOutgoingMessage(new TextSuccess());
        log.info("successful text response sent to {}", handler.getName());
        String name = handler.getName();
        String text = message.getText();
        UserMessage messageToSend = new UserMessage();
        messageToSend.setName(name);
        messageToSend.setMessage(text);
        this.getServerMessages().add(messageToSend);
        log.info("text message \"{}\" sent to everyone", text);
    }

    public void process(ListUsersRequest message, ClientHandler handler) {
        if (message.getSessionID() != handler.getSessionID()) {
            ListUsersError msg = new ListUsersError();
            msg.setError("session IDs are not equal");
            handler.addOutgoingMessage(msg);
            log.info("in list users request session IDs are not equal");
            return;
        }
        ListUsersSuccess msg = new ListUsersSuccess();
        msg.setUsers(this.getUsers());
        handler.addOutgoingMessage(msg);
        log.info("sent list users {} to {}", this.getUsers(), handler.getName());
    }

    public void process(LogoutRequest message, ClientHandler handler) {
        if (message.getSessionID() != handler.getSessionID()) {
            LogoutError msg = new LogoutError();
            msg.setError("session IDs are not equal");
            handler.addOutgoingMessage(msg);
            log.info("in logout request session IDs are not equal");
            return;
        }
        handler.addOutgoingMessage(new LogoutSuccess());
        log.info("successful logout response sent to user {}", handler.getName());
        this.getUsers().remove(handler.getName());
        UserLogoutMessage msg = new UserLogoutMessage();
        msg.setName(handler.getName());
        this.getServerMessages().add(msg);
        handler.stop();
        this.removeClientHandler(handler);
        log.info("sent user logout message to everyone");
    }

    public List<String> getUsers() {
        return users;
    }

    public void removeClientHandler(ClientHandler handler) {
        clientHandlers.remove(handler);
    }

    public BlockingQueue<ServerMessage> getServerMessages() {
        return serverMessages;
    }

    private void start() {
        objectStreamAcceptor = new Thread(new ObjectStreamAcceptor(), "ObjectStreamAcceptor");
        objectStreamAcceptor.start();

        xmlAcceptor = new Thread(new XMLAcceptor(), "XMLAcceptor");
        xmlAcceptor.start();

        sender = new Thread(new Sender(), "Sender");
        sender.start();
    }

    private void stop() {
        try {
            ooServerSocket.close();
            xmlServerSocket.close();
            sender.interrupt();
            for (ClientHandler h : clientHandlers) {
                h.stop();
            }
            objectStreamAcceptor.join();
            xmlAcceptor.join();
            sender.join();
        } catch (IOException e) {
            log.error("error closing socket");

        } catch (InterruptedException e) {
            log.info("interrupted");
        }
    }

    public static void main(String[] args) {
        log.info("to stop server write \"exit\"");
        Server server = new Server();
        server.start();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while (br.readLine().compareToIgnoreCase("exit") != 0);
        } catch (IOException e) {
            log.info("io exception", e);
        }
        server.stop();
    }


    private class XMLAcceptor implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Socket socket = xmlServerSocket.accept();
                    XMLClientHandler handler = new XMLClientHandler(Server.this, socket);
                    handler.start();
                    synchronized (lock) {
                        clientHandlers.add(handler);
                    }
                    log.info("new client accepted on port {}", XML_PORT);
                }
            } catch (IOException e) {
                log.info("stopped");
            }
        }
    }


    private class ObjectStreamAcceptor implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Socket socket = ooServerSocket.accept();
                    ObjectStreamClientHandler handler = new ObjectStreamClientHandler(Server.this, socket);
                    handler.start();
                    synchronized (lock) {
                        clientHandlers.add(handler);
                    }
                    log.info("new client accepted on port {}", OS_PORT);
                }
            } catch (IOException e) {
                log.info("stopped");
            }
        }
    }

    private class Sender implements Runnable {
        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    ServerMessage m = serverMessages.take();
                    synchronized (lock) {
                        for (ClientHandler h : clientHandlers) {
                            h.addOutgoingMessage(m);
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.info("interrupted");
            }
        }
    }

}
