package at.prismasolutions.graphhopper.extension.routing.ev;

import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValueImpl;

public class Fuehrung {
	public static final String KEY = "fuehrung";

    public static IntEncodedValue create() {
        return new IntEncodedValueImpl(KEY, 31, false);
    }
}
