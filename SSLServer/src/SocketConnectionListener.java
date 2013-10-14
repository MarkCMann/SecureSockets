

public interface SocketConnectionListener {

	/**
	 * @param conn The connection
	 */
	public void socketStartedListening(SocketConnection conn);
	public void socketReceivedObject(Object obj);
	public void socketStoppedListening(SocketConnection conn);
	
}
