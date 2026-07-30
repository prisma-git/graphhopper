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
						String eventfieldname = jParser.currentName();
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
							event.setStartDate(parseDate(jParser));
						}
						if ("endDate".equals(eventfieldname)) {
							event.setEndDate(parseDate(jParser));
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
	 * Note: this advances the parser onto the value token, mirroring the behaviour of the
	 * {@code nextTextValue()}/{@code nextIntValue()} calls used for the other fields.
	 */
	private Instant parseDate(JsonParser jParser) throws IOException {
		JsonToken token = jParser.nextToken();
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
