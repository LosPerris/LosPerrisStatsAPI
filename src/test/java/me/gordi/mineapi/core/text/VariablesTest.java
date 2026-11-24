package me.gordi.mineapi.core.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class VariablesTest {

    @Test
    void everyContextWithSyntaxHasVariables() {
        for (VariableContext ctx : new VariableContext[]{VariableContext.LANG, VariableContext.MILESTONES,
                VariableContext.REWARDS, VariableContext.DISCORD, VariableContext.SCOREBOARD, VariableContext.PLACEHOLDERAPI}) {
            assertFalse(Variables.of(ctx).isEmpty(), ctx.id());
        }
    }

    @Test
    void findsUnknownBraces() {
        assertEquals(Set.of("{nope}"), Variables.findUnknown(VariableContext.LANG, "{player} {nope} {value}"));
        assertTrue(Variables.findUnknown(VariableContext.LANG, "sin variables").isEmpty());
    }

    @Test
    void findsUnknownPercent() {
        assertTrue(Variables.findUnknown(VariableContext.MILESTONES, "%player%: %message%").isEmpty());
        assertEquals(Set.of("%nada%"), Variables.findUnknown(VariableContext.MILESTONES, "%player% %nada%"));
    }

    @Test
    void validatorWarnsOncePerUnknownAndHonoursIgnore() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<String> warnings = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        VariableValidator v = new VariableValidator(logger, "config.yml");
        v.check(VariableContext.SCOREBOARD, "scoreboard.lines", "%player_name% %vault_eco_balance% %oops%",
                t -> t.startsWith("%vault_"));
        assertEquals(1, v.warnings());
        assertTrue(warnings.get(0).contains("%oops%"));
        assertTrue(warnings.get(0).contains("variables/scoreboard.md"));
    }
}
