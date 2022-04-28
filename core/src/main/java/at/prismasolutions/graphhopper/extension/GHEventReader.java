package at.prismasolutions.graphhopper.extension;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;

public class GHEventReader {
	final Logger logger = LoggerFactory.getLogger(getClass());

	public List<GHEvent> read(File file) {
		List<GHEvent> list = new ArrayList<GHEvent>();
		try (JsonParser jParser = new JsonFactory().createParser(file);) {
			// loop until token equal to "}"
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jParser.getCurrentName();
				if ("ghEvents".equals(fieldname)) {
					if (jParser.nextToken() == JsonToken.START_ARRAY) {
						// ghEvents is array, loop until token equal to "]"
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							if (jParser.getCurrentToken() == JsonToken.START_OBJECT) {
								GHEvent event = new GHEvent();
								while (jParser.nextToken() != JsonToken.END_OBJECT) {
									String eventfieldname = jParser.getCurrentName();
									if ("type".equals(eventfieldname)) {
										int type = jParser.nextIntValue(0);
										switch (type) {
										case 0:
											event.setType(GHEventType.ALL);
											break;
										case 1:
											event.setType(GHEventType.DESCRIPTION);
											break;
										case 2:
											event.setType(GHEventType.EQUAL);
											break;
										case 3:
											event.setType(GHEventType.LESSERTHAN);
											break;
										case 4:
											event.setType(GHEventType.GREATERTHAN);
											break;
										}
									}
									if ("startDate".equals(eventfieldname)) {
										if (jParser.getCurrentValue() != null) {
											String date = jParser.nextTextValue();
											OffsetDateTime temp = OffsetDateTime.parse(date,
													DateTimeFormatter.ISO_DATE_TIME);
											event.setStartDate(Instant.from(temp));
										}
									}
									if ("endDate".equals(eventfieldname)) {
										if (jParser.getCurrentValue() != null) {
											String date = jParser.nextTextValue();
											OffsetDateTime temp = OffsetDateTime.parse(date,
													DateTimeFormatter.ISO_DATE_TIME);
											event.setEndDate(Instant.from(temp));
										}
									}
									if ("direction".equals(eventfieldname)) {
										int val = jParser.nextIntValue(2);
										event.setDirection((short) val);
									}
									if ("statFrom".equals(eventfieldname)) {
										jParser.nextToken();
										double val = jParser.getDoubleValue();
										event.setStatFrom(val);
									}
									if ("statTo".equals(eventfieldname)) {
										jParser.nextToken();
										double val = jParser.getDoubleValue();
										event.setStatTo(val);
									}
									if ("caption".equals(eventfieldname)) {
										String val = jParser.nextTextValue();
										event.setCaption(val);
									}
									if ("extType".equals(eventfieldname)) {
										String val = jParser.nextTextValue();
										event.setExtType(val);
									}
									if ("extId".equals(eventfieldname)) {
										String val = jParser.nextTextValue();
										event.setExtId(val);
									}
									if ("extEdgeId".equals(eventfieldname)) {
										String val = jParser.nextTextValue();
										event.setExtEdgeId(val);
									}
									if ("shape".equals(eventfieldname)) {
										String val = jParser.nextTextValue();
										event.setShape(val);
									}
									if ("parameterName".equals(eventfieldname)) {
										String val = jParser.nextTextValue();
										event.setParameterName(val);
									}
									if ("parameterValue".equals(eventfieldname)) {
										jParser.nextToken();
										double val = jParser.getValueAsDouble();
										event.setParameterValue(val);
									}
									if ("factor".equals(eventfieldname)) {
										jParser.nextToken();
										double val = jParser.getDoubleValue();
										event.setFactor(val);
									}
								}
								list.add(event);
							}
						}
					}
				}
			}
		} catch (JsonGenerationException e) {
			logger.error("Error reading GHEvents", e);
		} catch (JsonMappingException e) {
			logger.error("Error reading GHEvents", e);
		} catch (IOException e) {
			logger.error("Error reading GHEvents", e);
		}
		return list;

	}

}
