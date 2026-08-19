package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.CommandEvent;
import com.mojang.brigadier.ParseResults;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Forge's CommandEvent: a parsed command about to run, which the mod can call off. */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void tvfb$command(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        if (EventBus.post(new CommandEvent(parse)).isCanceled()) {
            ci.cancel();
        }
    }
}
