/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;

/**
 * This class is used by Packet.decodeInstance(); you don't normally need to use
 * this class directly.
 * This overrides ObjectInputStream only to remove the use of stream headers,
 * since the stream is left open, and contiguous transmissions need to be header-less.
 *
 * @author  Chris.Janicki@AugurSystems.com
 */
public class ArmiInputStreamHeaderless extends ArmiInputStream
{


	public ArmiInputStreamHeaderless() throws IOException, SecurityException
	{
		super();
	}


	public ArmiInputStreamHeaderless(InputStream in) throws IOException
	{
		super(in);
	}



	/** No headers since stream is left open for multiple transmissions. */
	@Override final protected void readStreamHeader() throws IOException, StreamCorruptedException
	{
		//System.out.println("ArmiInputStream> Called ArmiInputStream.readStreamHeader()");
	}



}
