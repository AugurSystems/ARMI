/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

/**
 * This Serializable carries the response to a synchronous method call, to be delivered
 * back to the calling client.  This wrapper is necessary since the calls result
 * is actually a subscription packet, and the exact return type isn't exactly known
 * (e.g. could be a subclass of the declared return value), so this wrapper is a
 * reliable subscription type for the system.  The end-user won't see this since
 * the underlying response object is returned by ServerLocal.call().
 * 
 * @author Chris.Janicki@AugurSystems.com
 */
class SynchronousResponse extends Object implements Externalizable
{
	public static final long serialVersionUID = 2763768130499626030L;
	
	/** The Serializable response from the method call */
	Serializable response;
	
	/** 
	 * The unique-within-calling-server serial number,
	 * copied from the associated SynchronousCall.
	 */
	int serial;


	public SynchronousResponse()
	{
		super();
	}

	SynchronousResponse(int serial, Serializable response)
	{
		this.serial = serial;
		this.response = response;
	}


	/**
	 * @serialData Just the 'response' Serializable is written.
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeInt(serial);
		out.writeObject(response);
	}


	public void readExternal(ObjectInput in)  throws IOException, ClassNotFoundException
	{
		serial = in.readInt();
		response = (Serializable)in.readObject();
	}


	@Override public String toString()
	{
		return "serial# "+serial+" = "+response.toString();
	}


}
