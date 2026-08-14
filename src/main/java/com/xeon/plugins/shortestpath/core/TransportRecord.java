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

import java.util.Map;

final class TransportRecord
{
	static final String ORIGIN = "Origin";
	static final String DESTINATION = "Destination";
	static final String SKILLS = "Skills";
	static final String QUESTS = "Quests";
	static final String VARBITS = "Varbits";
	static final String VAR_PLAYERS = "VarPlayers";
	static final String DURATION = "Duration";
	static final String DISPLAY_INFO = "Display info";
	static final String CONSUMABLE = "Consumable";
	static final String WILDERNESS_LEVEL = "Wilderness level";
	static final String OBJECT_INFO = "menuOption menuTarget objectID";

	private final Map<String, String> fields;

	TransportRecord(Map<String, String> fields)
	{
		this.fields = Map.copyOf(fields);
	}

	String get(String fieldName)
	{
		return fields.get(fieldName);
	}

	boolean hasKey(String fieldName)
	{
		return fields.containsKey(fieldName);
	}
}
