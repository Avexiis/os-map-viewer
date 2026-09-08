package com.xeon.view3d;

import java.awt.Color;

public record Map3DTextSegment(String text, Color color)
{
	public Map3DTextSegment
	{
		text = text == null ? "" : text;
		color = color == null ? Color.WHITE : color;
	}
}
