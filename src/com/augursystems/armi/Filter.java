/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */

package com.augursystems.armi;
import java.io.Serializable;

/**
 * This one-method interface can be implemented to filter asynchronous data
 * packets from being transmitted to the associated Client.
 *
 * @author Chris.Janicki@AugurSystems.com
 */
public interface Filter extends Serializable
{

	public boolean accepts(Packet p);

}
