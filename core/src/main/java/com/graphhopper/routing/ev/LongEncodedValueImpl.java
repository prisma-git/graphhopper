/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.ev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.lang.model.SourceVersion;

/**
 * Implementation of the IntEncodedValue via a certain number of bits (that determines the maximum value) and
 * a minimum value (default is 0).
 * With storeTwoDirections = true it can store separate values for forward and reverse edge direction e.g. for one speed
 * value per direction of an edge.
 * With negateReverseDirection = true it supports negating the value for the reverse direction without storing a separate
 * value e.g. to store an elevation slope which is negative for the reverse direction but has otherwise the same value
 * and is used to save storage space.
 */
public class LongEncodedValueImpl implements LongEncodedValue {
    private final String name;
    private final boolean storeTwoDirections;
    final int bits;
    final boolean negateReverseDirection;
    final long minStorableValue;
    final long maxStorableValue;
    long maxValue;

    /**
     * There are multiple int values possible per edge. Here we specify the index into this integer array.
     */
    private int fwdDataIndex;
    private int bwdDataIndex;
    int fwdShift = -1;
    int bwdShift = -1;
    int fwdMask;
    int bwdMask;
	private int fwdMask2;
	private int fwdDataIndex2;
	private int fwdShift2;
	private int bwdMask2;
	private int bwdDataIndex2;
	private int bwdShift2;

    /**
     * @see #IntEncodedValueImpl(String, int, int, boolean, boolean)
     */
    public LongEncodedValueImpl(String name,int bits, boolean storeTwoDirections) {
        this(name, bits, 0, false, storeTwoDirections);
    }

    /**
     * This creates an EncodedValue to store an integer value with up to the specified bits.
     *
     * @param name                   the key to identify this EncodedValue
     * @param bits                   the bits that should be reserved for storing the value. This determines the
     *                               maximum value.
     * @param minStorableValue       the minimum value. Use e.g. 0 if no negative values are needed.
     * @param negateReverseDirection true if the reverse direction should be always negative of the forward direction.
     *                               This is used to reduce space and store the value only once. If this option is used
     *                               you cannot use storeTwoDirections or a minValue different to 0.
     * @param storeTwoDirections     true if forward and backward direction of the edge should get two independent values.
     */
    public LongEncodedValueImpl(String name, int bits, long minStorableValue, boolean negateReverseDirection, boolean storeTwoDirections) {
        if (!isValidEncodedValue(name))
            throw new IllegalArgumentException("EncodedValue name wasn't valid: " + name + ". Use lower case letters, underscore and numbers only.");
        if (bits <= 0)
            throw new IllegalArgumentException(name + ": bits cannot be zero or negative");
        if (bits > 63)
            throw new IllegalArgumentException(name + ": at the moment the number of reserved bits cannot be more than 63");
        if (bits <= 32)
            throw new IllegalArgumentException(name + ": for 32 bit or less use IntEnodedValue");
        if (negateReverseDirection && (minStorableValue != 0 || storeTwoDirections))
            throw new IllegalArgumentException(name + ": negating value for reverse direction only works for minValue == 0 " +
                    "and !storeTwoDirections but was minValue=" + minStorableValue + ", storeTwoDirections=" + storeTwoDirections);
        this.name = name;
        this.storeTwoDirections = storeTwoDirections;
        long max = (1L << bits) - 1;
        // negateReverseDirection: store the negative value only once, but for that we need the same range as maxValue for negative values
        this.minStorableValue = negateReverseDirection ? -max : minStorableValue;
        this.maxStorableValue = max + minStorableValue;
        if (minStorableValue == Long.MIN_VALUE)
            // we do not allow this because we use this value to represent maxValue = untouched, i.e. no value has been set yet
            throw new IllegalArgumentException(Long.MIN_VALUE + " is not allowed for minValue");
        this.maxValue = Long.MIN_VALUE;
        // negateReverseDirection: we need twice the integer range, i.e. 1 more bit
        this.bits = negateReverseDirection ? bits+1 : bits;
        this.negateReverseDirection = negateReverseDirection;
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    LongEncodedValueImpl(@JsonProperty("name") String name,
                        @JsonProperty("bits") int bits,
                        @JsonProperty("min_storable_value") long minStorableValue,
                        @JsonProperty("max_storable_value") long maxStorableValue,
                        @JsonProperty("max_value") long maxValue,
                        @JsonProperty("negate_reverse_direction") boolean negateReverseDirection,
                        @JsonProperty("store_two_directions") boolean storeTwoDirections,
                        @JsonProperty("fwd_data_index") int fwdDataIndex,
                        @JsonProperty("bwd_data_index") int bwdDataIndex,
                        @JsonProperty("fwd_shift") int fwdShift,
                        @JsonProperty("bwd_shift") int bwdShift,
                        @JsonProperty("fwd_mask") int fwdMask,
                        @JsonProperty("bwd_mask") int bwdMask,
                        @JsonProperty("fwd_data_index2") int fwdDataIndex2,
                        @JsonProperty("bwd_data_index2") int bwdDataIndex2,
                        @JsonProperty("fwd_shift2") int fwdShift2,
                        @JsonProperty("bwd_shift2") int bwdShift2,
                        @JsonProperty("fwd_mask2") int fwdMask2,
                        @JsonProperty("bwd_mask2") int bwdMask2
    ) {
        // we need this constructor for Jackson
        this.name = name;
        this.storeTwoDirections = storeTwoDirections;
        this.bits = bits;
        this.negateReverseDirection = negateReverseDirection;
        this.minStorableValue = minStorableValue;
        this.maxStorableValue = maxStorableValue;
        this.maxValue = maxValue;
        this.fwdDataIndex = fwdDataIndex;
        this.bwdDataIndex = bwdDataIndex;
        this.fwdShift = fwdShift;
        this.bwdShift = bwdShift;
        this.fwdMask = fwdMask;
        this.bwdMask = bwdMask;
        this.fwdDataIndex2 = fwdDataIndex2;
        this.bwdDataIndex2 = bwdDataIndex2;
        this.fwdShift2 = fwdShift2;
        this.bwdShift2 = bwdShift2;
        this.fwdMask2 = fwdMask2;
        this.bwdMask2 = bwdMask2;

    }

    @Override
    public final int init(EncodedValue.InitializerConfig init) {
        if (isInitialized())
            throw new IllegalStateException("Cannot call init multiple times");

        init.next(bits-32);
        this.fwdMask = init.bitMask;
        this.fwdDataIndex = init.dataIndex;
        this.fwdShift = init.shift;
        init.next(32);
        this.fwdMask2 = init.bitMask;
        this.fwdDataIndex2 = init.dataIndex;
        this.fwdShift2 = init.shift;
        if (storeTwoDirections) {
            init.next(bits-32);
            this.bwdMask = init.bitMask;
            this.bwdDataIndex = init.dataIndex;
            this.bwdShift = init.shift;
            init.next(32);
            this.bwdMask2 = init.bitMask;
            this.bwdDataIndex2 = init.dataIndex;
            this.bwdShift2 = init.shift;
        }

        return storeTwoDirections ? 2 * bits : bits;
    }

    boolean isInitialized() {
        return fwdMask != 0;
    }

    @Override
    public final void setLong(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess, long value) {
        checkValue(value);
        uncheckedSet(reverse, edgeId, edgeIntAccess, value);
    }

    private void checkValue(long value) {
        if (!isInitialized())
            throw new IllegalStateException("EncodedValue " + getName() + " not initialized");
        if (value > maxStorableValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value + ", maxValue:" + maxStorableValue);
        if (value < minStorableValue)
            throw new IllegalArgumentException(name + " value too small for encoding " + value + ", minValue:" + minStorableValue);
    }

    final void uncheckedSet(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess, long value) {
        if (negateReverseDirection) {
            if (reverse) {
                reverse = false;
                value = -value;
            }
        } else if (reverse && !storeTwoDirections)
            throw new IllegalArgumentException(getName() + ": value for reverse direction would overwrite forward direction. Enable storeTwoDirections for this EncodedValue or don't use setReverse");

        maxValue = Math.max(maxValue, value);

        value -= minStorableValue;
        
        int x = (int)(value >> 32);
        int y = (int)value;
        if (reverse) {
            int flags = edgeIntAccess.getInt(edgeId, bwdDataIndex);
            // clear value bits
            flags &= ~bwdMask;
            edgeIntAccess.setInt(edgeId, bwdDataIndex, flags | (x << bwdShift));
            int flags2 = edgeIntAccess.getInt(edgeId, bwdDataIndex2);
            // clear value bits
            flags2 &= ~bwdMask2;
            edgeIntAccess.setInt(edgeId, bwdDataIndex2, flags2 | (y << bwdShift2));
        } else {
            int flags = edgeIntAccess.getInt(edgeId, fwdDataIndex);
            flags &= ~fwdMask;
            edgeIntAccess.setInt(edgeId, fwdDataIndex, flags | (x << fwdShift));
            int flags2 = edgeIntAccess.getInt(edgeId, fwdDataIndex2);
            flags2 &= ~fwdMask2;
            edgeIntAccess.setInt(edgeId, fwdDataIndex2, flags2 | (y << fwdShift2));
        }
    }

    @Override
    public final long getLong(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess) {
        assert fwdShift >= 0 : "incorrect shift " + fwdShift + " for " + getName();
        assert bits > 0 : "incorrect bits " + bits + " for " + getName();

        int flags;
        // if we do not store both directions ignore reverse == true for convenient reading
        if (storeTwoDirections && reverse) {
            flags = edgeIntAccess.getInt(edgeId, bwdDataIndex);
            int x = ((flags & bwdMask) >>> bwdShift);
            flags = edgeIntAccess.getInt(edgeId, bwdDataIndex2);
            int y = ((flags & bwdMask2) >>> bwdShift2);
            long value = (((long)x) << 32) | (y & 0xffffffffL);
            return minStorableValue + value;
        } else {
            flags = edgeIntAccess.getInt(edgeId, fwdDataIndex);
            int x = ((flags & fwdMask) >>> fwdShift);
            flags = edgeIntAccess.getInt(edgeId, fwdDataIndex2);
            int y = ((flags & fwdMask2) >>> fwdShift2);
            long value = (((long)x) << 32) | (y & 0xffffffffL);
            if (negateReverseDirection && reverse)
                return -(minStorableValue + value);
            return minStorableValue + value;
        }
    }

    @Override
    public long getMaxStorableLong() {
        return maxStorableValue;
    }

    @Override
    public long getMinStorableLong() {
        return minStorableValue;
    }

    @Override
    public long getMaxOrMaxStorableLong() {
        return maxValue == Long.MIN_VALUE ? getMaxStorableLong() : maxValue;
    }

    @Override
    public final boolean isStoreTwoDirections() {
        return storeTwoDirections;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String toString() {
        return getName();
    }

    public static boolean isValidEncodedValue(String name) {
        if (name.length() < 2 || name.startsWith("in_") || name.startsWith("backward_")
                || !isLowerLetter(name.charAt(0)) || SourceVersion.isKeyword(name))
            return false;

        int underscoreCount = 0;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                if (underscoreCount > 0) return false;
                underscoreCount++;
            } else if (!isLowerLetter(c) && !Character.isDigit(c)) {
                return false;
            } else {
                underscoreCount = 0;
            }
        }
        return true;
    }

    private static boolean isLowerLetter(char c) {
        return c >= 'a' && c <= 'z';
    }
}
