package me.gordi.mineapi.core.module;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ModuleManagerTest {

    static final List<String> LOG = new ArrayList<>();

    static class Fake implements Module {
        final String id;
        final List<String> deps;
        boolean enabled, failOnEnable;
        String problem;
        int reloads;

        Fake(String id, String... deps) {
            this.id = id;
            this.deps = List.of(deps);
        }

        public String id() { return id; }
        public List<String> dependencies() { return deps; }
        public void enable() throws Exception {
            if (failOnEnable) throw new IllegalStateException("boom");
            enabled = true;
            LOG.add("+" + id);
        }
        public void disable() {
            if (enabled) LOG.add("-" + id);
            enabled = false;
        }
        public void reload() { reloads++; }
        public boolean isEnabled() { return enabled; }
        public String problem() { return problem; }
    }

    private static Logger quiet() {
        Logger l = Logger.getAnonymousLogger();
        l.setUseParentHandlers(false);
        return l;
    }

    @Test
    void enablesInOrderAndDisablesInReverse() {
        LOG.clear();
        ModuleManager mm = new ModuleManager(quiet(), id -> true);
        Fake core = new Fake("stats"), web = new Fake("web", "stats");
        mm.register(core);
        mm.register(web);
        mm.enableAll();
        assertEquals(List.of("+stats", "+web"), LOG);
        assertEquals(List.of("stats", "web"), mm.enabledIds());
        mm.disableAll();
        assertEquals(List.of("+stats", "+web", "-web", "-stats"), LOG);
    }

    @Test
    void disabledFlagCostsNothingAndDependentsFollow() {
        ModuleManager mm = new ModuleManager(quiet(), id -> !id.equals("stats"));
        Fake core = new Fake("stats"), web = new Fake("web", "stats");
        mm.register(core);
        mm.register(web);
        mm.enableAll();
        assertFalse(core.enabled);
        assertFalse(web.enabled);
        assertTrue(mm.enabledIds().isEmpty());
    }

    @Test
    void failingModuleIsIsolated() {
        ModuleManager mm = new ModuleManager(quiet(), id -> true);
        Fake core = new Fake("stats"), web = new Fake("web", "stats"), afk = new Fake("afk");
        core.failOnEnable = true;
        mm.register(core);
        mm.register(web);
        mm.register(afk);
        mm.enableAll();
        assertFalse(core.enabled);
        assertFalse(web.enabled, "no arranca si su dependencia falló");
        assertTrue(afk.enabled);
    }

    @Test
    void reloadAppliesFlags() {
        Set<String> off = new HashSet<>();
        ModuleManager mm = new ModuleManager(quiet(), id -> !off.contains(id));
        Fake a = new Fake("a"), b = new Fake("b");
        mm.register(a);
        mm.register(b);
        mm.enableAll();
        off.add("b");
        mm.reloadAll();
        assertTrue(a.enabled);
        assertEquals(1, a.reloads);
        assertFalse(b.enabled);
        off.clear();
        mm.reloadAll();
        assertTrue(b.enabled);
        assertEquals(0, b.reloads);
    }

    @Test
    void rejectsDuplicatesAndUnknownDependencies() {
        ModuleManager mm = new ModuleManager(quiet(), id -> true);
        mm.register(new Fake("a"));
        assertThrows(IllegalArgumentException.class, () -> mm.register(new Fake("a")));
        assertThrows(IllegalArgumentException.class, () -> mm.register(new Fake("x", "missing")));
    }

    @Test
    void syncTogglesOnlyWhatChangedAndCascades() {
        LOG.clear();
        Set<String> on = new HashSet<>(List.of("stats", "web", "afk"));
        ModuleManager mm = new ModuleManager(quiet(), on::contains);
        Fake stats = new Fake("stats"), web = new Fake("web", "stats"), afk = new Fake("afk");
        mm.register(stats);
        mm.register(web);
        mm.register(afk);
        mm.enableAll();
        LOG.clear();

        on.remove("stats");
        ModuleManager.Changes c = mm.sync();
        assertEquals(List.of("web", "stats"), c.disabled(), "dependientes primero");
        assertTrue(c.enabled().isEmpty());
        assertEquals(0, afk.reloads, "sync no recarga los demás");
        assertEquals(ModuleManager.Status.OFF, mm.status("stats"));
        assertEquals(ModuleManager.Status.BROKEN, mm.status("web"), "web sigue pedido en config");
        assertEquals(new ModuleManager.Problem("needs", "stats"), mm.problem("web"));
        assertEquals(List.of("web"), mm.dependents("stats"));

        on.add("stats");
        c = mm.sync();
        assertEquals(List.of("stats", "web"), c.enabled());
        assertEquals(ModuleManager.Status.ON, mm.status("web"));
        assertNull(mm.problem("web"));
        assertTrue(mm.sync().isEmpty(), "idempotente");
    }

    @Test
    void reportsEnableErrorsAndModuleProblems() {
        ModuleManager mm = new ModuleManager(quiet(), id -> !id.equals("off"));
        Fake bad = new Fake("bad"), half = new Fake("half"), off = new Fake("off"), dep = new Fake("dep", "off");
        bad.failOnEnable = true;
        half.problem = "no-papi";
        mm.register(bad);
        mm.register(half);
        mm.register(off);
        mm.register(dep);
        mm.enableAll();
        assertEquals(ModuleManager.Status.BROKEN, mm.status("bad"));
        assertEquals(new ModuleManager.Problem("error", "boom"), mm.problem("bad"));
        assertEquals(ModuleManager.Status.BROKEN, mm.status("half"));
        assertEquals("no-papi", mm.problem("half").key());
        assertEquals(ModuleManager.Status.OFF, mm.status("off"));
        assertNull(mm.problem("off"));
        assertEquals(List.of("off"), mm.missingDependencies("dep"));

        bad.failOnEnable = false;
        ModuleManager.Changes c = mm.sync();
        assertEquals(List.of("bad"), c.enabled(), "un módulo que falló se reintenta");
        assertEquals(ModuleManager.Status.ON, mm.status("bad"));
    }

    @Test
    void addonModulesJoinAtRuntimeAndLeaveWithTheAddon() {
        LOG.clear();
        Set<String> on = new HashSet<>(Set.of("stats", "quests", "seasons"));
        ModuleManager mm = new ModuleManager(quiet(), on::contains);
        Fake stats = new Fake("stats");
        mm.register(stats);
        mm.enableAll();
        Fake quests = new Fake("quests", "stats"), titles = new Fake("titles"), seasons = new Fake("seasons", "quests");
        assertEquals(List.of("quests", "seasons"), mm.add(List.of(quests, titles, seasons)));
        assertSame(quests, mm.byId("quests"));
        assertFalse(titles.isEnabled(), "apagado en config.yml");
        LOG.clear();
        mm.remove(List.of("quests", "titles", "seasons"));
        assertEquals(List.of("-seasons", "-quests"), LOG, "se apagan en orden inverso");
        assertNull(mm.byId("quests"));
        assertEquals(List.of("stats"), mm.enabledIds());
        assertTrue(stats.isEnabled());
    }

    @Test
    void addonModuleWaitsForAnOffCoreDependency() {
        ModuleManager mm = new ModuleManager(quiet(), id -> !id.equals("discord-webhook"));
        mm.register(new Fake("discord-webhook"));
        mm.enableAll();
        assertEquals(List.of(), mm.add(List.of(new Fake("alerts", "discord-webhook"))));
        assertEquals(ModuleManager.Status.BROKEN, mm.status("alerts"));
        assertEquals("needs", mm.problem("alerts").key());
    }
}
