package me.gordi.mineapi.module.web.routes;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.api.WebApi;
import me.gordi.mineapi.module.web.DashboardService;
import me.gordi.mineapi.module.web.http.Http;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** {@link WebApi.Request} sobre un HttpExchange (endpoints de addons). */
final class ApiRequest implements WebApi.Request {

    private final MineAPI plugin;
    private final HttpExchange t;
    private final boolean pub;
    private final DashboardService adminLog;
    private Map<String, String> query;
    private JsonObject body = new JsonObject();

    ApiRequest(MineAPI plugin, HttpExchange t, boolean pub, DashboardService adminLog) {
        this.plugin = plugin;
        this.t = t;
        this.pub = pub;
        this.adminLog = adminLog;
    }

    ApiRequest body(JsonObject b) {
        if (b != null) body = b;
        return this;
    }

    @Override
    public Map<String, String> query() {
        if (query == null) query = Http.query(t);
        return query;
    }

    @Override
    public int intParam(String key, int def, int min, int max) {
        return Http.intParam(query(), key, def, min, max);
    }

    @Override
    public JsonObject body() {
        return body;
    }

    @Override
    public String ip() {
        return Http.remoteIp(t);
    }

    @Override
    public boolean isPublic() {
        return pub;
    }

    @Override
    public <T> T onMainThread(Callable<T> task) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(plugin, task).get(10, TimeUnit.SECONDS);
    }

    @Override
    public void log(String action, String detail, boolean ok) {
        if (adminLog != null) adminLog.logAdmin(ip(), action, detail, ok);
    }
}
