package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/** Commands are being built. */
public class RegisterCommandsEvent extends Event {
    private final CommandDispatcher<CommandSourceStack> dispatcher;
    private final CommandBuildContext buildContext;

    public RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandBuildContext buildContext) {
        this.dispatcher = dispatcher;
        this.buildContext = buildContext;
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.dispatcher;
    }

    public CommandBuildContext getBuildContext() {
        return this.buildContext;
    }
}
