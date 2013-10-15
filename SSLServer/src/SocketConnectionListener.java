
public interface SocketConnectionListener {

	/**
	 * @param conn The connection that just began listening.
	 */
	public void socketStartedListening(final SocketConnection conn);
	
	/**
	 * @param obj The object that the connection received.
	 */
	public void socketReceivedObject(final Object obj);
	
	/**
	 * @param conn The connection that stopped listening.
	 */
	public void socketStoppedListening(final SocketConnection conn);
	
}
