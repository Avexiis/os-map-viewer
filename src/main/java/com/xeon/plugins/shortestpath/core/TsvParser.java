/*
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
package com.xeon.plugins.shortestpath.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

final class TsvParser
{
	List<TransportRecord> parse(String contents)
	{
		List<TransportRecord> records = new ArrayList<>();
		try (Scanner scanner = new Scanner(contents))
		{
			if (!scanner.hasNextLine())
			{
				return records;
			}
			String[] headers = parseHeaderLine(scanner.nextLine());
			while (scanner.hasNextLine())
			{
				String line = scanner.nextLine();
				if (line.startsWith("#") || line.isBlank())
				{
					continue;
				}
				records.add(parseLine(line, headers));
			}
		}
		return records;
	}

	private String[] parseHeaderLine(String headerLine)
	{
		String normalized = headerLine;
		if (normalized.startsWith("# "))
		{
			normalized = normalized.substring(2);
		}
		else if (normalized.startsWith("#"))
		{
			normalized = normalized.substring(1);
		}
		return normalized.split("\t");
	}

	private TransportRecord parseLine(String line, String[] headers)
	{
		String[] fields = line.split("\t", -1);
		Map<String, String> fieldMap = new HashMap<>();
		for (int i = 0; i < headers.length; i++)
		{
			if (i < fields.length)
			{
				fieldMap.put(headers[i], fields[i]);
			}
		}
		return new TransportRecord(fieldMap);
	}
}
