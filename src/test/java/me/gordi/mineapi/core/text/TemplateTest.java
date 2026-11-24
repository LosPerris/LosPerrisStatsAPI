package me.gordi.mineapi.core.text;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplateTest {

    @Test
    void rendersVariablesInOrder() {
        Template t = Template.compile("Hola {player}, tienes {value} puntos", false);
        assertEquals("Hola Steve, tienes 10 puntos", t.render("player", "Steve", "value", 10));
        assertEquals(Set.of("player", "value"), t.variables());
    }

    @Test
    void missingValuesStayVisible() {
        assertEquals("x {player} y", Template.compile("x {player} y", false).render());
    }

    @Test
    void notVariablesAreLiteral() {
        Template t = Template.compile("{} {Mayus} {a b} {", false);
        assertEquals("{} {Mayus} {a b} {", t.render("a b", "nope"));
        assertTrue(t.variables().isEmpty());
    }

    @Test
    void colorsAreAppliedOnceAtCompile() {
        assertEquals("§aOK §b5", Template.compile("&aOK &b{n}").render("n", 5));
    }

    @Test
    void emptyAndNull() {
        assertEquals("", Template.compile(null).render());
        assertEquals("", Template.compile("").render("a", 1));
    }
}
