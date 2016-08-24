/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
//import java.io.StreamCorruptedException;

/**
 * This class is used by Packet.decodeInstance(); you don't normally need to use
 * this class directly.  Enabled reading of null String objects.
 *
 * @author  Chris.Janicki@AugurSystems.com
 */
public class ArmiInputStream extends ObjectInputStream
{


	public ArmiInputStream() throws IOException, SecurityException
	{
		super();
	}


	public ArmiInputStream(InputStream in) throws IOException
	{
		super(in);
	}


	/**
	 * Overridden to support 'null' values;
	 * @return The possibly null String
	 * @throws IOException
	 */
	@Override public String readUTF() throws IOException
	{
		if (readBoolean()) { return null; }
		else
		{
			return super.readUTF();
		}
	}


//	/**
//	 * This overrides ObjectInputStream only to remove the use of stream headers,
//	 * since the stream is left open, and contiguous transmissions need to be header-less.
//	 */
//	@Override	protected void readStreamHeader() throws IOException, StreamCorruptedException
//	{
//		System.out.println(Thread.currentThread().getName()+"> Going to read stream header...");
//		super.readStreamHeader();
//		System.out.println(Thread.currentThread().getName()+"> Done reading stream header...");
//	}


}
