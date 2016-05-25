package org.geoavalanche.wps.dangerindex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.Process;
import org.geotools.process.ProcessExecutor;
import org.geotools.process.Processors;
import org.geotools.process.Progress;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.geotools.util.KVP;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;


public class DangerIndex extends StaticMethodsProcessFactory<DangerIndex> {
    private static final Logger LOG = Logger.getLogger(DangerIndex.class.getName());

    public DangerIndex() {
        super(Text.text("GeoAvalanche"), "geoavalanche", DangerIndex.class);
    }
    
    @DescribeProcess(title = "DangerIndex", description = "DangerIndex")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection DangerIndex(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection") SimpleFeatureCollection featureCollection,
            @DescribeParameter(name = "distance", description = "distance") String distance,
            @DescribeParameter(name = "quadrantSegments", description = "quadrantSegments") String quadrantSegments,
            @DescribeParameter(name = "capStyle", description = "capStyle") String capStyle
            ) throws Exception {
        
        List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
                
        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName(featureCollection.getSchema().getName());
        tb.setCRS(featureCollection.getSchema().getCoordinateReferenceSystem());
        tb.add("THE_GEOM", Polygon.class);
        tb.add("DANGERINDEX", String.class);
        SimpleFeatureType targetSchema = tb.buildFeatureType();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(targetSchema);        
        
        LOG.info("input="+featureCollection);
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {
            SimpleFeature feature = itr.next();
            if (feature.getDefaultGeometry() instanceof LineString) {
                LineString theGeometry = (LineString)feature.getDefaultGeometry();
                for (int x=1; x<theGeometry.getCoordinates().length; x++ ) {
                    Coordinate[] points = new Coordinate[] { theGeometry.getCoordinates()[x-1], theGeometry.getCoordinates()[x] };
                    LineString newLineString = new LineString(points, theGeometry.getPrecisionModel(), theGeometry.getSRID());
                    Polygon newPolygon = (Polygon)geoBuffer(newLineString, distance, quadrantSegments, capStyle);
                    fb.reset();
                    fb.set("THE_GEOM", newPolygon);
                    fb.set("DANGERINDEX", fakeIndex());
                    featuresList.add(fb.buildFeature(feature.getID()+"."+x));
                }  
            } else if (feature.getDefaultGeometry() instanceof Point){
                Point theGeometry = (Point)feature.getDefaultGeometry();
                Polygon newPolygon = (Polygon)geoBuffer(theGeometry, distance, quadrantSegments, capStyle);
                fb.reset();
                fb.set("THE_GEOM", newPolygon);
                fb.set("DANGERINDEX", fakeIndex());
                featuresList.add(fb.buildFeature(feature.getID()));
            } else {
                fb.reset();
                fb.set("THE_GEOM", feature.getDefaultGeometry());
                fb.set("DANGERINDEX", fakeIndex());
                SimpleFeature newfeature = null;
                newfeature = fb.buildFeature(feature.getID());
                featuresList.add(newfeature);
            }
        }

        SimpleFeatureCollection ret = new ListFeatureCollection(targetSchema, featuresList);
        LOG.info("return=" +ret);        
        return ret;
    }  
          
    private static Geometry geoBuffer(Geometry theGeometry,String distance,String quadrantSegments,String capStyle) throws Exception {
        Name name = new NameImpl("geo","buffer");
        Process process = Processors.createProcess( name );
        if (process == null) {
            throw new Exception("process is null");
        }
        ProcessExecutor engine = Processors.newProcessExecutor(2);

        // quick map of inputs
        Map<String,Object> input = new KVP("geom", theGeometry, "distance", distance, "quadrantSegments", quadrantSegments, "capStyle", capStyle);
        Progress working = engine.submit(process, input );

        // you could do other stuff whle working is doing its thing
        if( working.isCancelled() ){
            return null;
        }

        Map<String,Object> result = working.get(); // get is BLOCKING
        Geometry ret = (Geometry) result.get("result");
        LOG.info("geo:buffer=" +ret);
        return ret;       
    }

    private static String fakeIndex() {
        String ret = ""+(System.currentTimeMillis()%3);
        return ret;
    }
}
