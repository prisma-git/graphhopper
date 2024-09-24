package at.prismasolutions.graphhopper.extension;

import static com.graphhopper.routing.util.EncodingManager.getKey;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

public class FreeFlagEncoder extends AbstractFlagEncoder {

    static final double MEAN_SPEED = 5;
    private static final Logger logger = LoggerFactory.getLogger(GraphHopper.class);

	protected FreeFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
		super(speedBits, speedFactor, maxTurnCosts);
		this.restrictions.clear();
		this.restrictedValues.clear();	
		maxPossibleSpeed = (int) MEAN_SPEED;
	}
	public FreeFlagEncoder(PMap properties) {
        this(properties.getInt("speed_bits", 4), properties.getDouble("speed_factor", 1), 0);
    }

	@Override
	public TransportationMode getTransportationMode() {
		return TransportationMode.FOOT;
	}
	
	@Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix) {
        // first two bits are reserved for route handling in superclass
		logger.info("Register for prefix: "+prefix);
        super.createEncodedValues(registerNewEncodedValue, prefix);
        
        registerNewEncodedValue.add(avgSpeedEnc = new DecimalEncodedValueImpl(getKey(prefix, "average_speed"), speedBits, speedFactor, true));
        
    }

	@Override
	public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
		//allow both directions always
		accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        setSpeed(false, edgeFlags, MEAN_SPEED);
        setSpeed(true, edgeFlags, MEAN_SPEED);
		return edgeFlags;
	}

	@Override
	public Access getAccess(ReaderWay way) {
		return EncodingManager.Access.WAY;
	}
	
	@Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }
	
	@Override
    public String toString() {
        return "free";
    }

}
