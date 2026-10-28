package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.protocol.GlobPattern;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * KEYS 命令执行器。
 *
 * <p>按 glob 模式匹配 key，结果按字典序返回，多个 key 之间以换行分隔。</p>
 */
public class KeysCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for KEYS";
        }
        Pattern pattern = GlobPattern.compile(args.get(0));
        List<String> matched = new ArrayList<>();
        for (String key : store.keys()) {
            if (pattern.matcher(key).matches()) {
                matched.add(key);
            }
        }
        Collections.sort(matched);
        return String.join("\n", matched);
    }
}
