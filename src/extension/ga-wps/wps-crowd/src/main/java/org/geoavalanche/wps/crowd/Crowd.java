package org.geoavalanche.wps.crowd;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.geoWithin;
import com.ushahidi.java.sdk.UshahidiApi;
import com.ushahidi.java.sdk.api.Incidents;
import com.ushahidi.java.sdk.api.tasks.IncidentsTask;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.io.StringWriter;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTS;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.referencing.CRS;
import org.geotools.text.Text;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

public class Crowd extends StaticMethodsProcessFactory<Crowd> {

    private static final Logger LOG = Logger.getLogger(Crowd.class.getName());
    private static String mongoip = "localhost"; 
    private static int mongoport = 27017; 
    private static final String mongodb = "geoavalanche"; 
    private static Boolean isAvailableMongodb = null; 

    public Crowd() {
        super(Text.text("GeoAvalanche"), "geoavalanche", Crowd.class);
    }

    @DescribeProcess(title = "Crowd", description = "Crowd")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection Crowd(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection") SimpleFeatureCollection featureCollection,
            @DescribeParameter(name = "sourceCRS", description = "sourceCRS", min=0, max=1) CoordinateReferenceSystem sourceCRS            
    ) throws Exception {
        Map<String, String> env = System.getenv();
        for (String envName : env.keySet()) {
            LOG.info(envName+"="+env.get(envName));
        }

        try {
            mongoip=System.getenv().get("MONGOIP");
        } catch (Exception e) {
            LOG.severe("env MONGOIP is not set");
        }
        try {            
            mongoport=Integer.parseInt(System.getenv().get("MONGOPORT"));
        } catch (Exception e) {
            LOG.severe("env MONGOPORT is not set");
        }
        
        if (sourceCRS == null) {
            sourceCRS=CRS.decode("EPSG:3857");
        }
        if (isAvailableMongodb==null) {
            isAvailableMongodb = isAvailableMongodb();
        }
        LOG.info("isAvailableMongodb="+isAvailableMongodb);
        List<Incidents> incidents = null;
        if (!isAvailableMongodb) {
            incidents = getAllIncidents();
        }
        
        List<SimpleFeature> featuresList = new ArrayList();
        SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);
        
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {         
            SimpleFeature feature = itr.next();
            fb.reset();
            for (Property p: feature.getProperties()) {
               fb.set(p.getName().getLocalPart(), p.getValue());
            }
            Geometry theGeometry = (Geometry)feature.getAttribute("the_geom");
            if (theGeometry == null) {
                theGeometry = (Geometry)feature.getAttribute("geometry");
            }
            if (theGeometry != null) {
                if (!isAvailableMongodb) {
                    fb.set("incidents", getNumIncidentsOfGeometry(incidents, theGeometry, sourceCRS));
                } else {
                    fb.set("incidents", getNumIncidentsOfGeometry(theGeometry, sourceCRS));
                }
            } 
            featuresList.add(fb.buildFeature(feature.getID()));
        }
        
        ListFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
        LOG.info("nrec = " + ret.size());
        return ret;
    }

    static long[] getNumIncidentsOfGeometry(List<Incidents> incidents, Geometry theGeometry, CoordinateReferenceSystem sourceCRS) throws Exception {
        boolean lenient = true;
        Geometry theGeometry4326 = theGeometry;
        if (sourceCRS!=CRS.decode("EPSG:4326")) {
            MathTransform transform = CRS.findMathTransform(sourceCRS, CRS.decode("EPSG:4326"), lenient);
            theGeometry4326 = JTS.transform(theGeometry, transform);
        } 
        
        GeometryFactory theGeometryFactory = new GeometryFactory(theGeometry4326.getPrecisionModel(), theGeometry4326.getSRID());            
        long _incidents = 0;
        long _incidentsInLastWeek = 0;
        long _incidentsInLastMonth = 0;
        long _incidentsInLastYear = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (Incidents entry: incidents) {
            Coordinate theCoordinate = new Coordinate(entry.incident.getLongitude(), entry.incident.getLatitude());
            Point thePoint = theGeometryFactory.createPoint(theCoordinate);
            if (theGeometry4326.contains(thePoint)) {
                if (isIncidentInThisWeek(entry.incident.getDate())) {
                    _incidentsInLastWeek++;
                }
                if (isIncidentInThisMonth(entry.incident.getDate())) {
                    _incidentsInLastMonth++;
                }
                if (isIncidentInThisYear(entry.incident.getDate())) {
                    _incidentsInLastYear++;
                }                
                _incidents++;
            }
        }        
        long[] ret = {_incidents, _incidentsInLastYear, _incidentsInLastMonth, _incidentsInLastWeek};
        return ret;        
    }

    static long[] getNumIncidentsOfGeometry(Geometry theGeometry, CoordinateReferenceSystem sourceCRS) throws Exception {
        long _incidents = 0;
        long _incidentsInLastWeek = 0;
        long _incidentsInLastMonth = 0;
        long _incidentsInLastYear = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        Geometry theGeometry4326 = theGeometry;
        if (sourceCRS!=CRS.decode("EPSG:4326")) {
            boolean lenient = true;
            MathTransform transform = CRS.findMathTransform(sourceCRS, CRS.decode("EPSG:4326"), lenient);
            theGeometry4326 = JTS.transform(theGeometry, transform);
        }

        GeometryJSON gjson = new GeometryJSON();
        StringWriter writer = new StringWriter();
        gjson.write(theGeometry4326, writer);
        String json = writer.toString();
        
        MongoClient mongoClient = new MongoClient(new ServerAddress(mongoip, mongoport));
        MongoDatabase db = mongoClient.getDatabase(mongodb);
        org.bson.Document theDocument = org.bson.Document.parse(json);        
        MongoCursor<org.bson.Document> cur = db.getCollection("incidents").find(geoWithin("loc", theDocument)).iterator();
        while(cur.hasNext()) {
            org.bson.Document doc = cur.next();
            org.bson.Document incident = (org.bson.Document)doc.get("incident");
            Date incidentdate = sdf.parse(incident.getString("incidentdate"));
            if (isIncidentInThisWeek(incidentdate)) {
                _incidentsInLastWeek++;
            }
            if (isIncidentInThisMonth(incidentdate)) {
                _incidentsInLastMonth++;
            }
            if (isIncidentInThisYear(incidentdate)) {
                _incidentsInLastYear++;
            }
            _incidents++;
        }
        mongoClient.close();
        long[] ret = {_incidents, _incidentsInLastYear, _incidentsInLastMonth, _incidentsInLastWeek};
        return ret;
    }

    static boolean isIncidentInThisYear(java.util.Date incidentdate) {
        Calendar _calendar = Calendar.getInstance();
        _calendar.add(Calendar.YEAR, -1);
        return (incidentdate.getTime() > _calendar.getTime().getTime());
    }
    
    static boolean isIncidentInThisMonth(java.util.Date incidentdate) {
        Calendar _calendar = Calendar.getInstance();
        _calendar.add(Calendar.MONTH, -1);
        return (incidentdate.getTime() > _calendar.getTime().getTime());
    }
    
    static boolean isIncidentInThisWeek(java.util.Date incidentdate) {
        Calendar _calendar = Calendar.getInstance();
        _calendar.add(Calendar.DAY_OF_MONTH, -7);
        return (incidentdate.getTime() > _calendar.getTime().getTime());
    }    
    
    static boolean isAvailableMongodb() {
        MongoClient mongoClient = null;
        try {
            mongoClient = new MongoClient(new ServerAddress(mongoip, mongoport));
            MongoDatabase db = mongoClient.getDatabase(mongodb);
            long nrec = db.getCollection("incidents").count();
            return nrec > 0;
        } catch (Exception e) {
            LOG.severe(e.toString());
            return false;
        } finally {
            mongoClient.close();
        }
    }
    
    static SimpleFeatureBuilder getSimpleFeatureBuilder(SimpleFeatureCollection featureCollection) {
        Map<String, Class> theProperties = new HashMap();
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {         
            SimpleFeature feature = itr.next();
            for (Property p: feature.getProperties()) {
               theProperties.put(p.getName().getLocalPart(), p.getType().getBinding());
            }
        }
        theProperties.put("incidents", Long.class);

        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName(featureCollection.getSchema().getName());
        tb.setCRS(featureCollection.getSchema().getCoordinateReferenceSystem());
        for (Map.Entry<String, Class> entry : theProperties.entrySet()) {
            tb.add(entry.getKey(), entry.getValue());
        }
        SimpleFeatureType schema = tb.buildFeatureType();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(schema);        
        return fb;
    }
    
    static List<Incidents> getAllIncidents() {
        UshahidiApi ushahidi = new UshahidiApi("http://geoavalanche.org/incident");        
        IncidentsTask incidentstask = ushahidi.factory.createIncidentsTask();
        incidentstask.setLimit(100);
        incidentstask.setOrderField("incidentid");
        List<Incidents> incidents = incidentstask.all();
        if (incidents==null) {
            incidents = new ArrayList();
        }
        LOG.info("getAllIncidents() nrec="+incidents.size());
        return incidents;
    }
}
