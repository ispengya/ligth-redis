package com.ispengya.lightredis.protocol;

import java.util.regex.Pattern;

/**
 * Redis 风格的简单 glob 模式。
 *
 * <p>支持 {@code *}、{@code ?} 和反斜杠转义，与 KEYS 命令保持一致。</p>
 */
public final class GlobPattern {

    private GlobPattern() {
    }

    /**
     * 把 glob 编译为正则。
     *
     * @param glob glob 模式
     * @return 正则
     */
    public static Pattern compile(String glob) {
        if (glob == null) {
            throw new IllegalArgumentException("Glob must not be null");
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char current = glob.charAt(i);
            switch (current) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append('.');
                    break;
                case '\\':
                    if (i + 1 < glob.length()) {
                        regex.append(Pattern.quote(String.valueOf(glob.charAt(++i))));
                    } else {
                        regex.append("\\\\");
                    }
                    break;
                default:
                    regex.append(Pattern.quote(String.valueOf(current)));
                    break;
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }
}
