package com.graphhopper.routing.ev;

/**
 * This class defines how and where to store an unsigned long. It is important to note that: 1. the range of the
 * long is highly limited (unlike the Java 32bit long values) so that the storable part of it fits longo the
 * specified number of bits (maximum 32) and 2. the default value is always 0.
 *
 * @see LongEncodedValueImpl
 */
public interface LongEncodedValue extends EncodedValue {

    /**
     * This method restores the long value from the specified 'flags' taken from the storage.
     */
    long getLong(boolean reverse, int edgeId, EdgeIntAccess edgeLongAccess);

    /**
     * This method stores the specified long value in the specified LongsRef.
     */
    void setLong(boolean reverse, int edgeId, EdgeIntAccess edgeLongAccess, long value);

    /**
     * The maximum long value this EncodedValue accepts for setLong without throwing an exception.
     */
    long getMaxStorableLong();

    /**
     * The minimum long value this EncodedValue accepts for setLong without throwing an exception.
     */
    long getMinStorableLong();

    /**
     * Returns the maximum value set using this encoded value or the physical storage limit if no value has been set
     * at all yet. Note that even when some values were set this is not equal to the global maximum across all values in
     * the graph if values are set multiple times for the same edge and they are decreasing. However, the returned value
     * will always be equal to or larger than the global maximum.
     */
    long getMaxOrMaxStorableLong();

    /**
     * @return true if this EncodedValue can store a different value for its reverse direction
     */
    boolean isStoreTwoDirections();
}