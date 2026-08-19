#!/bin/bash
# Boot the server and drive a few commands through its console, then stop it cleanly.
#
# What each command proves:
#   config                -> the TOML shim round-trips and every key is listed
#   summon general_darius -> entity types registered, and the entity ticks without throwing
#   setblock brain node   -> block + block entity registered, and the brain's own tick loop runs
#   execute in limbo      -> the custom dimension exists and is loadable
cd "$(dirname "$0")" || exit 1
rm -f logs/latest.log smoke.log

{
    until grep -q "Done (" logs/latest.log 2>/dev/null; do sleep 2; done
    sleep 3
    echo "thevillagefightsback config"
    sleep 3
    echo "summon thevillagefightsback:general_darius 8 -60 8"
    sleep 2
    echo "setblock 4 -60 4 thevillagefightsback:village_brain_node"
    sleep 2
    echo "execute in thevillagefightsback:limbo run forceload add 0 0"
    sleep 2
    echo "thevillagefightsback brain_stage_get"
    sleep 10
    echo "stop"
} | "C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot/bin/java.exe" -Xmx3G -jar fabric-server-launch.jar --nogui > smoke.log 2>&1
