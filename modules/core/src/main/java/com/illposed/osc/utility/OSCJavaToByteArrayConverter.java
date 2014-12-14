/*
 * Copyright (C) 2003-2014, C. Ramakrishnan / Illposed Software.
 * All rights reserved.
 *
 * This code is licensed under the BSD 3-Clause license.
 * See file LICENSE (or LICENSE.html) for more information.
 */

package com.illposed.osc.utility;

import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCImpulse;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Date;

/**
 * A helper class that translates from Java types to their byte stream representations
 * according to the OSC spec.
 *
 * The implementation is based on
 * <a href="http://www.emergent.de">Markus Gaelli</a> and
 * Iannis Zannos's OSC implementation in Squeak (a Smalltalk dialect).
 *
 * This version includes bug fixes and improvements from
 * Martin Kaltenbrunner and Alex Potsides.
 *
 * @author Chandrasekhar Ramakrishnan
 * @author Martin Kaltenbrunner
 * @author Alex Potsides
 */
public class OSCJavaToByteArrayConverter {

	/**
	 * 2208988800 seconds -- includes 17 leap years
	 */
	public static final long SECONDS_FROM_1900_TO_1970 = 2208988800L;

	/**
	 * baseline NTP time if bit-0=0 is 7-Feb-2036 @ 06:28:16 UTC
	 */
	protected static final long MSB_0_BASE_TIME = 2085978496000L;
	/**
	 * baseline NTP time if bit-0=1 is 1-Jan-1900 @ 01:00:00 UTC
	 */
	protected static final long MSB_1_BASE_TIME = -2208988800000L;

	private final SizeTrackingOutputStream stream;
	/** Used to encode message addresses and string parameters. */
	private Charset charset;
	private final byte[] intBytes;
	private final byte[] longintBytes;

	public OSCJavaToByteArrayConverter(final OutputStream wrappedStream) {

		this.stream = new SizeTrackingOutputStream(wrappedStream);
		this.charset = Charset.defaultCharset();
		this.intBytes = new byte[4];
		this.longintBytes = new byte[8];
	}

	/**
	 * Returns the character set used to encode message addresses
	 * and string parameters.
	 * @return the character-encoding-set used by this converter
	 */
	public Charset getCharset() {
		return charset;
	}

	/**
	 * Sets the character set used to encode message addresses
	 * and string parameters.
	 * @param charset the desired character-encoding-set to be used by this converter
	 */
	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	/**
	 * Align the stream by padding it with '0's so it has a size divisible by 4.
	 */
	private void alignStream() throws IOException {
		final int alignmentOverlap = stream.size() % 4;
		final int padLen = (4 - alignmentOverlap) % 4;
		for (int pci = 0; pci < padLen; pci++) {
			stream.write(0);
		}
	}

	private byte[] convertToByteArray(final OSCPacket packet) throws IOException {

		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		final OSCJavaToByteArrayConverter packetStream = new OSCJavaToByteArrayConverter(buffer);
		packetStream.setCharset(getCharset());
		if (packet instanceof OSCBundle) {
			packetStream.write((OSCBundle) packet);
		} else if (packet instanceof OSCMessage) {
			packetStream.write((OSCMessage) packet);
		} else {
			throw new UnsupportedOperationException("We do not support writing packets of type: "
					+ packet.getClass());
		}
		return buffer.toByteArray();
	}

	private void write(final OSCBundle bundle) throws IOException {
		write("#bundle");
		writeBundleTimestamp(bundle.getTimestamp());
		for (final OSCPacket pkg : bundle.getPackets()) {
			writeInternal(pkg);
		}
	}

	/**
	 * Convert the address into a byte array.
	 * Used internally only.
	 * @param stream where to write the address to
	 */
	private void writeAddressByteArray(final OSCMessage message) throws IOException {
		write(message.getAddress());
	}

	/**
	 * Convert the arguments into a byte array.
	 * Used internally only.
	 * @param stream where to write the arguments to
	 */
	private void writeArgumentsByteArray(final OSCMessage message) throws IOException {
		write(',');
		writeTypes(message.getArguments());
		for (final Object argument : message.getArguments()) {
			write(argument);
		}
	}

	private void write(final OSCMessage message) throws IOException {
		writeAddressByteArray(message);
		writeArgumentsByteArray(message);
	}

	private void writeInternal(final OSCPacket packet) throws IOException {

		// HACK NOTE We have to do it in this ugly way,
		//   because we have to know the packets size in bytes
		//   and write it to the stream,
		//   before we can write the packets content to the stream.
		final byte[] packetBytes = convertToByteArray(packet);
		write(packetBytes); // this first writes the #bytes, before the actual bytes
	}

	public void write(final OSCPacket packet) throws IOException {

		stream.reset();
		if (packet instanceof OSCBundle) {
			write((OSCBundle) packet);
		} else if (packet instanceof OSCMessage) {
			write((OSCMessage) packet);
		} else {
			throw new UnsupportedOperationException("We do not support writing packets of type: "
					+ packet.getClass());
		}
	}

	/**
	 * Write bytes into the byte stream.
	 * @param bytes  bytes to be written
	 */
	void write(final byte[] bytes) throws IOException {
		writeInteger32ToByteArray(bytes.length);
		stream.write(bytes);
		alignStream();
	}

	/**
	 * Write an integer into the byte stream.
	 * @param anInt the integer to be written
	 */
	void write(final int anInt) throws IOException {
		writeInteger32ToByteArray(anInt);
	}

	/**
	 * Write a float into the byte stream.
	 * @param aFloat floating point number to be written
	 */
	void write(final Float aFloat) throws IOException {
		writeInteger32ToByteArray(Float.floatToIntBits(aFloat));
	}

	/**
	 * Write a double into the byte stream (8 bytes).
	 * @param aDouble double precision floating point number to be written
	 */
	void write(final Double aDouble) throws IOException {
		writeInteger64ToByteArray(Double.doubleToRawLongBits(aDouble));
	}

	/**
	 * @param anInt the integer to be written
	 */
	void write(final Integer anInt) throws IOException {
		writeInteger32ToByteArray(anInt);
	}

	/**
	 * @param aLong the double precision integer to be written
	 */
	void write(final Long aLong) throws IOException {
		writeInteger64ToByteArray(aLong);
	}

	/**
	 * @param timestamp the timestamp to be written
	 */
	void write(final Date timestamp) throws IOException {
		writeInteger64ToByteArray(javaToNtpTimeStamp(timestamp.getTime()));
	}

	/**
	 * Convert the time-tag (a Java Date) into the OSC byte stream.
	 * Used Internally.
	 * @param stream where to write the time-tag to
	 */
	private void writeBundleTimestamp(final Date timestamp) throws IOException {
		if ((null == timestamp) || (timestamp.equals(OSCBundle.TIMESTAMP_IMMEDIATE))) {
			write((int) 0);
			write((int) 1);
			return;
		}

		final long millisecs = timestamp.getTime();
		final long secsSince1970 = (long) (millisecs / 1000);
		final long secs = secsSince1970 + SECONDS_FROM_1900_TO_1970;

		// this line was cribbed from jakarta commons-net's NTP TimeStamp code
		final long fraction = ((millisecs % 1000) * 0x100000000L) / 1000;

		write((int) secs);
		write((int) fraction);
	}

	/**
	 * Converts a Java time-stamp to a 64-bit NTP time representation.
	 * This code was copied in from the "Apache Jakarta Commons - Net" library,
	 * which is licensed under the
	 * <a href="http://www.apache.org/licenses/LICENSE-2.0.html">ASF 2.0 license</a>.
	 * The original source file can be found
	 * <a href="http://svn.apache.org/viewvc/commons/proper/net/trunk/src/main/java/org/apache/commons/net/ntp/TimeStamp.java?view=co">here</a>.
	 * @param javaTime Java time-stamp, as returned by {@link Date#getTime()}
	 * @return NTP time-stamp representation of the Java time value.
	 */
	protected static long javaToNtpTimeStamp(long javaTime) {
		final boolean useBase1 = javaTime < MSB_0_BASE_TIME; // time < Feb-2036
		final long baseTime;
		if (useBase1) {
			baseTime = javaTime - MSB_1_BASE_TIME; // dates <= Feb-2036
		} else {
			// if base0 needed for dates >= Feb-2036
			baseTime = javaTime - MSB_0_BASE_TIME;
		}

		long seconds = baseTime / 1000;
		final long fraction = ((baseTime % 1000) * 0x100000000L) / 1000;

		if (useBase1) {
			seconds |= 0x80000000L; // set high-order bit if msb1baseTime 1900 used
		}

		final long ntpTime = seconds << 32 | fraction;

		return ntpTime;
	}

	/**
	 * Write a string into the byte stream.
	 * @param aString the string to be written
	 */
	void write(final String aString) throws IOException {
		final byte[] stringBytes = aString.getBytes(charset);
		stream.write(stringBytes);
		stream.write(0);
		alignStream();
	}

	/**
	 * Write a char into the byte stream, and ensure it is 4 byte aligned again.
	 * @param aChar the character to be written
	 */
	void write(final Character aChar) throws IOException {
		stream.write((char) aChar);
		alignStream();
	}

	/**
	 * Write a char into the byte stream.
	 * CAUTION, this does not ensure 4 byte alignment (it actually breaks it)!
	 * @param aChar the character to be written
	 */
	void write(final char aChar) throws IOException {
		stream.write(aChar);
	}

	/**
	 * Checks whether the given object is represented by a type that comes without data.
	 * @param anObject the object to inspect
	 * @return whether the object to check consists of only its type information
	 */
	private boolean isNoDataObject(Object anObject) {
		return ((anObject instanceof OSCImpulse)
				|| (anObject instanceof Boolean)
				|| (anObject == null));
	}

	/**
	 * Write an object into the byte stream.
	 * @param anObject (usually) one of Float, Double, String, Character, Integer, Long,
	 *   or array of these.
	 */
	void write(Object anObject) throws IOException {
		// Can't do switch on class
		if (anObject instanceof Collection) {
			final Collection<Object> theArray = (Collection<Object>) anObject;
			for (final Object entry : theArray) {
				write(entry);
			}
		} else if (anObject instanceof Float) {
			write((Float) anObject);
		} else if (anObject instanceof Double) {
			write((Double) anObject);
		} else if (anObject instanceof String) {
			write((String) anObject);
		} else if (anObject instanceof byte[]) {
			write((byte[]) anObject);
		} else if (anObject instanceof Character) {
			write((Character) anObject);
		} else if (anObject instanceof Integer) {
			write((Integer) anObject);
		} else if (anObject instanceof Long) {
			write((Long) anObject);
		} else if (anObject instanceof Date) {
			write((Date) anObject);
		} else if (!isNoDataObject(anObject)) {
			throw new UnsupportedOperationException("Do not know how to write an object of class: "
					+ anObject.getClass());
		}
	}

	/**
	 * Write the OSC specification type tag for the type a certain Java type
	 * converts to.
	 * @param typeClass Class of a Java object in the arguments
	 */
	private void writeType(final Class typeClass) throws IOException {

		// A big ol' else-if chain -- what's polymorphism mean, again?
		// I really wish I could extend the base classes!
		if (Integer.class.equals(typeClass)) {
			stream.write('i');
		} else if (Long.class.equals(typeClass)) {
			stream.write('h');
		} else if (Date.class.equals(typeClass)) {
			stream.write('t');
		} else if (Float.class.equals(typeClass)) {
			stream.write('f');
		} else if (Double.class.equals(typeClass)) {
			stream.write('d');
		} else if (String.class.equals(typeClass)) {
			stream.write('s');
		} else if (byte[].class.equals(typeClass)) {
			stream.write('b');
		} else if (Character.class.equals(typeClass)) {
			stream.write('c');
		} else if (OSCImpulse.class.equals(typeClass)) {
			stream.write('I');
		} else {
			throw new UnsupportedOperationException("Do not know the OSC type for the java class: "
					+ typeClass);
		}
	}

	/**
	 * Write the types for an array element in the arguments.
	 * @param arguments array of base Objects
	 */
	private void writeTypesArray(Collection<Object> arguments) throws IOException {

		for (final Object argument : arguments) {
			if (null == argument) {
				stream.write('N');
			} else if (argument instanceof Collection) {
				// If the array at i is a type of array, write a '['.
				// This is used for nested arguments.
				stream.write('[');
				// fill the [] with the SuperCollider types corresponding to
				// the object (e.g., Object of type String needs -s).
				writeTypesArray((Collection<Object>) argument);
				// close the array
				stream.write(']');
			} else if (Boolean.TRUE.equals(argument)) {
				stream.write('T');
			} else if (Boolean.FALSE.equals(argument)) {
				stream.write('F');
			} else {
				// go through the array and write the superCollider types as shown
				// in the above method.
				// The classes derived here are used as the arg to the above method.
				writeType(argument.getClass());
			}
		}
	}

	/**
	 * Write types for the arguments.
	 * @param arguments  the arguments to an OSCMessage
	 */
	public void writeTypes(Collection<Object> arguments) throws IOException {

		writeTypesArray(arguments);
		// we always need to terminate with a zero,
		// even if (especially when) the stream is already aligned.
		stream.write((byte) 0);
		// align the stream with padded bytes
		alignStream();
	}

	/**
	 * Write a 32 bit integer to the byte array without allocating memory.
	 * @param value a 32 bit integer.
	 */
	private void writeInteger32ToByteArray(int value) throws IOException {
		//byte[] intBytes = new byte[4];
		//I allocated the this buffer globally so the GC has less work

		intBytes[3] = (byte)value; value >>>= 8;
		intBytes[2] = (byte)value; value >>>= 8;
		intBytes[1] = (byte)value; value >>>= 8;
		intBytes[0] = (byte)value;

		stream.write(intBytes);
	}

	/**
	 * Write a 64 bit integer to the byte array without allocating memory.
	 * @param value a 64 bit integer.
	 */
	private void writeInteger64ToByteArray(long value) throws IOException {
		longintBytes[7] = (byte)value; value >>>= 8;
		longintBytes[6] = (byte)value; value >>>= 8;
		longintBytes[5] = (byte)value; value >>>= 8;
		longintBytes[4] = (byte)value; value >>>= 8;
		longintBytes[3] = (byte)value; value >>>= 8;
		longintBytes[2] = (byte)value; value >>>= 8;
		longintBytes[1] = (byte)value; value >>>= 8;
		longintBytes[0] = (byte)value;

		stream.write(longintBytes);
	}
}
