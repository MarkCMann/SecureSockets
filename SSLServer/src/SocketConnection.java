
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public final class SocketConnection {
	
	private Socket connection;
	private Thread connectionThread;
	private boolean isListening = false, closed = false;
	private ObjectOutputStream outStream;
	private ObjectInputStream inStream;
	
	private SocketConnectionListener socketListener;
	
	private static final int MAX_JOIN_TIME = 1000;
	
	/**
	 * Constructs a new SocketConnection that attempts to connect to 
	 * @param host
	 * @param listener
	 */
	public SocketConnection(String host, SocketConnectionListener listener, final int port) {
		this(new InetSocketAddress(host, port), listener);
	}
	
	/**
	 * 
	 * @param host
	 * @param listener
	 * @param context
	 */
	public SocketConnection(String host, SocketConnectionListener listener, SSLContext context, final int port) {
		this(new InetSocketAddress(host, port), listener, context);
	}
	
	private SocketConnection(InetSocketAddress socketAddress, SocketConnectionListener listener) {
		this(initializeAndReturnSocket(socketAddress, null), listener);
	}
	
	private SocketConnection(InetSocketAddress socketAddress, SocketConnectionListener listener, SSLContext context) {
		this(initializeAndReturnSocket(socketAddress, context), listener);
	}
	
	/**
	 * Construct a new connection from the given socket and listener.
	 * @param socket A socket that is already connected to 
	 * its end point. This socket need not be an SSLSocket.
	 * @param listener The connection listener.
	 */
	public SocketConnection(Socket socket, SocketConnectionListener listener) {
		connectionThread = new Thread() {
			public void run() {
				SocketConnection.this.run();
			}
		};
		this.socketListener = listener;
		this.connection = socket;
		try {
			socket.setKeepAlive(true);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		this.isListening = false;
	}
	
	/**
	 * Creates and returns an SSLSocket that is bound and connected to the given socket address.
	 * @param socketAddress The address to try to connect to.
	 * @param context The secure socket layer context to use when creating the socket.
	 * @return A socket that is connected to the {@code socketAddress} that uses the given {@code context}.
	 */
	private static Socket initializeAndReturnSocket(InetSocketAddress socketAddress, SSLContext context) {
		Socket conn = null;
		try {
			if (context == null) {
				conn = SSLSocketFactory.getDefault().createSocket();
			} else {
				conn = context.getSocketFactory().createSocket();
			}
			conn.connect(socketAddress);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return conn;
	}

	/**
	 * The run loop for the socket connection.
	 */
	private void run() {
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
		try {
			this.setupStreams();
			connectionThread.start();
			socketListener.socketStartedListening(this);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
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
			socketListener.socketReceivedObject(inStream.readObject());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// The IO exceptions are thrown when the underlying stream is bad, when the socket is closed on either end.
			this.closeConnections();
		} catch (Exception e) {
			System.out.println("We shall hopefully never reach this statement");
			e.printStackTrace();
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
	 * Closes all connections and then sets the closed flag.
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
		if (socketListener != null) {
			socketListener.socketStoppedListening(this);
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
