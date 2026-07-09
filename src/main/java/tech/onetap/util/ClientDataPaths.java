package tech.onetap.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ClientDataPaths {
    private static final String ROOT_DIRECTORY = "C:\\Space";
    private static final String CONFIGS_DIRECTORY = "configs";

    public static final Path ROOT = Paths.get(ROOT_DIRECTORY);
    public static final Path CONFIGS = ROOT.resolve(CONFIGS_DIRECTORY);

    private ClientDataPaths() {
    }

    public static Path path(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ROOT;
        }
        return ROOT.resolve(relativePath);
    }

    public static File file(String relativePath) {
        return path(relativePath).toFile();
    }

    public static File rootDirectory() {
        return ROOT.toFile();
    }

    public static File configsDirectory() {
        return CONFIGS.toFile();
    }
}
