package com.xeon.view3d;

public record Map3DTileAction(
	String label,
	Runnable action
)
{
	public Map3DTileAction
	{
		label = label == null ? "" : label;
	}

	public void run()
	{
		if (action != null)
		{
			action.run();
		}
	}
}
