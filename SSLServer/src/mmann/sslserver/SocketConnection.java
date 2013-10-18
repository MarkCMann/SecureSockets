package mmann.sslserver;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * A class that encapsulates basic SSL socket functionality.
 * @author Mark
 * @version 1
 */
public final class SocketConnection {
	
	/**
	 * The underlying socket for the SSL socket connection.
	 */
	private Socket connection;
	
	/**
	 * The thread that runs in the background to communicate with the socket.
	 */
	private Thread connectionThread;
	
	/**
	 * Indicates whether or not the socket is currently
	 * listening to its input and output streams.
	 */
	private boolean isListening = false, closed = false;
	
	/**
	 * The object output stream that the socket uses to send
	 * objects to the other application.
	 */
	private ObjectOutputStream outStream;
	
	/**
	 * The input stream through which the socket receives
	 * objects from the other program.
	 */
	private ObjectInputStream inStream;
	
	/**
	 * A connection listener which is kept updated on the
	 * status of the underlying socket.
	 */
	private SocketConnectionListener listener;
	
	/**
	 * The maximum amount of time that this connection will wait
	 * for the thread to die.
	 */
	private static final int MAX_JOIN_TIME = 1000;
	
	/**
	 * Constructs a new SocketConnection that attempts to connect to the given host.
	 * The underlying socket is constructed using the default SSLContext.
	 * @param host The server's name.
	 * @param port The server port to connect to.
	 */
	public SocketConnection(final String host, final int port) {
		this.connection = initializeAndReturnSocket(new InetSocketAddress(host, port), null);
	}
	
	/**
	 * Constructs a new SocketConnection that attempts to connect to the given host.
	 * The underlying socket is constructed using the default SSLContext.
	 * @param host The server's name.
	 * @param port The server port to connect to.
	 * @param listener The connection listener.
	 */
	public SocketConnection(final String host, final int port, final SocketConnectionListener listener) {
		
	}
	
	/**
	 * Constructs a new SocketConnection that attempts to connect to the given host.
	 * @param host The server's name.
	 * @param port The server port to connect to.
	 * @param context The secure socket context to construct a new SSLSocket from.
	 */
	public SocketConnection(final String host, final int port, final SSLContext context) {
		this.connection = initializeAndReturnSocket(new InetSocketAddress(host, port), context);
	}
	
	/**
	 * Constructs a new SocketConnection that attempts to connect to the given host.
	 * @param host The server's name.
	 * @param port The server port number to connect to.
	 * @param context The secure socket context to construct a new SSLSocket from.
	 * @param listener The connection listener.
	 */
	public SocketConnection(final String host, final int port, final SSLContext context, final SocketConnectionListener listener) {
		this.connection = initializeAndReturnSocket(new InetSocketAddress(host, port), context);
		this.listener = listener;
	}
	
	/**
	 * Construct a new connection from the given socket and listener.
	 * @param socket A socket that is already connected to 
	 * its end point. This socket need not be an SSLSocket.
	 * @param listener The connection listener.
	 */
	public SocketConnection(Socket socket, SocketConnectionListener listener) {
		this.listener = listener;
		this.connection = socket;
		try {
			socket.setKeepAlive(true);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		this.isListening = false;
	}
	
	/**
	 * Replaces the old connection listener with the given listener.
	 * @param listener The new connection listener to send events to.
	 */
	public void setConnectionListener(final SocketConnectionListener listener) {
		this.listener = listener;
	}
	
	/**
	 * Creates and returns an SSLSocket that is bound and connected to the given socket address.
	 * @param socketAddress The address to try to connect to.
	 * @param context The secure socket layer context to use when creating the socket.
	 * @return A socket that is connected to the {@code socketAddress} that uses the given {@code context}.
	 */
	private static Socket initializeAndReturnSocket(InetSocketAddress socketAddress, SSLContext context) {
		Socket socket = null;
		try {
			if (context == null) {
				socket = SSLSocketFactory.getDefault().createSocket();
			} else {
				socket = context.getSocketFactory().createSocket();
			}
			socket.connect(socketAddress);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return socket;
	}

	/**
	 * Continuously listens until we stop listening on the socket.
	 */
	private void listen() {
		while (this.isListening) {
			readObject();
		}
	}
	
	/**
	 * Constructs the necessary streams for communication. If the streams 
	 * cannot be setup the connection will be closed.
	 */
	private synchronized void setupStreams() {
		try {
			// We must initialize the output stream first since the input stream blocks until it receives a serialized header from the associated output stream.
			outStream = new ObjectOutputStream(connection.getOutputStream());
			outStream.flush();
			inStream = new ObjectInputStream(connection.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
			this.closeConnections();
		}
	}
	
	/**
	 * The connection will construct and begin listening to 
	 * the input and output streams.
	 */
	public synchronized void beginListening() {
		if (this.isListening || this.closed) {
			// We do not want to overwrite anything that we are doing.
			// In the case that we are an invalid socket, we don't want to do anything.
			return;
		}
		this.isListening = true;
		
		connectionThread = new Thread() {
			public void run() {
				SocketConnection.this.listen();
			}
		};
		connectionThread.start();
		this.setupStreams();
		
		if (listener != null) {
			listener.socketStartedListening(this);
		}
	}
	
	/**
	 * Writes an object to the socket's output stream and then flushes 
	 * the stream. If the connection is unable to send the information
	 * the connection is closed.
	 * @param obj The object to write to the output stream.
	 */
	public final void writeObject(Object obj) {
		if (outStream == null || this.closed) {
			return;
		}
		try {
			outStream.writeObject(obj);
			outStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
			this.closeConnections();
		}
	}
	
	/**
	 * Closes all open streams and the underlying socket associated with
	 * this SocketConnection object.
	 */
	public void stopListening() {
		this.closeConnections();
	}
	
	/**
	 * Determines whether or not the connection is closed.
	 * @return true if the connection is either already 
	 * closed or in the process of closing.
	 */
	public boolean isClosed() {
		return closed;
	}
	
	/**
	 * Reads an object from the input stream and then calls the
	 * listeners socketReceivedObject method.
	 */
	private void readObject() {
		if (inStream == null || this.closed) {
			return;
		}
		try {
			if (listener != null) {
				listener.socketReceivedObject(inStream.readObject());
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// The IO exceptions are thrown when the underlying stream is bad, when the socket is closed on either end.
			this.closeConnections();
		} catch (Exception e) {
			System.err.println("We shall hopefully never reach this statement");
			e.printStackTrace();
		}
	}
	
	/**
	 * Closes all connections, and then notifies the listener that this socket is closed.
	 */
	private synchronized void closeConnections() {
		if (this.closed) {
			// We are already closing our connection!
			return;
		}
		closed = true;
		closeOutputStream();
		closeInputStream();
		closeSocket();
		isListening = false;
		joinConnectionThread();
		if (listener != null) {
			listener.socketStoppedListening(this);
		}
	}
	
	/**
	 * Closes the input stream associated with this connection.
	 */
	private void closeInputStream() {
		while (inStream != null) {
			try {
				inStream.close();
				inStream = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Closes the output stream associated with this connection.
	 */
	private void closeOutputStream() {
		while (outStream != null) {
			try {
				outStream.close();
				outStream = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Closes the socket associated with this connection.
	 */
	private void closeSocket() {
		while (connection != null) {
			try {
				connection.close();
				connection = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Joins the background thread.
	 */
	private void joinConnectionThread() {
		while (connectionThread != null) {
			try {
				this.connectionThread.join(MAX_JOIN_TIME);
				this.connectionThread = null;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
