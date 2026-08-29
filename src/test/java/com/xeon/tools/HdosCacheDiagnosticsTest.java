package com.xeon.tools;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HdosCacheDiagnosticsTest
{
    @Test
    void hdosCacheCanBeInspectedWithDispleeOnly() throws Exception
    {
        Path cacheDirectory = configuredHdosCache();
        Assumptions.assumeTrue(Files.isDirectory(cacheDirectory), "HDOS cache not found: " + cacheDirectory);

        HdosCacheDiagnostics.Result result = HdosCacheDiagnostics.run(
            new HdosCacheDiagnostics.Options(
                cacheDirectory,
                HdosCacheDiagnostics.DEFAULT_REGION_ID,
                1,
                8),
            System.out);

        assertTrue(result.validIndexCount() > 0, "No valid Displee indexes were loaded");
        assertTrue(result.requiredIndexesPresent(), "A required viewer index is missing");
        assertTrue(result.regionMapPresent(), "Sample region terrain archive is missing");
        assertTrue(result.regionLocationsPresent(), "Sample region location archive is missing");
        assertTrue(result.loadableForDiagnostics(), "HDOS cache was not loadable enough for diagnostics");
    }

    private static Path configuredHdosCache()
    {
        String property = System.getProperty("osmapviewer.hdosCache");
        if (property != null && !property.isBlank())
        {
            return Path.of(property);
        }

        String environment = System.getenv("OSMAPVIEWER_HDOS_CACHE");
        if (environment != null && !environment.isBlank())
        {
            return Path.of(environment);
        }

        return HdosCacheDiagnostics.DEFAULT_HDOS_CACHE;
    }
}
