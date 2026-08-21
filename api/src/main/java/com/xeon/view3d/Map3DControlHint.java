package com.xeon.view3d;

public record Map3DControlHint(
	String input,
	String action
)
{
	public Map3DControlHint
	{
		input = input == null ? "" : input;
		action = action == null ? "" : action;
	}
}
