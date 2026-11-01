package com.xinyv.median;

/** Exports the actual production payload for the JavaScript behavior test. */
public final class ConnectionHintsScriptDump {
    public static void main(String[] args) {
        System.out.print(ConnectionHintsScript.collectScript());
    }
}
