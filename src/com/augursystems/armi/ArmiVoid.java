/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.augursystems.armi;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 *
 * @author Chris.Janicki@AugurSystems.com
 * Copyright 2012 Augur Systems, Inc.  All rights reserved.
 */
public class ArmiVoid extends Object implements java.io.Serializable
{
	public static final ArmiVoid VOID = new ArmiVoid();

	/**
	 * Implements Externalizable; reads/writes nothing.
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{
	}


	/**
	 * Implements Externalizable; reads/writes nothing.
	 */
	public void readExternal(ObjectInput in)  throws IOException, ClassNotFoundException
	{
	}

}
