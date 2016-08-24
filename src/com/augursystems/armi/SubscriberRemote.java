/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.IOException;

/**
 * @author Chris.Janicki@AugurSystems.com
 */
final class SubscriberRemote extends Subscriber
{
	/**
	 * Assigned by the remote server after unmarshalling this from the stream;
	 * the assigned output stream writes back to that originating stream.
	 */
	transient ArmiRemote remoteServer; // set by server via setOutputStream()
	transient String clientName; // set by server via setOutputStream()
	
	public SubscriberRemote()
	{
		super();
	}

	/**
	 * This constructor used by the client, and sent to the server.
	 * @param type
	 * @param flavor
	 * @param filter The optionally null Filter; must implement Serializable in order
	 * to be sent to remote Server.
	 */
	SubscriberRemote(String type, String flavor, Filter filter)
	{
		super(type, flavor, filter);
	}

	void setOutputStream(ArmiRemote remoteServer,String clientName)
	{
		this.remoteServer = remoteServer;
		this.clientName = clientName;
	}


	/**
	 * Check the packet against the filter, then passes the packet to the
	 * ArmiRemote for sending.
	 * @param p  The Packet to be sent.
	 * @throws IOException  If the ArmiRemote had an I/O problem sending the packet.
	 */
	@Override final void transmit(Packet p) throws IOException
	{
		remoteServer.transmit(p);
	}

	
	@Override final void close(String reason)
	{
		Armi.log("Remote subscription ("+this+") closed: "+reason);
		remoteServer = null; // help GC
	}


	@Override	public String toString()
	{
		if (clientName == null) { return super.toString(); }
		else { return super.toString() + " -> " +clientName; }
	}


	@Override	public int hashCode()
	{
		int hash = 3;
	// Don't include because may be null after close()		hash = 67 * hash + (this.remoteServer != null ? this.remoteServer.hashCode() : 0);
		hash = 67 * hash + (this.clientName != null ? this.clientName.hashCode() : 0);
		return super.hashCode() + hash;
	}


	@Override public boolean equals(Object obj)
	{
		if (super.equals(obj) && obj instanceof SubscriberRemote)
		{
			SubscriberRemote rs = (SubscriberRemote)obj;
			return remoteServer == rs.remoteServer && clientName.equals(clientName);
		}
		return false;
	}

}
