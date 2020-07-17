package org.cryptomator.cloudaccess.webdav;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.ProtocolException;

class WebDavRedirectHandler {

	private static final int MAX_REDIRECT_COUNT = 20;
	private static final Request NO_REDIRECTED_REQUEST = null;
	private static final HttpUrl NO_REDIRECT_URL = null;

	private final OkHttpClient httpClient;

	WebDavRedirectHandler(final OkHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public Response executeFollowingRedirects(Request request) throws IOException {
		Response response;
		var redirectCount = 0;
		do {
			if (redirectCount > MAX_REDIRECT_COUNT) {
				throw new ProtocolException("Too many redirects: " + redirectCount);
			}
			response = httpClient.newCall(request).execute();
			request = redirectedRequestFor(response);
			redirectCount++;
		} while (request != NO_REDIRECTED_REQUEST);
		return response;
	}

	private Request redirectedRequestFor(final Response response) {
		switch (response.code()) {
			case 300: // fall through
			case 301: // fall through
			case 302: // fall through
			case 303: // fall through
			case 307: // fall through
			case 308:
				return createRedirectedRequest(response);
			default:
				return NO_REDIRECTED_REQUEST;
		}
	}

	private Request createRedirectedRequest(final Response response) {
		final var url = redirectUrl(response);
		if (url == NO_REDIRECT_URL) {
			return NO_REDIRECTED_REQUEST;
		}
		return createRedirectedRequest(response, url);
	}

	private Request createRedirectedRequest(final Response response, final HttpUrl url) {
		final var requestBuilder = response.request().newBuilder().url(url);
		if (methodShouldBeChangedToGet(response)) {
			changeMethodToGet(requestBuilder);
		}
		if (!connectionMatches(response.request().url(), url)) {
			requestBuilder.removeHeader("Authorization");
		}
		return requestBuilder.build();
	}

	private boolean methodShouldBeChangedToGet(final Response response) {
		return response.code() == 300 //
				|| response.code() == 303;
	}

	private void changeMethodToGet(final Request.Builder requestBuilder) {
		requestBuilder.method("GET", null) //
				.removeHeader("Transfer-Encoding") //
				.removeHeader("Content-Length") //
				.removeHeader("Content-Type");
	}

	private boolean connectionMatches(final HttpUrl url1, final HttpUrl url2) {
		return url1.scheme().equals(url2.scheme()) //
				&& url1.host().equals(url2.host()) //
				&& url1.port() == url2.port();
	}

	private HttpUrl redirectUrl(final Response response) {
		final var location = response.header("Location");
		if (location == null) {
			return NO_REDIRECT_URL;
		}
		return response.request().url().resolve(location);
	}

}
