/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;

/**
 * @author Chris.Janicki@AugurSystems.com
 */
final class SubscriberLocal extends Subscriber
{
	/** The Client object to receive subscribed packets; local to this JVM. */
	transient Client client=null;


	SubscriberLocal()
	{
		super();
	}

	/**
	 * This constructor used by the client, and sent to the server.
	 * @param type
	 * @param flavor
	 * @param filter The optionally null Filter
	 */
	SubscriberLocal(String type, String flavor, Filter filter, Client client)
	{
		super(type, flavor, filter);
		this.client = client;
	}
	

	@Override void transmit(Packet p)
	{
		client.handlePacket(p);
	}


	@Override void close(String reason) 
	{ 
		Armi.log("Subscription ("+this+") for local client ("+client+") closed: "+reason);
		client.abort(reason);
	}

}
