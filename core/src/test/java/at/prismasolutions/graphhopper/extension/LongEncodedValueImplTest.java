package at.prismasolutions.graphhopper.extension;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.LongEncodedValue;
import com.graphhopper.routing.ev.LongEncodedValueImpl;
import com.graphhopper.routing.ev.EdgeIntAccess;

import static com.graphhopper.routing.ev.LongEncodedValueImpl.isValidEncodedValue;
import static org.junit.jupiter.api.Assertions.*;

public class LongEncodedValueImplTest {

	@Test
    public void testInvalidReverseAccess() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 43, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.setLong(true, 0, createIntAccess(1), -1);
            fail();
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDirectedValue() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 43, true);
        prop.init(new EncodedValue.InitializerConfig());
        EdgeIntAccess edgeIntAccess = createIntAccess(1);
        prop.setLong(false, 0, edgeIntAccess, 100000000010L);
        prop.setLong(true, 0, edgeIntAccess, 100000000020L);
        assertEquals(100000000010L, prop.getLong(false, 0, edgeIntAccess));
        assertEquals(100000000020L, prop.getLong(true, 0, edgeIntAccess));
    }

    @Test
    public void multiIntsUsage() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 63, true);
        prop.init(new EncodedValue.InitializerConfig());
        EdgeIntAccess edgeIntAccess = createIntAccess(2);
        prop.setLong(false, 0, edgeIntAccess, 100000000010L);
        prop.setLong(true, 0, edgeIntAccess, 100000000020L);
        assertEquals(100000000010L, prop.getLong(false, 0, edgeIntAccess));
        assertEquals(100000000020L, prop.getLong(true, 0, edgeIntAccess));
    }

    @Test
    public void padding() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 62, true);
        prop.init(new EncodedValue.InitializerConfig());
        EdgeIntAccess edgeIntAccess = createIntAccess(2);
        prop.setLong(false, 0, edgeIntAccess, 100000000010L);
        prop.setLong(true, 0, edgeIntAccess, 100000000020L);
        assertEquals(100000000010L, prop.getLong(false, 0, edgeIntAccess));
        assertEquals(100000000020L, prop.getLong(true, 0, edgeIntAccess));
    }

    @Test
    public void maxValue() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 63, false);
        prop.init(new EncodedValue.InitializerConfig());
        EdgeIntAccess edgeIntAccess = createIntAccess(2);
        prop.setLong(false, 0, edgeIntAccess, (1L << 63) - 1);
        assertEquals(9_223_372_036_854_775_807L, prop.getLong(false, 0, edgeIntAccess));
    }

    @Test
    public void testSignedInt() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 63, -1000000000005L, false, true);
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        prop.init(config);

        EdgeIntAccess edgeIntAccess = createIntAccess(1);
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            prop.setLong(false, 0, edgeIntAccess, Long.MAX_VALUE);
        });
        assertTrue(exception.getMessage().contains("test value too large for encoding"), exception.getMessage());

        prop.setLong(false, 0, edgeIntAccess, -10000000005L);
        assertEquals(-10000000005L, prop.getLong(false, 0, edgeIntAccess));
        assertEquals(-10000000005L, prop.getLong(false, 0, edgeIntAccess));
    }

    @Test
    public void testSignedInt2() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 63, false);
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        prop.init(config);

        EdgeIntAccess edgeIntAccess = createIntAccess(1);
        prop.setLong(false, 0, edgeIntAccess, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, prop.getLong(false, 0, edgeIntAccess));

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            prop.setLong(false, 0, edgeIntAccess, -5);
        });
        assertTrue(exception.getMessage().contains("test value too small for encoding"), exception.getMessage());
    }

    @Test
    public void testNegateReverseDirection() {
        LongEncodedValue prop = new LongEncodedValueImpl("test", 37, 0, true, false);
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        prop.init(config);

        EdgeIntAccess edgeIntAccess = createIntAccess(1);
        prop.setLong(false, 0, edgeIntAccess, 5);
        assertEquals(5, prop.getLong(false, 0, edgeIntAccess));
        assertEquals(-5, prop.getLong(true, 0, edgeIntAccess));

        prop.setLong(true, 0, edgeIntAccess, 2);
        assertEquals(-2, prop.getLong(false, 0, edgeIntAccess));
        assertEquals(2, prop.getLong(true, 0, edgeIntAccess));

        prop.setLong(false, 0, edgeIntAccess, -3);
        assertEquals(-3, prop.getLong(false, 0, edgeIntAccess));
        assertEquals(3, prop.getLong(true, 0, edgeIntAccess));
    }

    @Test
    public void testEncodedValueName() {
        for (String str : Arrays.asList("blup_test", "test", "test12", "car_test_test")) {
            assertTrue(isValidEncodedValue(str), str);
        }

        for (String str : Arrays.asList("Test", "12test", "test|3", "car__test", "small_car$average_speed", "tes$0",
                "blup_te.st_", "car___test", "car$$access", "test{34", "truck__average_speed", "blup.test", "test,21",
                "täst", "blup.two.three", "blup..test")) {
            assertFalse(isValidEncodedValue(str), str);
        }

        for (String str : Arrays.asList("break", "switch")) {
            assertFalse(isValidEncodedValue(str), str);
        }
    }

    private static ArrayEdgeIntAccess createIntAccess(int ints) {
        return new ArrayEdgeIntAccess(ints);
    }
}