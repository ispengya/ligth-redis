package com.ispengya.lightredis.bootstrap;

import com.ispengya.lightredis.persistence.PersistenceConfig;

import java.io.PrintStream;
import java.util.Locale;

/**
 * 服务端启动参数。
 */
public final class ServerOptions {

    /**
     * AOF 数据目录。
     */
    private String dataDir = PersistenceConfig.DEFAULT_DATA_DIR;

    /**
     * 是否开启 AOF 持久化。
     */
    private boolean appendOnly = true;

    /**
     * 是否只打印帮助。
     */
    private boolean help;

    private ServerOptions() {
    }

    /**
     * 解析启动参数。
     *
     * @param args 原始参数
     * @return 解析结果
     * @throws IllegalArgumentException 参数非法
     */
    public static ServerOptions parse(String[] args) {
        ServerOptions options = new ServerOptions();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                options.help = true;
                continue;
            }
            if ("--data-dir".equals(arg)) {
                options.dataDir = requireValue(args, ++i, arg);
                continue;
            }
            if (arg.startsWith("--data-dir=")) {
                options.dataDir = arg.substring("--data-dir=".length());
                continue;
            }
            if ("--appendonly".equals(arg)) {
                options.appendOnly = parseBoolean(requireValue(args, ++i, arg), arg);
                continue;
            }
            if (arg.startsWith("--appendonly=")) {
                options.appendOnly = parseBoolean(arg.substring("--appendonly=".length()), arg);
                continue;
            }
            throw new IllegalArgumentException("Unknown option: " + arg);
        }
        if (options.dataDir == null || options.dataDir.trim().isEmpty()) {
            throw new IllegalArgumentException("--data-dir must not be empty");
        }
        options.dataDir = options.dataDir.trim();
        return options;
    }

    /**
     * 打印帮助信息。
     *
     * @param out 输出流
     */
    public static void printUsage(PrintStream out) {
        out.println("Usage: light-redis-server [options]");
        out.println("  --data-dir <path>       AOF data directory (default: "
                + PersistenceConfig.DEFAULT_DATA_DIR + ")");
        out.println("  --appendonly <yes|no>   Enable command log persistence (default: yes)");
        out.println("  -h, --help              Show this help");
    }

    /**
     * 返回数据目录。
     *
     * @return 数据目录
     */
    public String getDataDir() {
        return dataDir;
    }

    /**
     * 返回是否开启持久化。
     *
     * @return 开启时返回 true
     */
    public boolean isAppendOnly() {
        return appendOnly;
    }

    /**
     * 返回是否只打印帮助。
     *
     * @return 帮助模式返回 true
     */
    public boolean isHelp() {
        return help;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        String value = args[index];
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return value.trim();
    }

    private static boolean parseBoolean(String value, String option) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("yes".equals(normalized) || "true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("no".equals(normalized) || "false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid value for " + option + ": " + value);
    }
}
