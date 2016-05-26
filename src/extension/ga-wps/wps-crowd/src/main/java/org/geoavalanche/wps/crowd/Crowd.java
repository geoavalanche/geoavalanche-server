package org.geoavalanche.wps.crowd;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

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
        
        List<SimpleFeature> featuresList = new ArrayList();
        SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);
        
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {         
            SimpleFeature feature = itr.next();
            fb.reset();
            for (Property p: feature.getProperties()) {
               fb.set(p.getName().getLocalPart(), p.getValue());
            }
            fb.set("incidents", "0");
            featuresList.add(fb.buildFeature(feature.getID()));         
        }
        
        ListFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
        LOG.info("return=" + ret);
        return ret;
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
        theProperties.put("incidents", String.class);

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
}
