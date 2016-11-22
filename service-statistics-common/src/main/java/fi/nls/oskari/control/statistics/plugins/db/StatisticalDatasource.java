package fi.nls.oskari.control.statistics.plugins.db;

import fi.nls.oskari.util.JSONHelper;
import fi.nls.oskari.util.PropertyUtil;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the value object for the statistical indicator data source definition rows in the database.
 * MyBatis Type for the SQL table oskari_statistical_datasource
 */
public class StatisticalDatasource {

    private long id;
    private String locale;
    private String config;
    private String plugin;
    private List<DatasourceLayer> layers = new ArrayList<>();

    public List<DatasourceLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<DatasourceLayer> layers) {
        this.layers = layers;
    }

    /**
     * The <plugin> in the plugin implementing class @Oskari("<name>") annotation.
     *
     * The plugin class must extend OskariComponent and implement StatisticalDatasourcePlugin interface
     * and be situated in the classpath.
     */
    public String getPlugin() {
        return plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLocale() {
        return locale;
    }

    private JSONObject localeJSON = null;

    public String getName(String lang) {
        if(localeJSON == null) {
            localeJSON =  JSONHelper.createJSONObject(locale);
        }
        if(localeJSON == null) {
            // nothing to use as name
            return null;
        }
        if(lang == null) {
            lang = PropertyUtil.getDefaultLanguage();
        }
        JSONObject langJson = localeJSON.optJSONObject(lang);
        if(langJson == null) {
            // try with default language
            langJson = localeJSON.optJSONObject(PropertyUtil.getDefaultLanguage());
        }
        if(langJson == null) {
            // nothing to use as name
            return null;
        }
        return langJson.optString("name");
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public JSONObject getConfigJSON() {
        return JSONHelper.createJSONObject(config);
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }
}
