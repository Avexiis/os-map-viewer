package com.xeon.plugins.meshexport;

import com.xeon.view3d.Map3DMesh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class MeshExportIOTest
{
	@TempDir Path directory;

	@Test void namesUseIdFallbackAndArePortable()
	{
		assertEquals("42", MeshExportIO.fileName(null, 42));
		assertEquals("42", MeshExportIO.fileName(" null ", 42));
		assertEquals("A_B_C_", MeshExportIO.fileName("A/B:C?", 42));
		assertEquals("_CON", MeshExportIO.fileName("CON", 42));
		assertEquals("Goblin", MeshExportIO.fileName("Goblin", 42));
		assertEquals(Path.of("test.obj"), MeshExportIO.withExtension(Path.of("test.stl"), MeshExportIO.Format.OBJ));
	}

	@Test void writesLittleEndianStlAtRequestedScaleWithOutwardNormals() throws Exception
	{
		Map3DMesh mesh = MeshCompactorTest.cube(7, 8, 9, 2);
		Path path = directory.resolve("cube.stl");
		MeshExportIO.save(path, MeshExportIO.Format.STL, mesh, 40, false);
		byte[] bytes = Files.readAllBytes(path);
		assertEquals(84 + 50 * 12, bytes.length);
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals(12, buffer.getInt(80));
		Map3DMesh output = MeshExportIO.forExport(mesh, 40);
		assertEquals(64000, MeshCompactorTest.volume(output), 1e-8);
		for (int f = 0; f < 12; f++)
		{
			buffer.position(84 + f * 50);
			double nx = buffer.getFloat(), ny = buffer.getFloat(), nz = buffer.getFloat();
			assertEquals(1, nx * nx + ny * ny + nz * nz, 1e-6);
			for (int c = 0; c < 3; c++) for (int axis = 0; axis < 3; axis++)
				assertEquals(output.coordinate(output.vertexIndex(f, c), axis), buffer.getFloat(), 1e-5);
			assertEquals(0, buffer.getShort());
		}
		assertEquals(0, java.util.stream.IntStream.range(0, output.vertexCount()).mapToDouble(v -> output.coordinate(v, 2)).min().orElseThrow());
	}

	@Test void objUsesIndexedFacesAndLocaleIndependentNumbers() throws Exception
	{
		Locale previous = Locale.getDefault();
		Locale.setDefault(Locale.GERMANY);
		try
		{
			Path path = directory.resolve("cube.obj");
			MeshExportIO.save(path, MeshExportIO.Format.OBJ, MeshCompactorTest.cube(0, 0, 0, 1), 40.5, false);
			String text = Files.readString(path);
			assertEquals(8, text.lines().filter(line -> line.startsWith("v ")).count());
			assertEquals(12, text.lines().filter(line -> line.startsWith("f ")).count());
			assertTrue(text.contains("-20.25"));
			assertFalse(text.contains(","));
		}
		finally { Locale.setDefault(previous); }
	}

	@Test void cancellationAndFailedOverwriteLeaveDestinationIntact() throws Exception
	{
		Path path = directory.resolve("existing.stl");
		Files.writeString(path, "existing");
		assertThrows(FileAlreadyExistsException.class, () -> MeshExportIO.save(path, MeshExportIO.Format.STL,
			MeshCompactorTest.cube(0, 0, 0, 1), 100, false));
		assertEquals("existing", Files.readString(path));
		Thread.currentThread().interrupt();
		try { assertThrows(CancellationException.class, () -> MeshExportIO.save(path, MeshExportIO.Format.STL,
			MeshCompactorTest.cube(0, 0, 0, 1), 100, true)); }
		finally { Thread.interrupted(); }
		assertEquals("existing", Files.readString(path));
		try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
	}
}
