package io.github.rizoma.excel;

import io.github.rizoma.core.EngineException;
import io.github.rizoma.core.PathTabularSource;
import io.github.rizoma.core.TabularSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;

final class XlsxPackageHandle implements AutoCloseable {
    private final OPCPackage packageFile;
    private final Path temporary;
    private boolean closed;

    private XlsxPackageHandle(OPCPackage packageFile, Path temporary) {
        this.packageFile = packageFile;
        this.temporary = temporary;
    }

    static XlsxPackageHandle open(TabularSource source, long maxBytes) {
        Path temporary = null;
        try {
            if (source instanceof PathTabularSource local) {
                return new XlsxPackageHandle(OPCPackage.open(local.path().toFile(), PackageAccess.READ), null);
            }
            temporary = Files.createTempFile("rizoma-xlsx-", ".tmp");
            long copied = 0;
            byte[] buffer = new byte[8192];
            try (var input = source.openStream(); var output = Files.newOutputStream(temporary)) {
                for (int read; (read = input.read(buffer)) >= 0;) {
                    if (read == 0) continue;
                    copied += read;
                    if (copied > maxBytes) {
                        throw new EngineException("SOURCE_TOO_LARGE", "source exceeds configured byte limit");
                    }
                    output.write(buffer, 0, read);
                }
            }
            return new XlsxPackageHandle(OPCPackage.open(temporary.toFile(), PackageAccess.READ), temporary);
        } catch (Exception exception) {
            deleteQuietly(temporary);
            if (exception instanceof EngineException engineException) throw engineException;
            throw new EngineException("XLSX_OPEN_FAILED", "XLSX package could not be opened", exception);
        }
    }

    OPCPackage packageFile() { return packageFile; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        Exception failure = null;
        try { packageFile.close(); } catch (Exception exception) { failure = exception; }
        try { if (temporary != null) Files.deleteIfExists(temporary); }
        catch (Exception exception) { if (failure == null) failure = exception; }
        if (failure != null) {
            throw new EngineException("XLSX_CLOSE_FAILED", "XLSX package or temporary file could not be closed", failure);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
