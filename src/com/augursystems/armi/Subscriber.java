/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 *
 * @author Chris.Janicki@AugurSystems.com
 */
abstract class Subscriber extends Object implements Externalizable
{
	/**
	 * An optionally null Filter to add another layer of selectivity to the subscription.
	 */
	protected Filter filter;

	/**
	 * The ArmiString type (usually a Java class) and flavor (sub-category) this
	 * subscriber would like; flavor or both may be null.
	 */
	protected String type, flavor;

	/**
	 * Indicates if this is an attempt to subscribe or cancel
	 */
	protected boolean subscribe;


	public Subscriber()
	{
		super();
	}


	Subscriber(String type, String flavor, Filter filter)
	{
		this.type = type;
		this.flavor = flavor;
		this.filter = filter;
		this.subscribe = true;
	}

	
	/**
	 * Applies the filter.
	 * @param p The Packet
	 * @return A boolean, TRUE if the packet is accepted by the filter, or if the 
	 * filter is null.
	 */
	boolean accepts(Packet p)
	{
		return (filter == null) || (filter.accepts(p));
	}
	

	void unsubscribe()
	{
		this.subscribe = false;
	}


	/**
	 * Sends the matching Packet to the subscriber.
	 *
	 * @param p  The matching Packet.
	 * 
	 * @throws java.io.IOException if bad things happen on the way; more likely
	 * for RemoteSubscriber subclass.
	 */
	abstract void transmit(Packet p) throws IOException;


	/**
	 * Close down this subscriber; cannot be reopened.
	 */
	abstract void close(String reason);


	/**
	 * @serialData Writes 1) the UTF String 'type'; 2) the UTF String 'flavor';
	 * 3) the boolean 'subscribe'; 4) the 'filter' object.
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeUTF(type);
		out.writeUTF(flavor);
		out.writeBoolean(subscribe);
		out.writeObject(filter);
	}


	public void readExternal(ObjectInput in)  throws IOException, ClassNotFoundException
	{
		type = in.readUTF();
		flavor = in.readUTF();
		subscribe = in.readBoolean();
		filter = (Filter)in.readObject();
	}


	@Override public boolean equals(Object obj)
	{
		if (obj instanceof Subscriber)
		{
			Subscriber s = (Subscriber)obj;
			return
			(
				(
					(type==null && s.type==null) ||
					(type!=null && s.type!=null && type.equals(s.type))
				)
					&&
				(
					(flavor==null && s.flavor==null) ||
					(flavor!=null && s.flavor!=null && flavor.equals(s.flavor))
				)
			);
		}
		return false;
	}


	@Override public int hashCode()
	{
		int hash = 5;
		hash = 89 * hash + (this.type != null ? this.type.hashCode() : 0);
		hash = 89 * hash + (this.flavor != null ? this.flavor.hashCode() : 0);
		return hash;
	}


	/**
	 * @return A String containing the subscribed type and flavor (if set);
	 * also indicates if this is actually an un-subscription;
	 * also indicates if there is a filter set.
	 */
	@Override public String toString()
	{
		StringBuilder sb = new StringBuilder(20);
		if (!subscribe) { sb.append("(un-subscribe) "); }
		sb.append(type);
		if (flavor!=null) { sb.append("/").append(flavor); }
		if (filter!=null) { sb.append("[+filter]"); }
		return sb.toString();
	}
}
