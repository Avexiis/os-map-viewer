/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.

 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xeon.plugins.meshexport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class RuneProfileModelClient
{
	private static final String MODEL_ENDPOINT = "https://api.runeprofile.com/profiles/models/";
	private static final int MAX_MODEL_BYTES = 25 * 1024 * 1024;
	private static final int BUFFER_SIZE = 16 * 1024;

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	byte[] fetch(String username) throws IOException, InterruptedException
	{
		String cleanUsername = username == null ? "" : username.trim();
		if (cleanUsername.isBlank())
		{
			throw new IOException("Enter a RuneProfile username");
		}
		String encoded = URLEncoder.encode(cleanUsername, StandardCharsets.UTF_8).replace("+", "%20");
		HttpRequest request = HttpRequest.newBuilder(URI.create(MODEL_ENDPOINT + encoded))
			.timeout(Duration.ofSeconds(30))
			.header("Accept", "application/octet-stream")
			.header("User-Agent", "OSMapViewer Mesh Export Plugin")
			.GET()
			.build();
		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream input = response.body())
		{
			if (response.statusCode() == 404)
			{
				throw new IOException("RuneProfile has no uploaded player model for " + cleanUsername);
			}
			if (response.statusCode() == 429)
			{
				throw new IOException("RuneProfile request limit reached; try again in a minute");
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300)
			{
				throw new IOException("RuneProfile model request failed (" + response.statusCode() + ")");
			}
			long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
			if (contentLength > MAX_MODEL_BYTES)
			{
				throw new IOException("RuneProfile model exceeds the 25 MB limit");
			}
			ByteArrayOutputStream output = new ByteArrayOutputStream(contentLength > 0 ? (int) contentLength : BUFFER_SIZE);
			byte[] buffer = new byte[BUFFER_SIZE];
			int total = 0;
			for (int read; (read = input.read(buffer)) != -1; )
			{
				if (Thread.currentThread().isInterrupted())
				{
					throw new InterruptedException();
				}
				total += read;
				if (total > MAX_MODEL_BYTES)
				{
					throw new IOException("RuneProfile model exceeds the 25 MB limit");
				}
				output.write(buffer, 0, read);
			}
			if (total == 0)
			{
				throw new IOException("RuneProfile returned an empty player model");
			}
			return output.toByteArray();
		}
	}
}
