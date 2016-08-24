/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;

/**
 * This is your receipt for your subscription; hold on to it; you'll need it if
 * you wish to cancel later.  This is returned when you call Armi.subscribe().
 *
 * @author Chris.Janicki@AugurSystems.com
 * Copyright 2010 Augur Systems, Inc.  All rights reserved.
 */
public final class Subscription
{

	final private SubscriberLocal lsub;
	final private SubscriberRemote rsub;
	final private ArmiRemote remote;
	final private Armi armi;

	/**
	 *
	 * @param local  The local subscriber object.
	 * @param remote The remote subscriber object; may be null if no remote ARMI server was involved.
	 * @param armi  The Armi instance of the subscription.
	 */
	Subscription(Armi armi, SubscriberLocal lsub, ArmiRemote remote, SubscriberRemote rsub)
	{
		this.lsub = lsub;
		this.rsub = rsub;
		this.remote = remote;
		this.armi = armi;
	}


	/**
	 * Cancel this subscription.
	 * @throws ArmiException if this subscription was to a remote ARMI server,
	 * and there was a problem communicating your cancellation to the remote.
	 */
	public final void cancel() throws ArmiException
	{
		if (lsub != null) armi.unsubscribe(lsub);
		if (rsub != null) 
		{ 
			rsub.unsubscribe();
			try
			{
				if (Armi.debug) { System.out.println("Transmitting subscription cancelation to remote server..."); }
				remote.transmit(rsub);
				if (Armi.debug) { System.out.println("Done transmitting."); }
			}
			catch(Exception e) { throw new ArmiException("Problem cancelling subscription on remote server.", e); }
		}
	}

	
	@Override public String toString()
	{
		return getClass().getSimpleName() + " for type=" + lsub.type+", flavor="+lsub.flavor;
	}
}
