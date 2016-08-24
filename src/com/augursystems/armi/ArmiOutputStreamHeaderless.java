/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * This class is used by Packet.writeExternal(); you don't normally need to use
 * this class directly.
 * This overrides ObjectOutputStream to remove the use of stream headers,
 * since the stream is left open, and contiguous transmissions need to be header-less.
 *
 * @author  Chris.Janicki@AugurSystems.com
 */
public class ArmiOutputStreamHeaderless extends ArmiOutputStream
{


	public ArmiOutputStreamHeaderless() throws IOException, SecurityException
	{
	}


	public ArmiOutputStreamHeaderless(OutputStream out) throws IOException
	{
		super(out);
	}


	/** No headers since stream is left open for multiple transmissions. */
	@Override	final protected void writeStreamHeader() throws IOException
	{
	}


}
