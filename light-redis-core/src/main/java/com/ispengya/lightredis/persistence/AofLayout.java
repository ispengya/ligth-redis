package com.ispengya.lightredis.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOF 数据目录布局。
 *
 * <p>数据目录本身可以保持旧布局，重写后的 generation 放在
 * {@code generations/} 下，并通过 {@code CURRENT} 文件指向当前目录。</p>
 */
public final class AofLayout {

    /**
     * 当前 generation 指针文件名。
     */
    private static final String CURRENT_FILE = "CURRENT";

    /**
     * generation 目录名。
     */
    private static final String GENERATIONS_DIR = "generations";

    /**
     * 同一进程内的 generation 序号，避免同一毫秒重名。
     */
    private static final AtomicLong GENERATION_SEQUENCE = new AtomicLong();

    /**
     * 数据根目录。
     */
    private final Path rootDir;

    /**
     * 当前 generation 指针文件。
     */
    private final Path currentFile;

    /**
     * 当前活跃的 mini-file-storage 目录。
     */
    private final Path activeDir;

    private AofLayout(Path rootDir, Path currentFile, Path activeDir) {
        this.rootDir = rootDir;
        this.currentFile = currentFile;
        this.activeDir = activeDir;
    }

    /**
     * 打开并解析 AOF 目录布局。
     *
     * <p>没有 CURRENT 文件时，目录本身作为活跃 generation，
     * 兼容此前直接写在 dataDir 下的数据。</p>
     *
     * @param dataDir 数据根目录
     * @return 目录布局
     */
    public static AofLayout open(String dataDir) {
        if (dataDir == null || dataDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Data dir must not be empty");
        }
        Path root = Paths.get(dataDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new PersistenceException("Failed to create AOF root " + root, e);
        }

        Path currentFile = root.resolve(CURRENT_FILE);
        Path activeDir = root;
        if (Files.isRegularFile(currentFile)) {
            String relative = readCurrent(currentFile);
            if (relative == null || relative.isEmpty()) {
                throw new PersistenceException("AOF CURRENT file is empty: " + currentFile);
            }
            Path candidate = root.resolve(relative).normalize();
            if (!candidate.startsWith(root) || candidate.equals(root) || !Files.isDirectory(candidate)) {
                throw new PersistenceException("Invalid AOF generation in CURRENT: " + relative);
            }
            activeDir = candidate;
        }
        return new AofLayout(root, currentFile, activeDir);
    }

    /**
     * 创建一个新的 generation 目录。
     *
     * @return 新目录
     */
    public Path createGeneration() {
        Path generations = rootDir.resolve(GENERATIONS_DIR);
        try {
            Files.createDirectories(generations);
            String name = "gen-" + System.currentTimeMillis() + "-"
                    + GENERATION_SEQUENCE.incrementAndGet();
            Path dir = generations.resolve(name);
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new PersistenceException("Failed to create AOF generation under " + rootDir, e);
        }
    }

    /**
     * 原子更新 CURRENT 指针。
     *
     * @param generationDir 新 generation 目录
     */
    public void updateCurrent(Path generationDir) {
        Path target = generationDir.toAbsolutePath().normalize();
        if (!target.startsWith(rootDir) || target.equals(rootDir)) {
            throw new PersistenceException("Generation must be under AOF root: " + target);
        }
        String relative = rootDir.relativize(target).toString().replace('\\', '/');
        Path temp = null;
        try {
            temp = Files.createTempFile(rootDir, ".CURRENT-", ".tmp");
            Files.write(temp, (relative + "\n").getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, currentFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, currentFile, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException e) {
            throw new PersistenceException("Failed to update AOF CURRENT file " + currentFile, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 清理临时文件失败不影响主流程
                }
            }
        }
    }

    /**
     * 返回数据根目录。
     *
     * @return 根目录
     */
    public Path getRootDir() {
        return rootDir;
    }

    /**
     * 返回当前活跃目录。
     *
     * @return 活跃 generation 目录
     */
    public Path getActiveDir() {
        return activeDir;
    }

    /**
     * 返回 CURRENT 文件路径。
     *
     * @return CURRENT 文件
     */
    public Path getCurrentFile() {
        return currentFile;
    }

    private static String readCurrent(Path currentFile) {
        try {
            List<String> lines = Files.readAllLines(currentFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line != null && !line.trim().isEmpty()) {
                    return line.trim();
                }
            }
            return null;
        } catch (IOException e) {
            throw new PersistenceException("Failed to read AOF CURRENT file " + currentFile, e);
        }
    }
}
