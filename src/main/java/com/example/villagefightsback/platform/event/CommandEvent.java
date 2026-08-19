package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;

/** A command has been parsed and is about to run. Cancelling stops it. */
public class CommandEvent extends Event {
    private ParseResults<CommandSourceStack> parseResults;

    public CommandEvent(ParseResults<CommandSourceStack> parseResults) {
        this.parseResults = parseResults;
    }

    public ParseResults<CommandSourceStack> getParseResults() {
        return this.parseResults;
    }

    public void setParseResults(ParseResults<CommandSourceStack> parseResults) {
        this.parseResults = parseResults;
    }
}
