package com.xeon.plugins.meshexport;

import com.xeon.view3d.CachedMeshFixtures;
import com.xeon.view3d.MeshPreviewPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OSMAPVIEWER_TEST_CACHE", matches = ".+")
class MeshCacheSmokeTest
{
	@Test void compactsBroadCachedNpcSample() throws Exception
	{
		var fixtures = CachedMeshFixtures.loadNpcSample(Path.of(System.getenv("OSMAPVIEWER_TEST_CACHE")), 100);
		assertEquals(100, fixtures.size());
		for (var fixture : fixtures)
		{
			MeshCompactor.Result result = assertDoesNotThrow(() -> MeshCompactorTest.compact(fixture.mesh()), fixture.name());
			assertTrue(result.mesh().faceCount() > 0, fixture.name());
		}
	}

	@Test void compactsCachedModelsAndRendersPreview() throws Exception
	{
		var fixtures = CachedMeshFixtures.load(Path.of(System.getenv("OSMAPVIEWER_TEST_CACHE")));
		assertTrue(fixtures.size() >= 6);
		Path output = Path.of("build", "mesh-export-check");
		Files.createDirectories(output);
		for (var fixture : fixtures)
		{
			MeshCompactor.Result result = MeshCompactorTest.compact(fixture.mesh());
			assertTrue(result.mesh().faceCount() > 0, fixture.name());
			System.out.println(fixture.name() + ": " + fixture.mesh().faceCount() + " -> " + result.mesh().faceCount()
				+ "; " + result.diagnostics());
			BufferedImage image = new BufferedImage(600, 340, BufferedImage.TYPE_INT_RGB);
			SwingUtilities.invokeAndWait(() -> {
				MeshPreviewPanel preview = new MeshPreviewPanel();
				preview.setSize(300, 340);
				Graphics2D graphics = image.createGraphics();
				try
				{
					preview.setMesh(fixture.mesh());
					preview.paint(graphics);
					graphics.translate(300, 0);
					preview.setMesh(result.mesh());
					preview.paint(graphics);
				}
				finally { graphics.dispose(); }
			});
			int colored = 0;
			for (int y = 5; y < image.getHeight() - 5; y++) for (int x = 305; x < image.getWidth() - 5; x++)
				if ((image.getRGB(x, y) & 0xFFFFFF) != 0) colored++;
			assertTrue(colored > 100, fixture.name() + " has a blank preview");
			String name = MeshExportIO.fileName(fixture.name(), 0);
			ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
			MeshExportIO.save(output.resolve(name + ".stl"), MeshExportIO.Format.STL, result.mesh(), 100, true);
		}
	}
}
