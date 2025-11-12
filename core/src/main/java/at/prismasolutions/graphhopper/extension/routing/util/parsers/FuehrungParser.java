package at.prismasolutions.graphhopper.extension.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

public class FuehrungParser implements TagParser {

	private final IntEncodedValue fuehrungEnc;

	public FuehrungParser(IntEncodedValue fuehrungEnc) {
		this.fuehrungEnc = fuehrungEnc;
	}

	@Override
	public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
		if (way.hasTag("fuehrung")) {

			String val = way.getTag("fuehrung");
			Integer intVal;
			try {
				intVal = Integer.parseInt(val);
			} catch (NumberFormatException e) {
				intVal = null;
			}
			if (intVal != null) {
				if (intVal > fuehrungEnc.getMaxStorableInt())
					throw new IllegalArgumentException("Cannot store Fuehrung: " + way.getId()
							+ " as it is too large (> " + fuehrungEnc.getMaxStorableInt() + "). You can disable "
							+ fuehrungEnc.getName() + " if you do not " + "need to store Fuehrung");
				fuehrungEnc.setInt(false, edgeId, edgeIntAccess, intVal);
			}
		}
	}

}
