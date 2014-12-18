package fi.nls.oskari.transport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jackson.map.ObjectMapper;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.AbstractService;
import org.cometd.server.JacksonJSONContextServer;
import org.cometd.server.JettyJSONContextServer;

import com.vividsolutions.jts.geom.Coordinate;

import fi.nls.oskari.cache.JedisManager;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.pojo.GeoJSONFilter;
import fi.nls.oskari.pojo.Grid;
import fi.nls.oskari.pojo.Layer;
import fi.nls.oskari.pojo.Location;
import fi.nls.oskari.pojo.PropertyFilter;
import fi.nls.oskari.pojo.SessionStore;
import fi.nls.oskari.pojo.Tile;
import fi.nls.oskari.pojo.WFSCustomStyleStore;
import fi.nls.oskari.pojo.WFSLayerPermissionsStore;
import fi.nls.oskari.util.ConversionHelper;
import fi.nls.oskari.util.PropertyUtil;
import fi.nls.oskari.utils.GeometryJSONOutputModule;
import fi.nls.oskari.wfs.CachingSchemaLocator;
import fi.nls.oskari.wfs.WFSImage;
import fi.nls.oskari.wfs.pojo.WFSLayerStore;
import fi.nls.oskari.wfs.util.HttpHelper;
import fi.nls.oskari.work.OWSMapLayerJob;
import fi.nls.oskari.work.ResultProcessor;
import fi.nls.oskari.work.WFSCustomParserMapLayerJob;
import fi.nls.oskari.work.WFSMapLayerJob;
import fi.nls.oskari.work.fe.FEMapLayerJob;
import fi.nls.oskari.worker.Job;
import fi.nls.oskari.worker.JobQueue;





/**
 * Handles all incoming requests (channels) and manages Job queues
 * 
 * @see org.cometd.server.AbstractService
 */
public class TransportService extends AbstractService implements ResultProcessor {
    static {
        // populate properties before initializing logger since logger
        // implementation is configured in properties
        PropertyUtil.loadProperties("/oskari.properties");
        PropertyUtil.loadProperties("/transport.properties");
        PropertyUtil.loadProperties("/oskari-ext.properties");
        PropertyUtil.loadProperties("/transport-ext.properties");
    }
    private static Logger log = LogFactory.getLogger(TransportService.class);

    public static ObjectMapper mapper = new ObjectMapper();

	// params
	public static final String PARAM_ID = "id"; // skipped param - coming from cometd
    public static final String PARAM_UUID = "uuid"; //
	public static final String PARAM_CHANNEL = "channel"; // skipped param - coming from cometd
	public static final String PARAM_DATA = "data"; // own json data under this
	public static final String PARAM_SESSION = "session";
    public static final String PARAM_ROUTE = "route";
	public static final String PARAM_LANGUAGE = "language";
	public static final String PARAM_CLIENT = "client";
	public static final String PARAM_BROWSER = "browser";
	public static final String PARAM_BROWSER_VERSION = "browserVersion";
	public static final String PARAM_LOCATION = "location";
	public static final String PARAM_LOCATION_SRS = "srs";
	public static final String PARAM_LOCATION_BBOX = "bbox";
	public static final String PARAM_LOCATION_ZOOM = "zoom";
	public static final String PARAM_GRID = "grid";
    public static final String PARAM_TILES = "tiles";
	public static final String PARAM_ROWS = "rows";
	public static final String PARAM_COLUMNS = "columns";
	public static final String PARAM_BOUNDS = "bounds";
	public static final String PARAM_TILE_SIZE = "tileSize";
	public static final String PARAM_MAP_SIZE = "mapSize";
	public static final String PARAM_WIDTH = "width";
	public static final String PARAM_HEIGHT = "height";
	public static final String PARAM_MAP_SCALES = "mapScales";
	public static final String PARAM_LAYERS = "layers";
	public static final String PARAM_LAYER_ID = "layerId";
	public static final String PARAM_LAYER_STYLE = "styleName";
	public static final String PARAM_LONGITUDE = "longitude";
	public static final String PARAM_LATITUDE = "latitude";
	public static final String PARAM_LAYER_VISIBLE = "visible";
	public static final String PARAM_FEATURE_IDS = "featureIds";
	public static final String PARAM_KEEP_PREVIOUS = "keepPrevious";
    public static final String PARAM_GEOM_REQUEST = "geomRequest";

    // custom style params
    public static final String PARAM_FILL_COLOR = "fill_color";
    public static final String PARAM_FILL_PATTERN = "fill_pattern";
    public static final String PARAM_BORDER_COLOR = "border_color";
    public static final String PARAM_BORDER_LINEJOIN = "border_linejoin";
    public static final String PARAM_BORDER_DASHARRAY = "border_dasharray";
    public static final String PARAM_BORDER_WIDTH = "border_width";

    public static final String PARAM_STROKE_LINECAP = "stroke_linecap";
    public static final String PARAM_STROKE_COLOR = "stroke_color";
    public static final String PARAM_STROKE_LINEJOIN = "stroke_linejoin";
    public static final String PARAM_STROKE_DASHARRAY = "stroke_dasharray";
    public static final String PARAM_STROKE_WIDTH = "stroke_width";

    public static final String PARAM_DOT_COLOR = "dot_color";
    public static final String PARAM_DOT_SHAPE = "dot_shape";
    public static final String PARAM_DOT_SIZE = "dot_size";

	public static final String CHANNEL_INIT = "/service/wfs/init";
	public static final String CHANNEL_ADD_MAP_LAYER = "/service/wfs/addMapLayer";
	public static final String CHANNEL_REMOVE_MAP_LAYER = "/service/wfs/removeMapLayer";
	public static final String CHANNEL_SET_LOCATION = "/service/wfs/setLocation";
	public static final String CHANNEL_SET_MAP_SIZE = "/service/wfs/setMapSize";
	public static final String CHANNEL_SET_MAP_LAYER_STYLE = "/service/wfs/setMapLayerStyle";
    public static final String CHANNEL_SET_MAP_LAYER_CUSTOM_STYLE = "/service/wfs/setMapLayerCustomStyle";
	public static final String CHANNEL_SET_MAP_CLICK = "/service/wfs/setMapClick";
	public static final String CHANNEL_SET_FILTER = "/service/wfs/setFilter";
    public static final String CHANNEL_SET_PROPERTY_FILTER = "/service/wfs/setPropertyFilter";
	public static final String CHANNEL_SET_MAP_LAYER_VISIBILITY = "/service/wfs/setMapLayerVisibility";
	public static final String CHANNEL_HIGHLIGHT_FEATURES = "/service/wfs/highlightFeatures";

	public static final String CHANNEL_DISCONNECT = "/meta/disconnect";



    // action user uid API
    private static final String UID_API = "GetCurrentUser";
    private static final String KEY_UID = "currentUserUid";

	// server transport info
	private BayeuxServer bayeux;
	private ServerSession local;
	
	// JobQueue singleton
	private JobQueue jobs;

	/**
	 * Constructs TransportService with BayeuxServer instance
	 * 
	 * Hooks all channels to processRequest() and creates singletons for JobQueue and JedisManager. 
	 * Also initializes Jedis client for this thread.
	 * 
	 * @param bayeux
	 */
    public TransportService(BayeuxServer bayeux)
    {
        super(bayeux, "transport");
        
        Object jsonContext = bayeux.getOption("jsonContext");
        
        if( jsonContext instanceof JettyJSONContextServer ) {
            
        } else if( jsonContext instanceof JacksonJSONContextServer ) {
            ObjectMapper transportMapper =  ((JacksonJSONContextServer) jsonContext).getObjectMapper();
            transportMapper.registerModule(new GeometryJSONOutputModule());
            
        }

        int workerCount = ConversionHelper.getInt(PropertyUtil
                .get("workerCount"), 10);

        log.debug("Transport STARTED with worker count", workerCount);

        this.bayeux = bayeux;
        this.local = getServerSession();
        this.jobs = new JobQueue(workerCount);

        // init jedis
        JedisManager.connect(workerCount + 2,
                PropertyUtil.get("redis.hostname"),
                PropertyUtil.getOptional("redis.port", 6379));

        CachingSchemaLocator.init(); // init schemas

        addService(CHANNEL_DISCONNECT, "disconnect");
        addService(CHANNEL_INIT, "processRequest");
        addService(CHANNEL_ADD_MAP_LAYER, "processRequest");
        addService(CHANNEL_REMOVE_MAP_LAYER, "processRequest");
        addService(CHANNEL_SET_LOCATION, "processRequest");
        addService(CHANNEL_SET_MAP_SIZE, "processRequest");
        addService(CHANNEL_SET_MAP_LAYER_STYLE, "processRequest");
        addService(CHANNEL_SET_MAP_LAYER_CUSTOM_STYLE, "processRequest");
        addService(CHANNEL_SET_MAP_CLICK, "processRequest");
        addService(CHANNEL_SET_FILTER, "processRequest");
        addService(CHANNEL_SET_PROPERTY_FILTER, "processRequest");
        addService(CHANNEL_SET_MAP_LAYER_VISIBILITY, "processRequest");
        addService(CHANNEL_HIGHLIGHT_FEATURES, "processRequest");
    }

    /**
     * Removes Sessions and releases Jedis
     * 
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
    	// clear Sessions
    	JedisManager.delAll(SessionStore.KEY);
    	super.finalize();
        log.debug("DESTROYED");
    }

    /**
     * Call through implementation of ResultProcessor
     * @param clientId
     * @param channel
     * @param data
     */
    public void addResults(final String clientId, final String channel, final Object data) {
        send(clientId, channel, data);
    }
    /**
     * Sends data to certain client on a given channel
     * 
     * @param clientId
     * @param channel
     * @param data
     */
    public void send(String clientId, String channel, Object data) {
        ServerSession client = this.bayeux.getSession(clientId);
        client.deliver(local, channel, data, null);
    }

    /**
     * Tries to get session from cache with given key or creates a new
     * SessionStore
     * 
     * @param client
     * @return session object
     */
    public SessionStore getStore(String client) {
        String json = SessionStore.getCache(client);
        if (json == null) {
            log.debug("Created a new session for user (" + client + ")");
            return new SessionStore(client);
        }
        SessionStore store = null;
        try {
            store = SessionStore.setJSON(json);
        } catch (IOException e) {
            log.error(e, "JSON parsing failed for SessionStore \n" + json);
        }
        if (store == null) {
            return new SessionStore(client);
        }
        return store;
    }

    /**
     * Reset session
     */
    private void save(SessionStore store) {
        if (!store.save()) {
            this.send(store.getClient(), CHANNEL_RESET, "reset");
        }
    }

    /**
     * Removes client's session
     * 
     * @param client
     * @param message
     */

    public void disconnect(ServerSession client, Message message)
    {
        String json = SessionStore.getCache(client.getId());
        if(json != null) {
            SessionStore store;
            try {
                store = SessionStore.setJSON(json);
                JedisManager.del(WFSLayerPermissionsStore.KEY + store.getSession());
            } catch (IOException e) {
                log.error(e, "JSON parsing failed for SessionStore \n" + json);
            }
        }
        JedisManager.del(SessionStore.KEY + client.getId());
        JedisManager.delAll(WFSCustomStyleStore.KEY + client.getId());

        // TODO: remove styles from map
        
    	log.debug("Session & permission deleted: " + client);
    }

    /**
     * Preprocesses every service channel
     * 
     * Gets parameters and session and gives processing to a channel specific
     * method.
     * 
     * @param client
     * @param message
     */
    public void processRequest(ServerSession client, Message message)
    {
        log.debug("Serving client:", client.getId());
    	Map<String, Object> output = new HashMap<String, Object>();
    	Map<String, Object> params = message.getDataAsMap();
    	String json = message.getJSON();
    	
        if(params == null) {
            log.warn("Request failed because parameters were not set");
            output.put("once", false);
            output.put("message", "parameters_not_set");
            client.deliver(local, CHANNEL_ERROR, output, null);
            return;
        }

        // get session
        SessionStore store = getStore(client.getId());

        // channel processing
        String channel = message.getChannel();
        log.debug("Processing request on channel:", channel, "- payload:", json);
        if (channel.equals(CHANNEL_INIT)) {
            processInit(client, store, json);
        } else if (channel.equals(CHANNEL_ADD_MAP_LAYER)) {
            addMapLayer(store, params);
        } else if (channel.equals(CHANNEL_REMOVE_MAP_LAYER)) {
            removeMapLayer(store, params);
        } else if (channel.equals(CHANNEL_HIGHLIGHT_FEATURES)) {
            highlightMapLayerFeatures(store, params);
        } else if (channel.equals(CHANNEL_SET_LOCATION)) {
            setLocation(store, params);
        } else if (channel.equals(CHANNEL_SET_MAP_SIZE)) {
            setMapSize(store, params);
        } else if (channel.equals(CHANNEL_SET_MAP_LAYER_STYLE)) {
            setMapLayerStyle(store, params);
        } else if (channel.equals(CHANNEL_SET_MAP_LAYER_CUSTOM_STYLE)) {
            setMapLayerCustomStyle(store, params);
        } else if (channel.equals(CHANNEL_SET_MAP_CLICK)) {
            setMapClick(store, params);
        } else if (channel.equals(CHANNEL_SET_FILTER)) {
            setFilter(store, json);
        } else if (channel.equals(CHANNEL_SET_PROPERTY_FILTER)) {
            setPropertyFilter(store, json);
        } else if (channel.equals(CHANNEL_SET_MAP_LAYER_VISIBILITY)) {
            setMapLayerVisibility(store, params);
        }
    }

    /**
     * Parses init's json for session and adds jobs for the selected layers
     * 
     * @param client
     * @param store
     * @param json
     */
    public void processInit(ServerSession client, SessionStore store,
            String json) {
        try {
            store = SessionStore.setParamsJSON(json);
        } catch (IOException e) {
            log.error(e, "Session creation failed");
        }
        store.setClient(client.getId());
        store.setUuid(getOskariUid(store));
        this.save(store);

        // layers
        Map<String, Layer> layers = store.getLayers();
        for (Layer layer : layers.values()) {
            layer.setTiles(store.getGrid().getBounds()); // init bounds to tiles (render all)
        	initMapLayerJob(store, layer.getId());
        }
    }

    /**
     * Adds map layer to session and adds a job for the layer
     * 
     * @param store
     * @param layer
     */
    private void addMapLayer(SessionStore store, Map<String, Object> layer) {
        if (!layer.containsKey(PARAM_LAYER_ID)
                || !layer.containsKey(PARAM_LAYER_STYLE)) {
            log.warn("Failed to add a map layer");

    		return;
    	}

    	String layerId = layer.get(PARAM_LAYER_ID).toString();
    	String layerStyle = (String)layer.get(PARAM_LAYER_STYLE);
    	
    	if(!store.containsLayer(layerId)) {
            Layer tmpLayer = new Layer(layerId, layerStyle);
    		store.setLayer(layerId, tmpLayer);
        	this.save(store);
    	}

    }

    /**
     * Starts a new job for given layer
     * 
     * @param store
     * @param layerId
     */
    private void initMapLayerJob(SessionStore store, String layerId) {
        Job job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.NORMAL, store, layerId);
        jobs.remove(job);
        jobs.add(job);
    }

    private String getOskariUid(SessionStore store) {
        String sessionId = store.getSession();
        String route = store.getRoute();
        log.warn( WFSMapLayerJob.getAPIUrl(sessionId) + UID_API);
        String cookies = null;
        if(route != null && !route.equals("")) {
            cookies = WFSMapLayerJob.ROUTE_COOKIE_NAME + route;
        }
        return HttpHelper.getHeaderValue(WFSMapLayerJob.getAPIUrl(sessionId) + UID_API, cookies, KEY_UID);
    }
    /**
     * Removes map layer from session and jobs
     * 
     * @param store
     * @param layer
     */
    private void removeMapLayer(SessionStore store, Map<String, Object> layer) {
        if (!layer.containsKey(PARAM_LAYER_ID)) {
            log.warn("Failed to remove a map layer");
            return;
        }
        // Layer id may have prefix
        String layerId = layer.get(PARAM_LAYER_ID).toString(); //(Long) layer.get(PARAM_LAYER_ID);
        if (store.containsLayer(layerId)) {
            // first remove from jobs then from store
            Job job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.NORMAL, store, layerId);
            jobs.remove(job);

            store.removeLayer(layerId);
            this.save(store);
        }
    }

    /**
     * Sets location into session and starts jobs for selected layers with given
     * location
     * 
     * @param store
     * @param location
     */

	private void setLocation(SessionStore store, Map<String, Object> location) {
    	if (location == null ||
                !location.containsKey(PARAM_LAYER_ID) ||
                !location.containsKey(PARAM_LOCATION_SRS) ||
    			!location.containsKey(PARAM_LOCATION_BBOX) ||
    			!location.containsKey(PARAM_LOCATION_ZOOM) ||
    			!location.containsKey(PARAM_GRID) ||
                !location.containsKey(PARAM_TILES)) {
            log.warn("Failed to set location");
    		return;
    	}

    	Object tmpbbox_fld = location.get(PARAM_LOCATION_BBOX);
    	Object[] tmpbbox = tmpbbox_fld  instanceof List ? ((List)tmpbbox_fld).toArray() : (Object[]) tmpbbox_fld;
    	List<Double> bbox = new ArrayList<Double>();
    	for(Object obj : tmpbbox) {
    		if(obj instanceof Double) {
    			bbox.add((Double) obj);
    		} else {
    			bbox.add(((Number) obj).doubleValue());
    		}
    	}
    	
    	Location mapLocation = new Location();
    	mapLocation.setSrs((String)location.get(PARAM_LOCATION_SRS));
    	mapLocation.setBbox(bbox);
    	
    	mapLocation.setZoom(((Number)location.get(PARAM_LOCATION_ZOOM)).longValue());
    	store.setLocation(mapLocation);
    	

    	Grid grid = parseGrid(location);
    	store.setGrid(grid);
    	
    	this.save(store);

        String layerId = location.get(PARAM_LAYER_ID).toString();
        
        Object tmptiles_fld = location.get(PARAM_TILES);
        Object[] tmptiles = tmptiles_fld instanceof List ? ((List) tmptiles_fld).toArray() : (Object[])tmptiles_fld;
        List<List<Double>> tiles = parseBounds(tmptiles);

        Layer layer = store.getLayers().get(layerId);
        if(layer.isVisible()) {
            layer.setTiles(tiles); // selected tiles to render
            Job job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.NORMAL, store, layerId);
            jobs.remove(job);
            jobs.add(job);
        }
    }

    /**
     * Sets map size into session and starts jobs for selected layers with given
     * map size if got bigger
     * 
     * @param store
     * @param mapSize
     */
    private void setMapSize(SessionStore store, Map<String, Object> mapSize) {
        if (mapSize == null || !mapSize.containsKey(PARAM_WIDTH)
                || !mapSize.containsKey(PARAM_HEIGHT)) {
            log.warn("Failed to set map size");
            return;
        }

        Tile newMapSize = new Tile();
        newMapSize.setWidth(((Number) mapSize.get(PARAM_WIDTH)).intValue());
        newMapSize.setHeight(((Number) mapSize.get(PARAM_HEIGHT)).intValue());
        store.setMapSize(newMapSize);

        this.save(store);
    }

    /**
     * Sets layer style into session and starts job for the layer
     * 
     * @param store
     * @param layer
     */
    private void setMapLayerStyle(SessionStore store, Map<String, Object> layer) {
    	if(!layer.containsKey(PARAM_LAYER_ID) || !layer.containsKey(PARAM_LAYER_STYLE)) {
            log.warn("Failed to set map layer style");
    		return;
    	}

    	String layerId = layer.get(PARAM_LAYER_ID).toString();
    	String layerStyle = (String)layer.get(PARAM_LAYER_STYLE);
    	
    	if(store.containsLayer(layerId)) {
            Layer tmpLayer = store.getLayers().get(layerId);

            if(!tmpLayer.getStyleName().equals(layerStyle) || layerStyle.startsWith(WFSImage.PREFIX_CUSTOM_STYLE)) {
                tmpLayer.setStyleName(layerStyle);
                this.save(store);
                if(tmpLayer.isVisible()) {
                    tmpLayer.setTiles(store.getGrid().getBounds()); // init bounds to tiles (render all)
                    Job job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.NORMAL, store, layerId, false, true, false); // no features
                    jobs.remove(job);
                    jobs.add(job);
                }
            }
    	}
    }

    /**
     * Sets layer style into session and starts job for the layer
     *
     * @param store
     * @param style
     */
    private void setMapLayerCustomStyle(SessionStore store, Map<String, Object> style) {
        if(!style.containsKey(PARAM_LAYER_ID) ||
                !style.containsKey(PARAM_FILL_COLOR) ||
                !style.containsKey(PARAM_FILL_PATTERN) ||
                !style.containsKey(PARAM_BORDER_COLOR) ||
                !style.containsKey(PARAM_BORDER_LINEJOIN) ||
                !style.containsKey(PARAM_BORDER_DASHARRAY) ||
                !style.containsKey(PARAM_BORDER_WIDTH) ||

                !style.containsKey(PARAM_STROKE_LINECAP) ||
                !style.containsKey(PARAM_STROKE_COLOR) ||
                !style.containsKey(PARAM_STROKE_LINEJOIN) ||
                !style.containsKey(PARAM_STROKE_DASHARRAY) ||
                !style.containsKey(PARAM_STROKE_WIDTH) ||

                !style.containsKey(PARAM_DOT_COLOR) ||
                !style.containsKey(PARAM_DOT_SHAPE) ||
                !style.containsKey(PARAM_DOT_SIZE)) {
            log.warn("Failed to set map layer custom style");
            return;
        }

        String layerId = style.get(PARAM_LAYER_ID).toString();

        WFSCustomStyleStore customStyle = new WFSCustomStyleStore();

        customStyle.setLayerId(layerId);
        customStyle.setClient(store.getClient());

        customStyle.setFillColor(style.get(PARAM_FILL_COLOR).toString());
        customStyle.setFillPattern(((Number)style.get(PARAM_FILL_PATTERN)).intValue());
        customStyle.setBorderColor(style.get(PARAM_BORDER_COLOR).toString());
        customStyle.setBorderLinejoin(style.get(PARAM_BORDER_LINEJOIN).toString());
        customStyle.setBorderDasharray(style.get(PARAM_BORDER_DASHARRAY).toString());
        customStyle.setBorderWidth(((Number)style.get(PARAM_BORDER_WIDTH)).intValue());

        customStyle.setStrokeLinecap(style.get(PARAM_STROKE_LINECAP).toString());
        customStyle.setStrokeColor(style.get(PARAM_STROKE_COLOR).toString());
        customStyle.setStrokeLinejoin(style.get(PARAM_STROKE_LINEJOIN).toString());
        customStyle.setStrokeDasharray(style.get(PARAM_STROKE_DASHARRAY).toString());
        customStyle.setStrokeWidth(((Number)style.get(PARAM_STROKE_WIDTH)).intValue());

        customStyle.setDotColor(style.get(PARAM_DOT_COLOR).toString());
        customStyle.setDotShape(((Number)style.get(PARAM_DOT_SHAPE)).intValue());
        customStyle.setDotSize(((Number)style.get(PARAM_DOT_SIZE)).intValue());

        customStyle.save();
    }

    /**
     * Click isn't saved in session. Set click will be request just once.
     * 
     * Sends only feature json.
     * 
     * @param store
     * @param point
     */
    private void setMapClick(SessionStore store, Map<String, Object> point) {
        if (!point.containsKey(PARAM_LONGITUDE)
                || !point.containsKey(PARAM_LATITUDE)
                || !point.containsKey(PARAM_KEEP_PREVIOUS)){
            log.warn("Failed to set a map click", point);
            return;
        }

        double longitude;
        double latitude;
        boolean keepPrevious;

        if (point.get(PARAM_LONGITUDE) instanceof Double) {
            longitude = (Double) point.get(PARAM_LONGITUDE);
        } else {
            longitude = ((Number) point.get(PARAM_LONGITUDE)).doubleValue();
        }
        if (point.get(PARAM_LATITUDE) instanceof Double) {
            latitude = (Double) point.get(PARAM_LATITUDE);
        } else {
            latitude = ((Number) point.get(PARAM_LATITUDE)).doubleValue();
        }

        keepPrevious = (Boolean) point.get(PARAM_KEEP_PREVIOUS);

        // stores click, but doesn't save
        store.setMapClick(new Coordinate(longitude, latitude));
        store.setKeepPrevious(keepPrevious);

        Job job = null;
        for (Entry<String, Layer> e : store.getLayers().entrySet()) {
            if (e.getValue().isVisible()) {
                // job without image drawing
                job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.MAP_CLICK, store, e.getValue().getId(), true, false, false);
                jobs.remove(job);
                jobs.add(job);
            }
        }
    }

    /**
     * Filter isn't saved in session. Set filter will be request just once.
     * 
     * Sends only feature json.
     * 
     * @param store
     * @param json
     */
    private void setFilter(SessionStore store, String json) {
        GeoJSONFilter filter = GeoJSONFilter.setParamsJSON(json);

        // stores geojson, but doesn't save
        store.setFilter(filter);

        Job job = null;
        for (Entry<String, Layer> e : store.getLayers().entrySet()) {
            if (e.getValue().isVisible()) {
                // job without image drawing
                job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.GEOJSON, store, e.getValue().getId(), true, false, false);
                jobs.remove(job);
                jobs.add(job);
            }
        }
    }
    /**
     * Property filter isn't saved in session. Set filter will be request just once.
     *
     * Sends only feature json.
     *
     * @param store
     * @param json
     */
    private void setPropertyFilter(SessionStore store, String json) {
        PropertyFilter propertyFilter = PropertyFilter.setParamsJSON(json);
        // stores property filters, but doesn't save
        store.setPropertyFilter(propertyFilter);

        Job job = null;
        for (Entry<String, Layer> e : store.getLayers().entrySet()) {
            if (e.getValue().isVisible()) {
                // job without image drawing
                // only for requested layer
                if (e.getValue().getId().equals(propertyFilter.getLayerId())) {
                    job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.PROPERTY_FILTER, store, e.getValue().getId(), true, false, false);
                    jobs.remove(job);
                    jobs.add(job);
                }
            }
        }
    }
    /**
     * Sets layer visibility into session and starts/stops job for the layer
     * 
     * @param store
     * @param layer
     */
    private void setMapLayerVisibility(SessionStore store,
            Map<String, Object> layer) {
        if (!layer.containsKey(PARAM_LAYER_ID)
                || !layer.containsKey(PARAM_LAYER_VISIBLE)) {
            log.warn("Layer style not defined");
    		return;
    	}

    	String layerId = layer.get(PARAM_LAYER_ID).toString();
    	boolean layerVisible = (Boolean)layer.get(PARAM_LAYER_VISIBLE);
    	
    	if(store.containsLayer(layerId)) {
    		Layer tmpLayer = store.getLayers().get(layerId);
    		if(tmpLayer.isVisible() != layerVisible) { // only if changed
	    		tmpLayer.setVisible(layerVisible);
	    		this.save(store);
	    		if(layerVisible) {
                    tmpLayer.setTiles(store.getGrid().getBounds()); // init bounds to tiles (render all)
		    		Job job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.NORMAL, store, layerId);
		        	jobs.remove(job);
		        	jobs.add(job);
	    		}
    		}
    	}
    }

    /**
     * FeatureIds aren't stored in session. Sets highlighted features
     * 
     * Sends only image json.
     * 
     * @param store
     * @param layer
     */
    private void highlightMapLayerFeatures(SessionStore store,
            Map<String, Object> layer) {
        if (!layer.containsKey(PARAM_LAYER_ID)
                || !layer.containsKey(PARAM_FEATURE_IDS)
                || !layer.containsKey(PARAM_KEEP_PREVIOUS)
                || !layer.containsKey(PARAM_GEOM_REQUEST)) {
            log.warn("Layer features not defined");
    		return;
    	}

    	String layerId = layer.get(PARAM_LAYER_ID).toString();
    	List<String> featureIds = new ArrayList<String>();
    	boolean keepPrevious;
        boolean geomRequest;
    	
        Object tmpfids_fld = layer.get(PARAM_FEATURE_IDS);
    	Object[] tmpfids = tmpfids_fld instanceof List ? ((List) tmpfids_fld).toArray() : (Object[])tmpfids_fld;
    	for(Object obj : tmpfids) {
			featureIds.add((String) obj);
    	}
    	
    	keepPrevious = (Boolean)layer.get(PARAM_KEEP_PREVIOUS);
    	store.setKeepPrevious(keepPrevious);
        geomRequest = (Boolean) layer.get(PARAM_GEOM_REQUEST);
        store.setGeomRequest(geomRequest);
    	
    	if(store.containsLayer(layerId)) {
    		store.getLayers().get(layerId).setHighlightedFeatureIds(featureIds);
    		if(store.getLayers().get(layerId).isVisible()) {
            	// job without feature sending
    			Job job = createOWSMapLayerJob(this, OWSMapLayerJob.Type.HIGHLIGHT, store, layerId, false, true, true);
	        	jobs.remove(job);
	        	jobs.add(job);
    		}
    	}
    }

    /**
     * Helper for creating Grid from
     * 
     * @param params
     */
    @SuppressWarnings("unchecked")
	private Grid parseGrid(Map<String, Object> params) {
    	Grid grid = new Grid();
    	
    	Map<String, Object> tmpgrid = (Map<String, Object>) params.get(PARAM_GRID);
    	Object tmpbounds_fld = tmpgrid.get(PARAM_BOUNDS);
    	Object[] tmpbounds = tmpbounds_fld instanceof List ? ((List) tmpbounds_fld).toArray(): (Object[])tmpbounds_fld;
    	List<List<Double>> bounds = parseBounds(tmpbounds);

    	grid.setRows(((Number)tmpgrid.get(PARAM_ROWS)).intValue());
    	grid.setColumns(((Number)tmpgrid.get(PARAM_COLUMNS)).intValue());
    	grid.setBounds(bounds);
    	
    	return grid;
    }

    private List<List<Double>> parseBounds(Object[] params) {
        if(params == null) {
            return null;
        }

        List<List<Double>> bounds = new ArrayList<List<Double>>();
        List<Double> tile = null;

        for(Object obj : params) {
            
            if(obj instanceof Object[] || obj instanceof List) {
                tile = new ArrayList<Double>();
                
                Object[] objs = obj instanceof List ? ((List) obj).toArray():(Object[])obj;                
                for(Object bound : objs ) {
                    if(bound instanceof Double) {
                        tile.add((Double)bound);
                    } else {
                        tile.add(((Number)bound).doubleValue());
                    }
                }
                bounds.add(tile);
            }
        }

        return bounds;
    }

    /**
     * Creates a new runnable job with own Jedis instance
     * 
     * Parameters define client's service (communication channel), session and
     * layer's id. Sends all resources that the layer configuration allows.
     * 
     * @param service
     * @param store
     * @param layerId
     * @return
     */
    public Job createOWSMapLayerJob(ResultProcessor service, OWSMapLayerJob.Type type,
            SessionStore store, String layerId) {
        
        WFSLayerStore layer = OWSMapLayerJob.getLayerConfiguration(layerId, store.getSession(), store.getRoute());

        if ("oskari-feature-engine".equals(layer.getJobType())) {
            return new FEMapLayerJob(service, type, store, layerId);
        }
        else if("oskari-custom-parser".equals(layer.getJobType())) {
            new WFSCustomParserMapLayerJob(service, type, store, layerId);
        }
        return new WFSMapLayerJob(service, type, store, layerId);
    }

    /**
     * Creates a new runnable job with own Jedis instance
     * 
     * Parameters define client's service (communication channel), session and
     * layer's id. Also sets resources that will be sent if the layer
     * configuration allows.
     * 
     * @param service
     * @param store
     * @param layerId
     * @param reqSendFeatures
     * @param reqSendImage
     * @param reqSendHighlight
     */
    public Job createOWSMapLayerJob(ResultProcessor service, OWSMapLayerJob.Type type,
            SessionStore store, String layerId, boolean reqSendFeatures,
            boolean reqSendImage, boolean reqSendHighlight) {
        
        WFSLayerStore layer = OWSMapLayerJob.getLayerConfiguration(layerId, store.getSession(), store.getRoute());

        if ("oskari-feature-engine".equals(layer.getJobType()) ) {
            return new FEMapLayerJob(service, type, store, layerId,
                    reqSendFeatures, reqSendImage, reqSendHighlight);
        }
        else if("oskari-custom-parser".equals(layer.getJobType())) {
            new WFSCustomParserMapLayerJob(service, type, store, layerId,
                    reqSendFeatures, reqSendImage, reqSendHighlight);
        }
        return new WFSMapLayerJob(service, type, store, layerId,
                    reqSendFeatures, reqSendImage, reqSendHighlight);
    }

    private static final String JOB_TYPE_FEATURE_ENGINE = "oskari-feature-engine";
    private static final String JOB_TYPE_CUSTOM_PARSER = "oskari-custom-parser";

}
