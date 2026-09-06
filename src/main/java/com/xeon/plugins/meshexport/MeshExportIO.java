package com.xeon.plugins.meshexport;

import com.xeon.view3d.Map3DMesh;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import static com.xeon.plugins.meshexport.MeshTopology.*;

final class MeshExportIO
{
	enum Format
	{
		STL("stl", "Binary STL (*.stl)"), OBJ("obj", "Wavefront OBJ (*.obj)");
		final String extension;
		final String description;
		Format(String extension, String description) { this.extension = extension; this.description = description; }
	}

	static String fileName(String name, int id)
	{
		String value = name == null ? "" : name.trim();
		if (value.isBlank() || value.equalsIgnoreCase("null")) value = Integer.toString(id);
		value = value.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_").replaceAll("^[. ]+|[. ]+$", "");
		if (value.isBlank()) value = Integer.toString(id);
		if (value.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) value = "_" + value;
		if (value.codePointCount(0, value.length()) > 100) value = value.substring(0, value.offsetByCodePoints(0, 100));
		return value;
	}

	static Path withExtension(Path path, Format format)
	{
		String name = path.getFileName().toString();
		if (name.toLowerCase(Locale.ROOT).endsWith("." + format.extension)) return path;
		name = name.replaceFirst("(?i)\\.(stl|obj)$", "");
		return path.resolveSibling(name + "." + format.extension);
	}

	static Map3DMesh forExport(Map3DMesh mesh, double sizeMillimeters)
	{
		if (!Double.isFinite(sizeMillimeters) || sizeMillimeters <= 0 || mesh.faceCount() == 0)
			throw new IllegalArgumentException("Export size and mesh must be nonzero");
		double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (int v = 0; v < mesh.vertexCount(); v++) for (int axis = 0; axis < 3; axis++)
		{
			min[axis] = Math.min(min[axis], mesh.coordinate(v, axis));
			max[axis] = Math.max(max[axis], mesh.coordinate(v, axis));
		}
		double extent = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
		if (!(extent > 0)) throw new IllegalArgumentException("Mesh has no size");
		double scale = sizeMillimeters / extent;
		double[] positions = new double[mesh.vertexCount() * 3];
		for (int v = 0; v < mesh.vertexCount(); v++)
		{
			// Rotate Y-up to Z-up with positive determinant, center XY, and put the base at Z=0.
			positions[v * 3] = (mesh.coordinate(v, 0) - (min[0] + max[0]) / 2) * scale;
			positions[v * 3 + 1] = -(mesh.coordinate(v, 2) - (min[2] + max[2]) / 2) * scale;
			positions[v * 3 + 2] = (mesh.coordinate(v, 1) - min[1]) * scale;
		}
		return new Map3DMesh(positions, mesh.triangles());
	}

	static void save(Path destination, Format format, Map3DMesh mesh, double size, boolean replace) throws IOException
	{
		Path target = destination.toAbsolutePath();
		Path temporary = Files.createTempFile(target.getParent(), ".mesh-export-", ".tmp");
		try
		{
			Map3DMesh output = forExport(mesh, size);
			if (format == Format.STL) writeStl(temporary, output);
			else writeObj(temporary, output);
			checkCancelled();
			if (replace)
			{
				try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
				catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
			}
			else Files.move(temporary, target);
		}
		finally { Files.deleteIfExists(temporary); }
	}

	private static void writeStl(Path path, Map3DMesh mesh) throws IOException
	{
		try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path)))
		{
			ByteBuffer header = ByteBuffer.allocate(84).order(ByteOrder.LITTLE_ENDIAN);
			header.put("OS Map Viewer - millimeters - Z up".getBytes(StandardCharsets.US_ASCII));
			header.position(80);
			header.putInt(mesh.faceCount());
			output.write(header.array());
			ByteBuffer triangle = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);
			for (int f = 0; f < mesh.faceCount(); f++)
			{
				checkCancelled();
				Vec a = position(mesh, mesh.vertexIndex(f, 0)), b = position(mesh, mesh.vertexIndex(f, 1)), c = position(mesh, mesh.vertexIndex(f, 2));
				Vec cross = b.minus(a).cross(c.minus(a));
				Vec normal = cross.lengthSquared() == 0 ? new Vec(0, 0, 0) : cross.normalized();
				triangle.clear();
				for (Vec point : new Vec[]{normal, a, b, c}) triangle.putFloat((float) point.x()).putFloat((float) point.y()).putFloat((float) point.z());
				triangle.putShort((short) 0);
				output.write(triangle.array());
			}
		}
	}

	private static void writeObj(Path path, Map3DMesh mesh) throws IOException
	{
		try (BufferedWriter output = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
		{
			output.write("# OS Map Viewer; coordinates in millimeters; Z up\no mesh\n");
			for (int v = 0; v < mesh.vertexCount(); v++)
			{
				checkCancelled();
				output.write("v " + mesh.coordinate(v, 0) + " " + mesh.coordinate(v, 1) + " " + mesh.coordinate(v, 2) + "\n");
			}
			for (int f = 0; f < mesh.faceCount(); f++)
			{
				checkCancelled();
				output.write("f " + (mesh.vertexIndex(f, 0) + 1) + " " + (mesh.vertexIndex(f, 1) + 1) + " " + (mesh.vertexIndex(f, 2) + 1) + "\n");
			}
		}
	}
	private static Vec position(Map3DMesh mesh, int vertex)
	{
		return new Vec(mesh.coordinate(vertex, 0), mesh.coordinate(vertex, 1), mesh.coordinate(vertex, 2));
	}
}
