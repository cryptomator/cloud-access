package org.cryptomator.cloudaccess.webdav;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class PropfindResponseParser {

	private static final Logger LOG = LoggerFactory.getLogger(PropfindResponseParser.class);

	private static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();
	private static final String TAG_RESPONSE = "response";
	private static final String TAG_HREF = "href";
	private static final String TAG_COLLECTION = "collection";
	private static final String TAG_LAST_MODIFIED = "getlastmodified";
	private static final String TAG_CONTENT_LENGTH = "getcontentlength";
	private static final String TAG_PROPSTAT = "propstat";
	private static final String TAG_STATUS = "status";
	private static final String STATUS_OK = "200";

	static {
		PARSER_FACTORY.setNamespaceAware(true);
	}

	private final SAXParser parser;

	PropfindResponseParser() {
		try {
			this.parser = PARSER_FACTORY.newSAXParser();
		} catch (ParserConfigurationException | SAXException e) {
			throw new IllegalStateException(e);
		}
	}

	public List<PropfindEntryData> parse(final InputStream responseBody) throws SAXException, IOException {
		if (responseBody == null) {
			return List.of();
		}
		var parseHandler = new ParseHandler();
		parser.parse(responseBody, parseHandler);
		return parseHandler.entries;
	}

	private class ParseHandler extends DefaultHandler {

		public final List<PropfindEntryData> entries = new ArrayList<>();
		private StringBuilder textBuffer;
		private String href;
		private String lastModified;
		private String contentLength;
		private String status;
		private boolean isCollection;

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			switch (localName.toLowerCase()) {
				case TAG_RESPONSE:
					href = null;
					lastModified = null;
					contentLength = null;
					status = null;
					isCollection = false;
					break;
				case TAG_HREF:
				case TAG_LAST_MODIFIED:
				case TAG_CONTENT_LENGTH:
				case TAG_STATUS:
					textBuffer = new StringBuilder();
					break;
				case TAG_COLLECTION:
					isCollection = true;
					break;
				default:
					// no-op
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) {
			if (textBuffer != null) {
				textBuffer.append(ch, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			switch (localName.toLowerCase()) {
				case TAG_PROPSTAT:
					assembleEntry();
					break;
				case TAG_HREF:
					href = textBuffer.toString();
					break;
				case TAG_LAST_MODIFIED:
					lastModified = textBuffer.toString();
					break;
				case TAG_CONTENT_LENGTH:
					contentLength = textBuffer.toString();
					break;
				case TAG_STATUS:
					status = textBuffer.toString();
					break;
				default:
					// no-op
			}
		}

		private void assembleEntry() {
			if (!status.contains(STATUS_OK)) {
				LOG.trace("No propstat element with 200 status in response element. Entry ignored.");
				return; // no-op
			}

			if (href == null) {
				LOG.trace("Missing href in response element. Entry ignored.");
				return; // no-op
			}

			var entry = new PropfindEntryData();
			entry.setLastModified(parseDate(lastModified));
			entry.setSize(parseLong(contentLength));
			entry.setPath(href);
			entry.setFile(!isCollection);

			entries.add(entry);
		}

		private Optional<Instant> parseDate(final String text) {
			try {
				return Optional.of(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text)));
			} catch (DateTimeException e) {
				return Optional.empty();
			}
		}

		private Optional<Long> parseLong(final String text) {
			try {
				return Optional.of(Long.parseLong(text));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		}

	}


}
