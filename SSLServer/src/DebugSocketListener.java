
public class DebugSocketListener implements SocketConnectionListener {

	private SocketConnection connection;
	
	public DebugSocketListener() {
		// There is nothing to initialize.
	}
	
	@Override
	public void socketStartedListening(SocketConnection conn) {
		this.connection = conn;
		System.out.println(String.format("SocketConnection, %s, began listening", conn));
	}

	@Override
	public void socketReceivedObject(Object obj) {
		System.out.println(obj);
	}

	@Override
	public void socketStoppedListening(SocketConnection conn) {
		assert(connection == conn);
		System.out.println(String.format("SocketConnection, %s, stopped listening", conn));
		this.connection = null;
	}

}
