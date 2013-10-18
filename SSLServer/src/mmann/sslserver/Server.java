/*
 * A server class that implements SSL socket security.
 */

package mmann.sslserver;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * This class will create a server socket and then begin listening for connections.
 * @author Mark
 * @version 1
 */
public class Server implements SocketConnectionListener {
	
	/**
	 * A flag for indicating whether or not this server is listening on its
	 * port.
	 */
	private volatile boolean isListening;
	
	/**
	 * The server socket factory to create the server sockets from.
	 */
	private final ServerSocketFactory factory;
	
	/**
	 * A list of all currently open connections.
	 */
	private List<SocketConnection> clientConnections;
	
	/**
	 * The thread that the server will use to listen for connections.
	 */
	private Thread serverThread;
	
	/**
	 * The server socket that the server listens with.
	 */
	private ServerSocket serverSocket;
	
	/**
	 * The maximum amount of time that the server thread an live without being
	 * joined.
	 */
	private final static int MAX_SERVER_JOIN_TIME = 1000;
	
	/**
	 * The maximum number of open connections that the server allows.
	 */
	private final int backlog;
	
	/**
	 * The default number of open connections that the server allows.
	 */
	private static final int DEFAULT_BACKLOG = 10;
	
	/**
	 * The port that the server listens on.
	 */
	private final int port;
	
	/**
	 * The default port that the server listens on.
	 */
	public static final int DEFAULT_PORT = 4444;
	
	/**
	 * The server uses the listener to forward information that is received
	 * by its connections.
	 */
	private SocketConnectionListener listener;
	
	/**
	 * Constructs a new SSL secured sever socket and then begins accepting
	 * connections.
	 * @param listener The listener for the sockets.
	 * @param context The context with which to construct the SSLServerSocket.
	 * @param backlog The maximum number of open connections that this server
	 * will allow.
	 */
	public Server(final SocketConnectionListener listener, final SSLContext context, final int backlog) {
		this.factory = context.getServerSocketFactory();
		this.isListening = false;
		this.serverThread = null;
		this.backlog = backlog > 0 ? backlog : DEFAULT_BACKLOG;
		this.port = DEFAULT_PORT;
		this.clientConnections = new ArrayList<SocketConnection>(this.backlog);
		this.listener = listener;
	}
	
	/**
	 * Prints out all supported and default ciphers.
	 */
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
	
	/**
	 * Begin accepting connections on the server socket in a background thread.
	 */
	public void beginListening() {
		if (isListening || serverThread != null) {
			System.err.println("Server.beginListening() called, but server is already listening");
			return;
		}
		isListening = true;
		serverThread = new Thread() {
			public void run() {
				Server.this.listen();
			}
		};
		serverThread.start();
	}
	
	/**
	 * Closes the underlying server socket and closes all open connections.
	 */
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
	
	/**
	 * The listens for outside connections.
	 */
	public void listen() {
		try {
			serverSocket = this.factory.createServerSocket(port, backlog);
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
				if (clientConnections.size() >= backlog) {
					connection.writeObject("Too many connections currently");
				}
				clientConnections.add(connection);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	/**
	 * Forwards the message along to the listener registered with the server.
	 * @param conn The socket that started listening.
	 */
	public void socketStartedListening(final SocketConnection conn) {
		assert(this.clientConnections.contains(conn));
		this.listener.socketStartedListening(conn);
	}

	@Override
	/**
	 * Forwards the received object along to the listener registered with the server.
	 * @param The object that was read.
	 */
	public void socketReceivedObject(final Object obj) {
		this.listener.socketReceivedObject(obj);
	}

	@Override
	/**
	 * Forwards the stop message to the registered listener. In addition the server will
	 * remove the connection from its list of open connections.
	 */
	public void socketStoppedListening(final SocketConnection conn) {
		assert(this.clientConnections.contains(conn));
		this.clientConnections.remove(conn);
		this.listener.socketStoppedListening(conn);
	}
	
	public static void main(final String[] args) {
		String userHome = System.getProperty("user.home");
		String pathToKeystore = userHome + File.separatorChar + "Java Keystores" + File.separatorChar + "server.jks";
		System.setProperty("javax.net.ssl.keyStore", pathToKeystore);
		System.setProperty("javax.net.ssl.keyStoreType", "jks");
		System.setProperty("javax.net.ssl.keyStorePassword", "password");
		
		Server server = null;
		try {
			server = new Server(new DebugSocketListener(), SSLContext.getDefault(), 10);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		try {
			server.beginListening();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
