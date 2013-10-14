

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

public class Server implements SocketConnectionListener {
	
	private volatile boolean isListening;
	
	private ServerSocketFactory factory;
	
	private ArrayList<SocketConnection> clientConnections;
	
	private Thread serverThread;
	private ServerSocket serverSocket;
	
	private static final int BACKLOG = 5, MAX_SERVER_JOIN_TIME = 1000;
	public static final int PORT_NUMBER = 4444;
	
	private SocketConnectionListener listener;
	
	public Server(SocketConnectionListener listener) {
		this.factory = SSLServerSocketFactory.getDefault();
		this.isListening = false;
		this.serverThread = null;
		this.clientConnections = new ArrayList<SocketConnection>(10);
		this.listener = listener;
	}
	
	public void printCipherSuites() {
		System.out.println("Supported ciphers: ");
		for (String str : ((SSLServerSocketFactory)factory).getSupportedCipherSuites()) {
			System.out.println(str);
		}
		System.out.println("Default ciphers: ");
		for (String str : ((SSLServerSocketFactory)factory).getDefaultCipherSuites()) {
			System.out.println(str);
		}
	}
	
	public void beginListening() {
		if (isListening || serverThread != null) {
			System.out.println("Server.beginListening() called, but server is already listening");
			return;
		}
		isListening = true;
		serverThread = new Thread() {
			public void run() {
				Server.this.run();
			}
		};
		serverThread.start();
	}
	
	public void stopListening() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (SocketConnection connection : this.clientConnections) {
			connection.stopListening();
		}
		while (serverThread != null) {
			try {
				serverThread.join(MAX_SERVER_JOIN_TIME);
				serverThread = null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		this.isListening = false;
	}
	
	public void run() {
		try {
			serverSocket = this.factory.createServerSocket(PORT_NUMBER, BACKLOG);
		} catch (IOException e1) {
			e1.printStackTrace();
			System.out.println("Could not open up server socket.");
			this.isListening = false;
			return;
		}
		while (this.isListening && !serverSocket.isClosed()) {
			try {
				Socket clientConnection = serverSocket.accept();
				SocketConnection connection = new SocketConnection(clientConnection, this);
				connection.beginListening();
				if (clientConnections.size() >= BACKLOG) {
					connection.writeObject("Too many connections currently");
				}
				clientConnections.add(connection);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		String userHome = System.getProperty("user.home");
		String pathToKeystore = userHome + File.separatorChar + "Java Keystores" + File.separatorChar + "server.jks";
		System.setProperty("javax.net.ssl.keyStore", pathToKeystore);
		System.setProperty("javax.net.ssl.keyStoreType", "jks");
		System.setProperty("javax.net.ssl.keyStorePassword", "password");
		
		Server server = new Server(new DebugSocketListener());
		server.beginListening();
	}

	@Override
	public void socketStartedListening(SocketConnection conn) {
		assert(this.clientConnections.contains(conn));
		this.listener.socketStartedListening(conn);
	}

	@Override
	public void socketReceivedObject(Object obj) {
		this.listener.socketReceivedObject(obj);
	}

	@Override
	public void socketStoppedListening(SocketConnection conn) {
		assert(this.clientConnections.contains(conn));
		this.clientConnections.remove(conn);
		this.listener.socketStoppedListening(conn);
	}
}
