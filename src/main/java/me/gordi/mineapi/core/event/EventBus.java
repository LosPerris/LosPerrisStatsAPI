package me.gordi.mineapi.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bus de eventos interno, síncrono y muy ligero (sin reflexión). Los módulos se comunican por
 * aquí sin depender unos de otros. Publicar un evento sin suscriptores no cuesta casi nada; use
 * {@link #hasSubscribers(Class)} para no crear el objeto si nadie escucha.
 */
public final class EventBus {

    private record Subscription(Object owner, Consumer<Object> handler) {
    }

    private final Logger logger;
    private final Map<Class<?>, List<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public EventBus(Logger logger) {
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    public <E> void subscribe(Object owner, Class<E> type, Consumer<? super E> handler) {
        subscriptions.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                .add(new Subscription(owner, (Consumer<Object>) handler));
    }

    public boolean hasSubscribers(Class<?> type) {
        List<Subscription> list = subscriptions.get(type);
        return list != null && !list.isEmpty();
    }

    /** Entrega el evento a los suscriptores de su clase exacta, en el hilo que llama. */
    public void post(Object event) {
        List<Subscription> list = subscriptions.get(event.getClass());
        if (list == null) return;
        for (Subscription s : list) {
            try {
                s.handler.accept(event);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error en un suscriptor de " + event.getClass().getSimpleName(), e);
            }
        }
    }

    /** Quita todas las suscripciones de un dueño (al desactivar un módulo). */
    public void unsubscribeAll(Object owner) {
        for (List<Subscription> list : subscriptions.values()) {
            list.removeIf(s -> s.owner == owner);
        }
    }
}
