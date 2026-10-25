package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

public interface CommandExecutor {

    String execute(Command command, KeyValueStore store);
}
