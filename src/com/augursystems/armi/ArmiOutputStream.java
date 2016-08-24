/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * This class is used by Packet.writeExternal(); you don't normally need to use
 * this class directly.  Enables writing of null String objects.
 *
 * @author  Chris.Janicki@AugurSystems.com
 */
public class ArmiOutputStream extends ObjectOutputStream
{


	public ArmiOutputStream() throws IOException, SecurityException
	{
	}


	public ArmiOutputStream(OutputStream out) throws IOException
	{
		super(out);
	}


	/**
	 * Overridden to support 'null' values;
	 * @throws IOException
	 */
	@Override	public final void writeUTF(String s) throws IOException
	{
		if (s == null)
		{
			writeBoolean(true);
		}
		else
		{
			writeBoolean(false);
			super.writeUTF(s);
		}
	}

//	/** No headers since stream is left open for multiple transmissions. */
//	@Override	protected void writeStreamHeader() throws IOException
//	{
//		System.out.println(Thread.currentThread().getName()+"> Going to write stream header...");
//		super.writeStreamHeader();
//		System.out.println(Thread.currentThread().getName()+"> Done writing stream header...");
//	}


}
