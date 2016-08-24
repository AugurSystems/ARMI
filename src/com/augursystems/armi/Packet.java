/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

/**
 * This is the basic wrapper for all messages sent via ARMI.
 * The serialized format of this object is:
 * <ol>
 * <li>dataType (UTF-String)
 * <li>dataLength (4-byte integer)
 * <li>data (variable byte array)
 * </ol>
 * The dataType is is usually a fully-qualified class name, but may be an
 * arbitrary name for passing pure (non-object) data bytes.
 * <p>
 * An optional sub-categorization of the class called "flavor" is supported.
 * Sources and listeners may specify a flavor to differentiate between
 * different uses of the same dataType; optionally null if just plain vanilla.

 * @author Chris.Janicki@AugurSystems.com
 */
public class Packet extends Object implements Externalizable
{

	/**
	 * Represents the type of data being serialized.  Listeners will register based on
	 * the dataType.  Since this usually contains a fully-qualified class name,
	 * an optional "flavor" may be appended after a '/' character.  The flavor
	 * provides a way to differentiate listeners who want the same class of packet
	 * objects, but maybe from different sources.  You could do the same thing by
	 * using a Filter, but the flavor is much more efficient since it is used as
	 * part of the hashCode that quickly maps packet types to subscribers.
	 * <p>
	 * For example, imagine an alarm system that generates "Alarm" objects from
	 * various external sources (e.g. different buildings or data centers).  Since
	 * a common Alarm class would encapsulate each alarm regardless of the source,
	 * the flavor could be used to identify the source.  Subscribers could then
	 * specify a flavor of Alarm in order to efficiently pre-filter packets.
	 */
	protected String dataType;

	/**
	 * Represents an optional sub-categorization of the class being serialized.
	 * Sources and listeners may specify a flavor to differentiate between
	 * different uses of the same dataType; not null but may be ArmiString.NULL
	 */
	protected String dataFlavor;

	
	/**
	 * A byte[] that may contain a serialized object, or just plain data.
	 * May be null until serialized, assuming the 'instance' member is non-null.
	 */
	protected byte[] data;


	protected byte compression = 0;


	/**
	 * The cached Serializable object that may be stored in the data byte[].
	 * This is an optimization for wrapping objects in packets, but isn't
	 * a required by the API.  It is marked "transient" to help note this.
	 */
	protected transient Serializable instance;

	//private transient byte[] serials;


	/** This constructor is only used by the unwrapSerializable() utility method. */
	public Packet() { super(); }
	

	/**
	 * Wraps a generic Serializable in a Packet; useful to wrap a remote return value
	 * of unknown Serializable subtype.
	 *
	 * @param a The Serializable to be wrapped in the packet; if null, will be
	 * replaced by ArmiString.NULL; the dataFlavor is set to null.
	 */
	public Packet(Serializable a)
	{
		this(a,null);
	}

	/**
	 * Wraps a generic Serializable in a Packet; useful to wrap a remote return value
	 * of unknown Serializable subtype.
	 *
	 * @param a The Serializable to be wrapped in the packet; if null, will be
	 * replaced by ArmiString.NULL.
	 * @param dataFlavor The String flavor of the Serializable; optionally null if
	 * just plain vanilla.
	 */
	public Packet(Serializable a, String dataFlavor)
	{
		super();
		this.instance = a;
		this.dataType = (a==null? null : a.getClass().getName());
		this.dataFlavor = dataFlavor;
	}


	/**
	 * Creates a Packet.
	 * If both dataType and dataFlavor are null, you have a very plain packet indeed.
	 * 
	 * @param dataType A String identifier for the data byte[]; usually the
	 * fully-qualified String name of the class stored in the data; may be null,
	 * although only for unusual circumstances.
	 * 
	 * @param dataFlavor An optional String that differentiates the
	 * purpose of this packet for subscriptions; optionally null if just plain vanilla.
	 *
	 * @param data The byte[] containing the your data; usually a serialized
	 * version of an Serializable class, named in the dataType parameter.
	 */
	public Packet(String dataType, String dataFlavor, byte compression, byte[] data)
	{
		if (dataType==null) { dataType = ""; }
		this.dataType = dataType;
		this.dataFlavor = dataFlavor;
		this.compression = compression;
		this.data = data; 
	}


	/** Implements Externalizable.  Note that this is a two-step process; only
	 * the type/flavor and compression fields are initially read; to retrieve
	 * the wrapped data object, you call decodeInstance().

	 */
	public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		instance = null;
		dataType = in.readUTF();
		//System.out.println("Packet.readExternal()> dataType="+dataType);
		dataFlavor = in.readUTF();
		//System.out.println("Packet.readExternal()> dataFlavor="+dataFlavor);
		compression = in.readByte();
		//System.out.println("Packet.readExternal()> compression="+compression);
		int dataLength = in.readInt();
		//System.out.println("Packet.readExternal()> dataLength="+dataLength);
		if (dataLength > 0)
		{
			data = new byte[dataLength];
			in.readFully(data);
		}
	}


	/**
	 * Implements Externalizable.  This Packet wraps a Serializable.  It is
	 * written in two steps: 1) The data type/flavor and compression algorithm
	 * are serialized; 2) the wrapped Serializable is separately serialized and
	 * written as a byte[].  This system allows packets to be ignored and/or
	 * forwarded without the cost of de-serializing the content.
	 *
	 * @serialData Writes 1) the UTF String data type; 2) the UTF String data
	 * flavor; 3) a byte representing the compression algorithm (0=none, default);
	 * 4) the int length of the serialized data object; 5) the byte[] holding the
	 * serialized data object.
	 */
	public final void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeUTF(dataType);
		out.writeUTF(dataFlavor);
		out.writeByte(compression);
		if (data==null && instance!=null)
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ArmiOutputStreamHeaderless oos = new ArmiOutputStreamHeaderless(baos);
			oos.writeObject(instance);
			oos.close();
			data = baos.toByteArray();
		}

		out.writeInt(data==null? 0 : data.length);
		if (data!=null) { out.write(data); }
	}


	/**
	 * @return The String 'flavor' of this packet; possibly null.
	 * See also the documentation of the dataType field.
	 */
	public final String getDataFlavor()
	{
		return dataFlavor;
	}


	/**
	 * @return The String 'type' of this packet; usually the fully qualified
	 * class name of the Serializable object in this Packet's data payload.
	 * See also the documentation of the dataType field.
	 */
	public final String getDataType()
	{
		return dataType;
	}


//	/**
//	 * @return A cached byte[] containing the serialized form; useful for when
//	 * the same serialization will be written to several targets.
//	 */
//	public final byte[] Xserialize() throws IOException
//	{
//		if (serials == null)
//		{
//			ByteArrayOutputStream ba = new ByteArrayOutputStream();
//			ArmiOutputStream out = new ArmiOutputStream(ba);
//			out.writeUTF(dataType);
//			out.writeUTF(dataFlavor);
//			out.writeByte(compression);
//			if (data==null && instance!=null)
//			{
//				ByteArrayOutputStream baos = new ByteArrayOutputStream();
//				ArmiOutputStream oos = new ArmiOutputStream(baos);
//				oos.writeObject(instance);
//				oos.close();
//				data = baos.toByteArray();
//			}
//			out.writeInt(data==null? 0 : data.length);
//			if (data!=null) { out.write(data); }
//			out.close();
//			serials = ba.toByteArray();
//		}
//		return serials;
//	}


	/**
	 * @return The byte[] containing the Packet's data payload (usually a
	 * Serializable that should be decoded via decodeInstance(), but not necessarily...
	 * it could actually be some other binary data passed via the ARMI system.
	 */
	public byte[] getData()
	{
		return data;
	}


	/**
	 * If this packet's data contains a Java object of the class named in the
	 * dataType field, then this method will unmarshall that object;
	 * this object is cached so future calls are not expensive.
	 */
	public Serializable decodeInstance() throws Exception
	{
		if (instance==null && data!=null)
		{
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			ArmiInputStreamHeaderless ois = new ArmiInputStreamHeaderless(bais);
			instance = (Serializable)ois.readObject();
			ois.close();
		}
		return instance;
	}


	/**
	 * @return A String formatted with this fully-qualified class name, followed by
	 * brackets containing the data type and favor contained in this packet, e.g.
	 * "com.augursystems.armi.Packet [com.augursystems.armi.ArmiString/MyFlavor]".
	 * If the type or flavor is null, they will be simply blank.
	 */
	@Override public String toString()
	{
		return getClass().getSimpleName()+
			" ["+
			(dataType==null?"":dataType)+
			"/"+
			(dataFlavor==null?"":dataFlavor)+
			"]";
	}


}
