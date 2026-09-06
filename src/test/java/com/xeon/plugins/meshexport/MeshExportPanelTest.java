package com.xeon.plugins.meshexport;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class MeshExportPanelTest
{
	@Test void startsEmptyWithAnEvenPreviewAndControlSplit() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			MeshExportPanel panel = new MeshExportPanel(null, null);
			panel.setSize(330, 800);
			layoutTree(panel);

			Component preview = panel.getComponent(0);
			Component controls = panel.getComponent(1);
			assertEquals(preview.getHeight(), controls.getHeight());
			assertTrue(preview.getHeight() >= 380 && preview.getHeight() <= 400);
			assertFalse(button(panel, "Export Mesh...").isEnabled());
			assertFalse(button(panel, "Cancel").isEnabled());
			assertFalse(button(panel, "Clear Selection").isEnabled());

			BufferedImage image = new BufferedImage(330, 800, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = image.createGraphics();
			try { panel.paint(graphics); }
			finally { graphics.dispose(); }
			try
			{
				Path output = Path.of("build", "mesh-export-check", "empty-sidebar.png");
				Files.createDirectories(output.getParent());
				ImageIO.write(image, "png", output.toFile());
			}
			catch (Exception ex) { throw new RuntimeException(ex); }
		});
	}

	private static void layoutTree(Container container)
	{
		container.doLayout();
		for (Component child : container.getComponents())
			if (child instanceof Container nested) layoutTree(nested);
	}

	private static JButton button(Container root, String text)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton button && text.equals(button.getText())) return button;
			if (child instanceof Container nested)
			{
				JButton found = buttonOrNull(nested, text);
				if (found != null) return found;
			}
		}
		throw new AssertionError("Button not found: " + text);
	}

	private static JButton buttonOrNull(Container root, String text)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton button && text.equals(button.getText())) return button;
			if (child instanceof Container nested)
			{
				JButton found = buttonOrNull(nested, text);
				if (found != null) return found;
			}
		}
		return null;
	}
}
