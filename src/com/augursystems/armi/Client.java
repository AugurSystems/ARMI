/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;

/**
 * This interface is implemented by ARMI clients that want to receive
 * asynchronous data.
 *
 * @author Chris.Janicki@AugurSystems.com
 */


public interface Client
{
	/**
	 * Called when a subscribed Packet arrives for this Client.  Usually, you
	 * will call Packet.decodeInstance() to get the Serializable object wrapped
	 * by the Packet.  Alternatively, you might call Packet.getData() if the
	 * Packet contains a raw byte[], instead of an object.
	 *<p>
	 * If you have multiple subscriptions (or expect multiple flavors),
	 * you will have to interrogate the Packet via its getDataType() and
	 * getDataFlavor() methods.
	 * 
	 * @param p  A Packet that matched a subscription of yours.
	 */
	public void handlePacket(Packet p);
	
	
	/**
	 * Called when there's a problem, for example, when the connection to the 
	 * remote server dies before we receive the expected call response.
	 * 
	 * @param reason The reason to document why; must be non-null else abort ignored.
	 */
	public void abort(String reason);
}
