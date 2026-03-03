package at.prismasolutions.graphhopper.extension.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

public class ObjArtParser implements TagParser {

	private final IntEncodedValue objArtEnc;

	public ObjArtParser(IntEncodedValue objArtEnc) {
		this.objArtEnc = objArtEnc;
	}

	@Override
	public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
		if (way.hasTag("objart")) {

			String val = way.getTag("objart");
			Integer intVal;
			try {
				intVal = Integer.parseInt(val);
			} catch (NumberFormatException e) {
				intVal = null;
			}
			if (intVal != null) {
				if (intVal > objArtEnc.getMaxStorableInt())
					throw new IllegalArgumentException("Cannot store ObjArt: " + way.getId()
							+ " as it is too large (> " + objArtEnc.getMaxStorableInt() + "). You can disable "
							+ objArtEnc.getName() + " if you do not " + "need to store ObjArt");
				objArtEnc.setInt(false, edgeId, edgeIntAccess, intVal);
			}
		}
	}
}
