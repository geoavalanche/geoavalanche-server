package org.geoavalanche.wps.crowd;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import org.opengis.referencing.operation.MathTransform;

public class Crowd extends StaticMethodsProcessFactory<Crowd> {

    private static final Logger LOG = Logger.getLogger(Crowd.class.getName());

    public Crowd() {
        super(Text.text("GeoAvalanche"), "geoavalanche", Crowd.class);
    }

    @DescribeProcess(title = "Crowd", description = "Crowd")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection Crowd(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection") SimpleFeatureCollection featureCollection
    ) throws Exception {
        
        List<Incidents> incidents = getAllIncidents();
        
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
                try {
                    fb.set("incidents", getNumIncidentsOfGeometry(theGeometry));
                } catch (Exception e) {
                    fb.set("incidents", getNumIncidentsOfGeometry(incidents, theGeometry));
                }
            } 
            featuresList.add(fb.buildFeature(feature.getID()));
        }
        
        ListFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
        LOG.info("return=" + ret);
        return ret;
    }

    static long getNumIncidentsOfGeometry(List<Incidents> incidents, Geometry theGeometry3857) throws Exception {
        boolean lenient = true;
        MathTransform transform = CRS.findMathTransform(CRS.decode("EPSG:3857"), CRS.decode("EPSG:4326"), lenient);
        Geometry theGeometryWGS84 = JTS.transform(theGeometry3857, transform);
        
        GeometryFactory theGeometryFactory = new GeometryFactory(theGeometryWGS84.getPrecisionModel(), theGeometryWGS84.getSRID());            
        long _incidents = 0;
        for (Incidents entry: incidents) {
            Coordinate theCoordinate = new Coordinate(entry.incident.getLongitude(), entry.incident.getLatitude());
            Point thePoint = theGeometryFactory.createPoint(theCoordinate);
            if (theGeometryWGS84.contains(thePoint)) {
                _incidents++;
            }
        }        
        return _incidents;
    }

    static long getNumIncidentsOfGeometry(Geometry theGeometry3857) throws Exception {
        long _incidents = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        boolean lenient = true;
        MathTransform transform = CRS.findMathTransform(CRS.decode("EPSG:3857"), CRS.decode("EPSG:4326"), lenient);
        Geometry theGeometryWGS84 = JTS.transform(theGeometry3857, transform);

        GeometryJSON gjson = new GeometryJSON();
        StringWriter writer = new StringWriter();
        gjson.write(theGeometryWGS84, writer);
        String json = writer.toString();
        
        String mongourl = "mongodb://localhost:27017";
        MongoClient mongoClient = new MongoClient(new MongoClientURI(mongourl));
        MongoDatabase db = mongoClient.getDatabase("test");
        org.bson.Document theDocument = org.bson.Document.parse(json);        
        MongoCursor<org.bson.Document> cur = db.getCollection("incidents").find(geoWithin("loc", theDocument)).iterator();
        while(cur.hasNext()) {
            org.bson.Document doc = cur.next();
            org.bson.Document incident = (org.bson.Document)doc.get("incident");
            Date incidentdate = sdf.parse(incident.getString("incidentdate"));
            _incidents++;
        }
        mongoClient.close(); 
        return _incidents;
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
        return incidents;
    }
}
