package at.prismasolutions.graphhopper.extension;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

			// ghEvents is array, loop until token equal to "]"
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				if (jParser.getCurrentToken() == JsonToken.START_OBJECT) {
					GHEvent event = new GHEvent();
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
						// Only field names introduce a value; guard defensively so an
						// unexpected token can never be read as if it were a field.
						if (jParser.getCurrentToken() != JsonToken.FIELD_NAME) {
							continue;
						}
						String eventfieldname = jParser.currentName();
						// Advance onto the value token. No GHEvent field carries a structured
						// value, so any object/array (an unknown field, or e.g. a GeoJSON "shape")
						// is skipped whole via skipChildren() before dispatch - otherwise the
						// hand-rolled token stream would step into it and corrupt the remaining
						// events. Scalar values are read via getValueAs* (rather than
						// nextTextValue/nextIntValue) so a number written where a string is
						// expected, and vice versa, is tolerated.
						jParser.nextToken();
						if (jParser.getCurrentToken() == JsonToken.START_OBJECT
								|| jParser.getCurrentToken() == JsonToken.START_ARRAY) {
							jParser.skipChildren();
							continue;
						}
						switch (eventfieldname) {
							case "type":
								switch (jParser.getValueAsInt(0)) {
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
								break;
							case "startDate":
								event.setStartDate(parseDate(jParser));
								break;
							case "endDate":
								event.setEndDate(parseDate(jParser));
								break;
							case "direction":
								event.setDirection((short) jParser.getValueAsInt(2));
								break;
							case "statFrom":
								event.setStatFrom(jParser.getValueAsDouble());
								break;
							case "statTo":
								event.setStatTo(jParser.getValueAsDouble());
								break;
							case "caption":
								event.setCaption(jParser.getValueAsString());
								break;
							case "extType":
								event.setExtType(jParser.getValueAsString());
								break;
							case "extId":
								event.setExtId(jParser.getValueAsString());
								break;
							case "extEdgeId":
								// OSM way id: accept a quoted string ("12620747") or a raw JSON
								// number (12620747); getValueAsString normalises both to the same
								// key used by GHEventMapper (Long.toString of the edge's way id).
								event.setExtEdgeId(jParser.getValueAsString());
								break;
							case "shape":
								event.setShape(jParser.getValueAsString());
								break;
							case "parameterName":
								event.setParameterName(jParser.getValueAsString());
								break;
							case "parameterValue":
								event.setParameterValue(jParser.getValueAsDouble());
								break;
							case "factor":
								event.setFactor(jParser.getValueAsDouble());
								break;
							default:
								// unknown scalar field: value already consumed, nothing to do
								break;
						}
					}
					list.add(event);
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

	/**
	 * Reads the value of the current date field and converts it to an {@link Instant}.
	 * <p>
	 * Accepts an ISO-8601 date-time string (e.g. {@code 2022-02-02T14:25:34+02:00}) as
	 * well as a numeric unix timestamp (auto-detected as seconds or milliseconds) or a
	 * numeric string. Returns {@code null} for a JSON {@code null} or an unparseable value.
	 * <p>
	 * The caller has already advanced the parser onto the value token, so this reads the
	 * current token instead of advancing.
	 */
	private Instant parseDate(JsonParser jParser) throws IOException {
		JsonToken token = jParser.currentToken();
		if (token == null || token == JsonToken.VALUE_NULL) {
			return null;
		}
		if (token.isNumeric()) {
			return fromEpoch(jParser.getLongValue());
		}
		String text = jParser.getValueAsString();
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		text = text.trim();
		try {
			return Instant.from(OffsetDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME));
		} catch (DateTimeParseException e) {
			try {
				return fromEpoch(Long.parseLong(text));
			} catch (NumberFormatException nfe) {
				logger.warn("Ignoring unparseable GHEvent date: {}", text);
				return null;
			}
		}
	}

	/** Interpret an epoch value as milliseconds when it is clearly too large to be seconds. */
	private Instant fromEpoch(long epoch) {
		return Math.abs(epoch) >= 100_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
	}

}
