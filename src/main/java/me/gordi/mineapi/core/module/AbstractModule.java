package me.gordi.mineapi.core.module;

import me.gordi.mineapi.MineAPI;

/** Módulo del núcleo: {@link BaseModule} con el plugin principal. Core module base. */
public abstract class AbstractModule extends BaseModule<MineAPI> {

    protected AbstractModule(MineAPI plugin, String id, String... dependencies) {
        super(plugin, id, dependencies);
    }
}
